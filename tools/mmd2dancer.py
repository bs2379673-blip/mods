#!/usr/bin/env python3
"""Convierte un modelo FBX con esqueleto y pesos (p. ej. MMD exportado desde Blender) en un modelo 'dancers' formato 2
(malla suave con pesos). Uso:  python3 fbx2dancer.py <config.json> <salida_dir>
Espacio final = espacio de reposo de Epic Fight (X derecha, Y frente, Z arriba), brazos ya bajados a la vertical."""
import json, math, os, re, sys, shutil, unicodedata
import numpy as np
from PIL import Image
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import fbxparse as F

def nm(s):
    s = s.split('\x00')[0]
    try: s = s.encode('latin1').decode('utf8')
    except Exception: pass
    return unicodedata.normalize('NFKC', s)

def N(x): return unicodedata.normalize('NFKC', x)

def rot_between(a, b):
    a = a / np.linalg.norm(a); b = b / np.linalg.norm(b)
    c = np.cross(a, b); d = float(np.dot(a, b))
    if d > 0.999999: return np.eye(3)
    k = 1 / (1 + d)
    return np.array([[c[0]*c[0]*k + d, c[0]*c[1]*k - c[2], c[0]*c[2]*k + c[1]],
                     [c[1]*c[0]*k + c[2], c[1]*c[1]*k + d, c[1]*c[2]*k - c[0]],
                     [c[2]*c[0]*k - c[1], c[2]*c[1]*k + c[0], c[2]*c[2]*k + d]])


# =====================================================================================
# mmd2dancer: FBX riggeado (huesos MMD reales) + VMD  ->  modelo 'dancers' formato 3 + animaciones .dnc
# Uso: python3 mmd2dancer.py <config3.json> <salida_dir>
# =====================================================================================
import gzip, struct
import vmd as V

# MMD (izquierda +X, Y arriba, frente -Z)  ->  espacio Epic Fight (X derecha, Y frente, Z arriba)
C = np.array([[-1, 0, 0], [0, 0, -1], [0, 1, 0]], float)

def quat_to_mat(q):
    x, y, z, w = q
    return np.array([[1 - 2*(y*y + z*z), 2*(x*y - w*z), 2*(x*z + w*y)],
                     [2*(x*y + w*z), 1 - 2*(x*x + z*z), 2*(y*z - w*x)],
                     [2*(x*z - w*y), 2*(y*z + w*x), 1 - 2*(x*x + y*y)]])

def mat_to_quat(m):
    t = m[0, 0] + m[1, 1] + m[2, 2]
    if t > 0:
        s = math.sqrt(t + 1) * 2; return np.array([(m[2, 1] - m[1, 2]) / s, (m[0, 2] - m[2, 0]) / s, (m[1, 0] - m[0, 1]) / s, s / 4])
    if m[0, 0] > m[1, 1] and m[0, 0] > m[2, 2]:
        s = math.sqrt(1 + m[0, 0] - m[1, 1] - m[2, 2]) * 2; return np.array([s / 4, (m[0, 1] + m[1, 0]) / s, (m[0, 2] + m[2, 0]) / s, (m[2, 1] - m[1, 2]) / s])
    if m[1, 1] > m[2, 2]:
        s = math.sqrt(1 + m[1, 1] - m[0, 0] - m[2, 2]) * 2; return np.array([(m[0, 1] + m[1, 0]) / s, s / 4, (m[1, 2] + m[2, 1]) / s, (m[0, 2] - m[2, 0]) / s])
    s = math.sqrt(1 + m[2, 2] - m[0, 0] - m[1, 1]) * 2; return np.array([(m[0, 2] + m[2, 0]) / s, (m[1, 2] + m[2, 1]) / s, s / 4, (m[1, 0] - m[0, 1]) / s])

def qmul(a, b):   # a*b, arrays (...,4) xyzw
    ax, ay, az, aw = a[..., 0], a[..., 1], a[..., 2], a[..., 3]; bx, by, bz, bw = b[..., 0], b[..., 1], b[..., 2], b[..., 3]
    return np.stack([aw*bx + ax*bw + ay*bz - az*by, aw*by - ax*bz + ay*bw + az*bx, aw*bz + ax*by - ay*bx + az*bw, aw*bw - ax*bx - ay*by - az*bz], -1)

