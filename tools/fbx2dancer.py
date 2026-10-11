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

def main():
    cfg = json.load(open(sys.argv[1])); out = sys.argv[2]
    base = os.path.dirname(os.path.abspath(sys.argv[1]))
    r = F.parse(os.path.join(base, cfg['fbx']))
    ob = r.get('Objects'); conns = r.get('Connections').children
    models = {n.props[0]: (nm(n.props[1]), n.props[2]) for n in ob.all('Model')}
    par = {c.props[1]: c.props[2] for c in conns if c.props[0] == 'OO' and c.props[1] in models and c.props[2] in models}
    # ---- huesos y su posicion de reposo (TransformLink), en el espacio del esqueleto
    cl = {}
    for n in ob.all('Deformer'):
        if n.props[2] == 'Cluster':
            idx = n.get('Indexes'); w = n.get('Weights'); tl = n.get('TransformLink')
            cl[n.props[0]] = (idx.props[0] if idx else np.zeros(0, int), w.props[0] if w else np.zeros(0), np.array(tl.props[0]).reshape(4, 4).T)
    bone_cluster = {}
    for c in conns:
        if c.props[0] == 'OO' and c.props[1] in models and c.props[2] in cl: bone_cluster[models[c.props[1]][0]] = c.props[2]
    bonepos = {name: cl[c][2][:3, 3].copy() for name, c in bone_cluster.items()}
    boneparent = {models[k][0]: models[v][0] for k, v in par.items() if models[k][1] == 'LimbNode' and models[v][1] == 'LimbNode'}
    # espacio del esqueleto (X izquierda, Y arriba, Z frente) -> EF (X derecha, Y frente, Z arriba)
    def ef(p): return np.array([-p[0], p[2], p[1]], float)
    # ---- malla
    g = [n for n in ob.all('Geometry') if n.props[2] == 'Mesh'][0]
    V = g.get('Vertices').props[0].reshape(-1, 3)
    mscale = cfg.get('mesh_to_skeleton', 100.0)
    Vs = np.c_[V[:, 0] * mscale, V[:, 2] * mscale, -V[:, 1] * mscale]      # malla (Blender, Z arriba) -> esqueleto
    PI = g.get('PolygonVertexIndex').props[0]
    ln = g.get('LayerElementNormal'); NR = ln.get('Normals').props[0].reshape(-1, 3)
    lu = g.get('LayerElementUV'); UV = lu.get('UV').props[0].reshape(-1, 2); UVI = lu.get('UVIndex').props[0]
    MAT = g.get('LayerElementMaterial').get('Materials').props[0]
    polys, cur = [], []
    for i, x in enumerate(PI):
        cur.append((int(x) if x >= 0 else int(~x), i))
        if x < 0: polys.append(cur); cur = []
    # ---- pesos por vertice de control
    W = [dict() for _ in range(len(V))]
    # mapeo de huesos -> hueso destino
    rules = [(re.compile(unicodedata.normalize('NFKC', a)), b) for a, b in cfg['map']]
    def target(bone):
        for rx, t in rules:
            m = rx.fullmatch(bone)
            if m: return m.expand(t)
        return None
    for bname, c in bone_cluster.items():
        t = target(bname)
        if t is None: continue
        idx, wt, _ = cl[c]
        for i, w in zip(idx, wt): W[int(i)][t] = W[int(i)].get(t, 0.0) + float(w)
    # ---- posiciones EF de huesos y pivotes
    pivots = {}
    for jn, src in cfg['pivots'].items():
        srcs = src if isinstance(src, list) else [src]
        pivots[jn] = np.mean([ef(bonepos[N(s)]) for s in srcs], axis=0)
    # ---- reposo EF: bajar los brazos
    verts = np.array([ef(p) for p in Vs])
    rot = {}   # hueso -> (R, centro)
    for side, fx in cfg.get('rest_fix', {}).items():
        S = ef(bonepos[N(fx['shoulder'])]); Wr = ef(bonepos[N(fx['wrist'])])
        R = rot_between(Wr - S, np.array([0, 0, -1.0]))
        for b in fx['bones']: rot[b] = (R, S)
        for jn in fx['move_joints']:
            pivots[jn] = S + R @ (pivots[jn] - S)
    # resortes
    springs = {}
    for sn, sc in cfg.get('springs', {}).items():
        springs[sn] = dict(sc)
        p = ef(bonepos[N(sc['bone'])]); springs[sn]['pivot'] = p
        for b, (R, S) in rot.items():
            pass
    # extremos (tip) de cada resorte: siguiente eslabon de la cadena, o extrapolado
    names = list(springs)
    for sn in names:
        sc = springs[sn]
        nxt = [m for m in names if springs[m].get('parent') == sn]
        if nxt: sc['tip'] = springs[nxt[0]]['pivot'].copy()
        else:
            parent = sc['parent']
            pp = springs[parent]['pivot'] if parent in springs else pivots[parent]
            d = sc['pivot'] - pp
            ln_ = np.linalg.norm(d)
            sc['tip'] = sc['pivot'] + (d / ln_ if ln_ > 1e-9 else np.array([0, 0, -1.0])) * sc.get('end_len', ln_ * 0.8 if ln_ > 1e-9 else 0.1)
    # escala
    zmin, zmax = verts[:, 2].min(), verts[:, 2].max()
    s = float(cfg['height']) / (zmax - zmin)
    origin = np.array([0.0, 0.0, zmin])
    def tf(p): return (p - origin) * s
    # LBS de reposo (brazos) para vertices y normales
    nverts_ctrl = len(V)
    posed_ctrl = np.zeros_like(verts)
    for i in range(nverts_ctrl):
        w = W[i]; tot = sum(w.values())
        if tot <= 0:
            posed_ctrl[i] = verts[i]; continue
        acc = np.zeros(3)
        for b, wt in w.items():
            if b in rot: R, S = rot[b]; acc += wt / tot * (S + R @ (verts[i] - S))
            else: acc += wt / tot * verts[i]
        posed_ctrl[i] = acc
    def rot_normal(i, n):
        w = W[i]; tot = sum(w.values())
        if tot <= 0: return n
        acc = np.zeros(3)
        for b, wt in w.items():
            acc += wt / tot * ((rot[b][0] @ n) if b in rot else n)
        l = np.linalg.norm(acc); return acc / l if l > 1e-9 else n
    # ---- vertices de salida (deduplicados) y triangulos por material
    key2v = {}; ov = []; tris = {}
    for pi_, poly in enumerate(polys):
        m = int(MAT[pi_])
        ids = []
        for c, pv in poly:
            u, v = UV[UVI[pv]]; n = NR[pv]
            key = (c, int(UVI[pv]), round(float(n[0]), 3), round(float(n[1]), 3), round(float(n[2]), 3))
            if key not in key2v:
                key2v[key] = len(ov)
                nef = ef(np.array([n[0], n[2], n[1]])) if False else None
                # normal de la malla (Blender Z arriba) -> esqueleto -> EF
                nsk = np.array([n[0], n[2], -n[1]]); nef = ef(nsk); nef = rot_normal(c, nef)
                ov.append((c, tf(posed_ctrl[c]), (float(u), float(1 - v)), nef))
            ids.append(key2v[key])
        for k in range(1, len(ids) - 1): tris.setdefault(m, []).append((ids[0], ids[k], ids[k + 1]))
    # pesos finales (top 4)
    bone_names = []
    def bidx(b):
        if b not in bone_names: bone_names.append(b)
        return bone_names.index(b)
    bi = []; bw = []
    for c, p, uv, n in ov:
        w = sorted(W[c].items(), key=lambda kv: -kv[1])[:4]
        tot = sum(x for _, x in w)
        if tot <= 0: w = [(cfg.get('fallback_bone', 'Root'), 1.0)]; tot = 1.0
        ids = [bidx(b) for b, _ in w] + [0] * (4 - len(w)); ws = [x / tot for _, x in w] + [0.0] * (4 - len(w))
        bi.append(ids); bw.append(ws)
    # texturas
    os.makedirs(out, exist_ok=True)
    tex_names = []
    for k, f in enumerate(cfg['textures']):
        im = Image.open(os.path.join(base, f)).convert('RGBA'); fn = f"{cfg['name']}_{k}.png"; im.save(os.path.join(out, fn)); tex_names.append(fn)
    groups = []
    flat = []
    for m, tx in sorted(cfg['material_texture'].items(), key=lambda kv: int(kv[0])):
        t = tris.get(int(m), [])
        if not t: continue
        groups.append({'tex': tx, 'start': len(flat) // 3, 'count': len(t)})
        for a, b, c in t: flat += [a, b, c]
    model = {'format': 2, 'name': cfg['name'], 'textures': tex_names,
             'joints': {jn: {'ef': jn, 'pivot': [round(float(x), 5) for x in tf(p)]} for jn, p in pivots.items()},
             'springs': {sn: {'parent': sc['parent'], 'pivot': [round(float(x), 5) for x in tf(sc['pivot'])], 'tip': [round(float(x), 5) for x in tf(sc['tip'])],
                              'stiffness': sc.get('stiffness', 120), 'damping': sc.get('damping', 4), 'gravity': sc.get('gravity', 9.8), 'max_angle': sc.get('max_angle', 70)}
                         for sn, sc in springs.items()},
             'hip_height': float(tf(pivots['Root'])[2]),
             'parts': [],
             'skin': {'bones': bone_names,
                      'v': [round(float(x), 5) for c, p, uv, n in ov for x in (p[0], p[1], p[2], uv[0], uv[1], n[0], n[1], n[2])],
                      'bi': [x for ids in bi for x in ids], 'bw': [round(float(x), 4) for ws in bw for x in ws],
                      'tris': flat, 'groups': groups}}
    json.dump(model, open(os.path.join(out, cfg['name'] + '.json'), 'w'), separators=(',', ':'))
    print('verts', len(ov), 'tris', len(flat) // 3, 'bones', bone_names, 'height', cfg['height'])

main()