def slerp_dense(keys, n):
    """keys: [(frame,pos,quat)] -> (n,3) pos, (n,4) quat por fotograma entero (interpolacion lineal / slerp)."""
    fr = np.array([k[0] for k in keys]); P = np.array([k[1] for k in keys], float); Q = np.array([k[2] for k in keys], float)
    Q /= np.linalg.norm(Q, axis=1, keepdims=True)
    t = np.arange(n)
    if len(keys) == n and fr[0] == 0 and fr[-1] == n - 1 and np.all(np.diff(fr) == 1): return P, Q
    idx = np.clip(np.searchsorted(fr, t, side='right') - 1, 0, len(fr) - 1); j = np.clip(idx + 1, 0, len(fr) - 1)
    span = np.maximum(fr[j] - fr[idx], 1); a = np.clip((t - fr[idx]) / span, 0, 1)[:, None]; a = np.where((j == idx)[:, None], 0, a)
    q0, q1 = Q[idx], Q[j]; d = np.sum(q0 * q1, 1, keepdims=True); q1 = np.where(d < 0, -q1, q1); d = np.abs(d)
    th = np.arccos(np.clip(d, -1, 1)); s = np.sin(th)
    w0 = np.where(s > 1e-6, np.sin((1 - a) * th) / np.maximum(s, 1e-9), 1 - a); w1 = np.where(s > 1e-6, np.sin(a * th) / np.maximum(s, 1e-9), a)
    q = w0 * q0 + w1 * q1; q /= np.linalg.norm(q, axis=1, keepdims=True)
    return P[idx] * (1 - a) + P[j] * a, q

def sample_morph(keys, n):
    fr = np.array([k[0] for k in keys]); w = np.array([k[1] for k in keys], float)
    return np.interp(np.arange(n), fr, w)

def build_model(cfg, base, out):
    r = F.parse(os.path.join(base, cfg['fbx']))
    ob = r.get('Objects'); conns = r.get('Connections').children
    models = {n.props[0]: (nm(n.props[1]), n.props[2]) for n in ob.all('Model')}
    par = {c.props[1]: c.props[2] for c in conns if c.props[0] == 'OO' and c.props[1] in models and c.props[2] in models}
    cl = {}
    for n in ob.all('Deformer'):
        if n.props[2] == 'Cluster':
            idx = n.get('Indexes'); w = n.get('Weights'); tl = n.get('TransformLink')
            cl[n.props[0]] = (idx.props[0] if idx else np.zeros(0, int), w.props[0] if w else np.zeros(0), np.array(tl.props[0]).reshape(4, 4).T)
    bone_cluster = {}
    for c in conns:
        if c.props[0] == 'OO' and c.props[1] in models and c.props[2] in cl: bone_cluster[models[c.props[1]][0]] = c.props[2]
    bonepos = {name: cl[c][2][:3, 3].copy() for name, c in bone_cluster.items()}
    id2name = {k: v[0] for k, v in models.items()}
    limb = [k for k, v in models.items() if v[1] == 'LimbNode' and v[0] in bonepos]
    # orden: padres primero (DFS estable)
    names = []
    parent_of = {}
    for k in limb:
        p = par.get(k)
        while p is not None and not (models[p][1] == 'LimbNode' and models[p][0] in bonepos): p = par.get(p)
        parent_of[models[k][0]] = models[p][0] if p is not None else None
    pending = [models[k][0] for k in limb]
    while pending:
        for n in list(pending):
            if parent_of[n] is None or parent_of[n] in names: names.append(n); pending.remove(n)
    bidx = {n: i for i, n in enumerate(names)}
    def ef(p): return np.array([-p[0], p[2], p[1]], float)
    # ---- malla
    g = [n for n in ob.all('Geometry') if n.props[2] == 'Mesh'][0]
    gid = g.props[0]
    V_ = g.get('Vertices').props[0].reshape(-1, 3)
    mscale = cfg.get('mesh_to_skeleton', 100.0)
    Vs = np.c_[V_[:, 0] * mscale, V_[:, 2] * mscale, -V_[:, 1] * mscale]
    verts = np.array([ef(p) for p in Vs])
    zmin, zmax = verts[:, 2].min(), verts[:, 2].max()
    s = float(cfg['height']) / (zmax - zmin)
    origin = np.array([0.0, 0.0, zmin])
    def tf(p): return (p - origin) * s
    PI = g.get('PolygonVertexIndex').props[0]
    ln = g.get('LayerElementNormal'); NR = ln.get('Normals').props[0].reshape(-1, 3)
    lu = g.get('LayerElementUV'); UV = lu.get('UV').props[0].reshape(-1, 2); UVI = lu.get('UVIndex').props[0]
    MAT = g.get('LayerElementMaterial').get('Materials').props[0]
    polys, cur = [], []
    for i, x in enumerate(PI):
        cur.append((int(x) if x >= 0 else int(~x), i))
        if x < 0: polys.append(cur); cur = []
    W = [dict() for _ in range(len(V_))]
    for bname, c in bone_cluster.items():
        if bname not in bidx: continue
        idx, wt, _ = cl[c]
        for i, w in zip(idx, wt): W[int(i)][bidx[bname]] = W[int(i)].get(bidx[bname], 0.0) + float(w)
    key2v = {}; ov = []; tris = {}; ctrl2out = {}
    for pi_, poly in enumerate(polys):
        m = int(MAT[pi_]); ids = []
        for c, pv in poly:
            u, v = UV[UVI[pv]]; n = NR[pv]
            key = (c, int(UVI[pv]), round(float(n[0]), 3), round(float(n[1]), 3), round(float(n[2]), 3))
            if key not in key2v:
                key2v[key] = len(ov)
                nef = ef(np.array([n[0], n[2], -n[1]]))
                ov.append((c, tf(verts[c]), (float(u), float(1 - v)), nef)); ctrl2out.setdefault(c, []).append(len(ov) - 1)
            ids.append(key2v[key])
        for k in range(1, len(ids) - 1): tris.setdefault(m, []).append((ids[0], ids[k], ids[k + 1]))
    bi = []; bw = []
    for c, p, uv, n in ov:
        w = sorted(W[c].items(), key=lambda kv: -kv[1])[:4]
        tot = sum(x for _, x in w)
        if tot <= 0: w = [(bidx[cfg.get('fallback_bone', 'センター')], 1.0)]; tot = 1.0
        bi.append([b for b, _ in w] + [0] * (4 - len(w))); bw.append([x / tot for _, x in w] + [0.0] * (4 - len(w)))
    os.makedirs(out, exist_ok=True)
    tex_names = []
    for k, f in enumerate(cfg['textures']):
        im = Image.open(os.path.join(base, f)).convert('RGBA'); fn = f"{cfg['name']}_{k}.png"; im.save(os.path.join(out, fn)); tex_names.append(fn)
    groups = []; flat = []
    for m, tx in sorted(cfg['material_texture'].items(), key=lambda kv: int(kv[0])):
        t = tris.get(int(m), [])
        if not t: continue
        groups.append({'tex': tx, 'start': len(flat) // 3, 'count': len(t)})
        for a, b, c in t: flat += [a, b, c]
    piv = np.array([tf(ef(bonepos[n])) for n in names])
    # ---- resortes
    sp_rules = [(re.compile(N(a['match'])), a) for a in cfg['springs']]
    springs = []
    sp_names = {}
    for n in names:
        for rx, a in sp_rules:
            if rx.fullmatch(n): sp_names[n] = a; break
    for n in names:
        if n not in sp_names: continue
        a = sp_names[n]; i = bidx[n]
        kids = [m for m in names if parent_of[m] == n and m in sp_names]
        if kids: tip = piv[bidx[kids[0]]]
        else:
            if a.get('tip_dir') == 'down': d = np.array([0, 0, -1.0])
            else:
                d = piv[i] - piv[bidx[parent_of[n]]]; d = d / (np.linalg.norm(d) + 1e-9)
            tip = piv[i] + d * a.get('end_len', 0.1)
        anchor = parent_of[n]
        while anchor in sp_names: anchor = parent_of[anchor]
        springs.append({'bone': bidx[n], 'anchor': bidx[anchor], 'tip': [round(float(x), 5) for x in tip], 'stiffness': a.get('stiffness', 90), 'damping': a.get('damping', 4),
                        'gravity': a.get('gravity', 2), 'max_angle': a.get('max_angle', 25), 'max_total': a.get('max_total', 70), 'group': a.get('group', 'hair'), 'radius': a.get('radius', 0.0)})
    colliders = []
    for cdef in cfg.get('colliders', []):
        if isinstance(cdef['a'], list): pa = np.array(cdef['a'], float); pb = np.array(cdef['b'], float)   # coordenadas finales (metros, espacio EF)
        else: pa = tf(ef(bonepos[N(cdef['a'])])); pb = tf(ef(bonepos[N(cdef['b'])]))
        colliders.append({'bone': bidx[N(cdef['bone'])], 'a': [round(float(x), 5) for x in pa], 'b': [round(float(x), 5) for x in pb], 'r': cdef['r'], 'groups': cdef['groups']})
    # ---- morphs (blend shapes)
    objs = {n.props[0]: n for n in ob.children if n.props and isinstance(n.props[0], int)}
    ch = {n.props[0]: n for n in ob.all('Deformer') if n.props[2] == 'BlendShapeChannel'}
    morphs = {}
    for c in conns:
        if c.props[0] == 'OO' and c.props[2] in ch and c.props[1] in objs and objs[c.props[1]].name == 'Geometry':
            sg = objs[c.props[1]]; mname = N(nm(ch[c.props[2]].props[1]).replace('\x01SubDeformer', '').replace('\x01', ''))
            idx = sg.get('Indexes'); vv = sg.get('Vertices')
            if idx is None or vv is None: continue
            idx = idx.props[0]; D = vv.props[0].reshape(-1, 3)
            oi = []; od = []
            for ci, d in zip(idx, D):
                dsk = np.array([d[0] * mscale, d[2] * mscale, -d[1] * mscale]); de = ef(dsk) * s
                if np.linalg.norm(de) < 1e-7: continue
                for o in ctrl2out.get(int(ci), []): oi.append(o); od += [round(float(x), 5) for x in de]
            if oi: morphs[mname] = {'i': oi, 'd': od}
    model = {'format': 3, 'name': cfg['name'], 'textures': tex_names, 'unit': 0.08 * cfg['height'] / 1.58,
             'bones': [{'n': n, 'p': (bidx[parent_of[n]] if parent_of[n] else -1), 'pv': [round(float(x), 5) for x in piv[i]]} for i, n in enumerate(names)],
             'springs': springs, 'colliders': colliders, 'morphs': morphs,
             'skin': {'bones': names, 'v': [round(float(x), 5) for c, p, uv, n in ov for x in (p[0], p[1], p[2], uv[0], uv[1], n[0], n[1], n[2])],
                      'bi': [x for ids in bi for x in ids], 'bw': [round(float(x), 4) for ws in bw for x in ws], 'tris': flat, 'groups': groups}}
    json.dump(model, open(os.path.join(out, cfg['name'] + '.json'), 'w'), separators=(',', ':'))
    print('modelo: huesos', len(names), 'verts', len(ov), 'tris', len(flat) // 3, 'resortes', len(springs), 'morphs', len(morphs), sorted(morphs))
    return model, names, bidx

# ---------------------------------------------------------------- bake
def fk(model, L, rootT, root_bone):
    """L: (nb,3,3) delta local (base EF); devuelve D (nb,3,3), posed (nb,3)."""
    B = model['bones']; nb = len(B); D = np.zeros((nb, 3, 3)); P = np.zeros((nb, 3))
    pv = np.array([b['pv'] for b in B])
    for i, b in enumerate(B):
        p = b['p']
        if p < 0: D[i] = L[i]; P[i] = pv[i]
        else:
            D[i] = D[p] @ L[i]; P[i] = P[p] + D[p] @ (pv[i] - pv[p])
        if i == root_bone: P[i] = P[i] + (D[p] @ rootT if p >= 0 else rootT)
    return D, P

def bake(cfg, model, names, bidx, vmd_body, vmd_face, outpath, preview=None):
    n = 0
    tracks = {}
    amap = {N(k): N(v) for k, v in cfg['bone_map'].items()}
    morph_alias = {N(k): N(v) for k, v in cfg.get('morph_alias', {}).items()}
    body = V.parse(vmd_body) if vmd_body else None
    face = V.parse(vmd_face) if vmd_face else None
    if body: n = max(f for b in body['bones'].values() for f, _, _ in b) + 1
    if face: n = max(n, max([f for b in face['morphs'].values() for f, _ in b] + [0]) + 1)
    unit = model['unit']
    bonesN = {N(k): v for k, v in body['bones'].items()} if body else {}
    dense = {}
    def get(k):
        if k not in bonesN: return None
        if k not in dense: dense[k] = slerp_dense(bonesN[k], n)
        return dense[k]
    nb = len(names)
    quats = {}   # idx hueso -> (n,4) quat EF
    root_t = np.zeros((n, 3))
    root_bone = bidx[N(cfg['root_bone'])]
    for vname, bname in amap.items():
        d = get(vname)
        if d is None or N(bname) not in bidx: continue
        P, Q = d
        q = Q.copy()
        for extra in cfg.get('merge', {}).get(vname, []):
            e = get(N(extra))
            if e is not None: q = qmul(q, e[1])
        qe = np.stack([q[:, 0], q[:, 2], -q[:, 1], q[:, 3]], 1)    # reflejo MMD->EF
        quats[bidx[N(bname)]] = qe
        if N(bname) == N(cfg['root_bone']):
            root_t = unit * np.stack([-P[:, 0], -P[:, 2], P[:, 1]], 1)
    # --- apoyo de pies: calcula la altura de la suela y la corrige cuando el original esta en el suelo
    delta = np.zeros(n)
    gcfg = cfg.get('ground')
    if gcfg and body:
        sole = {}
        for side in 'LR':
            ank = bidx[N(f'足首.{side}')]; toe = bidx[N(f'つま先.{side}')]
            pv = np.array([b['pv'] for b in model['bones']])
            heel = pv[ank].copy(); heel[2] = 0; heel[1] -= gcfg.get('heel_back', 0.03)
            sole[side] = (ank, [pv[toe], heel])
        ikz = [get(N('左足ＩＫ')), get(N('右足ＩＫ'))]
        I3 = np.eye(3)
        low = np.zeros(n)
        for f in range(n):
            L = np.tile(I3, (nb, 1, 1))
            for b, q in quats.items(): L[b] = quat_to_mat(q[f])
            D, P = fk(model, L, root_t[f], root_bone)
            zs = []
            pv = np.array([b['pv'] for b in model['bones']])
            for side in 'LR':
                ank, pts = sole[side]
                for p in pts: zs.append((D[ank] @ (p - pv[ank]) + P[ank])[2])
            low[f] = min(zs)
        h_ik = np.minimum(ikz[0][0][:, 1] if ikz[0] is not None else np.full(n, 99.0), ikz[1][0][:, 1] if ikz[1] is not None else np.full(n, 99.0))
        grounded = h_ik < gcfg.get('ik_threshold', 0.6)
        want = -low
        # fuera del suelo: interpolar entre los apoyados
        gi = np.where(grounded)[0]
        if len(gi) > 1: delta = np.interp(np.arange(n), gi, want[gi])
        k = int(gcfg.get('smooth', 5)); ker = np.ones(2 * k + 1) / (2 * k + 1)
        delta = np.convolve(np.pad(delta, k, mode='edge'), ker, mode='valid')
        delta = np.clip(delta, -0.25, 0.25)
        root_t[:, 2] += delta
        print('  apoyo: delta medio %.3f min %.3f max %.3f, apoyados %.0f%%' % (delta.mean(), delta.min(), delta.max(), 100 * grounded.mean()))
    # --- morphs
    mk = {}
    mnames = set(model['morphs'])
    for src in (face, body):
        if not src: continue
        for k, keys in src['morphs'].items():
            kk = morph_alias.get(N(k), N(k))
            if kk in mnames and len(keys) > 0 and kk not in mk: mk[kk] = keys
    # --- escribir .dnc
    buf = bytearray(b'DNC1')
    buf += struct.pack('<HIH', 30, n, 0); nb_pos = 4 + 2 + 4
    const = []; var = []
    for b, q in sorted(quats.items()):
        if np.max(np.abs(q - q[0])) < 1e-4: const.append((b, q[0]))
        else: var.append((b, q))
    buf = bytearray(b'DNC1') + struct.pack('<HIHH', 30, n, len(const), len(var))
    for b, q in const: buf += struct.pack('<H4f', b, *q)
    for b, q in var:
        qq = q / np.linalg.norm(q, axis=1, keepdims=True)
        sgn = np.where(qq[:, 3:4] < 0, -1, 1); qq = qq * sgn
        buf += struct.pack('<H', b) + np.round(qq * 32767).astype('<i2').tobytes()
    buf += struct.pack('<H', root_bone) + root_t.astype('<f4').tobytes()
    buf += struct.pack('<H', len(mk))
    for kk, keys in mk.items():
        e = kk.encode('utf8'); buf += struct.pack('<B', len(e)) + e + struct.pack('<I', len(keys))
        for f, w in keys: buf += struct.pack('<If', f, w)
    with gzip.open(outpath, 'wb', compresslevel=9) as fh: fh.write(bytes(buf))
    print('  %s: %d fotogramas (%.1fs), %d huesos animados (%d constantes), %d morphs, %d KB' % (os.path.basename(outpath), n, n / 30, len(var), len(const), len(mk), os.path.getsize(outpath) // 1024))
    return quats, root_t, mk, n

def main():
    cfgp = sys.argv[1]; out = sys.argv[2]
    cfg = json.load(open(cfgp)); base = os.path.dirname(os.path.abspath(cfgp))
    model, names, bidx = build_model(cfg, base, out)
    adir = os.path.join(out, 'anims'); os.makedirs(adir, exist_ok=True)
    only = sys.argv[3:] 
    for a in cfg['vmds']:
        if only and a['name'] not in only: continue
        print('bake', a['name'])
        bake(cfg, model, names, bidx, a.get('body'), a.get('face'), os.path.join(adir, a['name'] + '.dnc'))

if __name__ == '__main__': main()
