"""Referencia en Python del retargeting que hace el mod (para previsualizar y para comparar con la version Java)."""
import json, math, sys
import numpy as np
from PIL import Image

SK = json.load(open('/home/claude/mods/tools/ef_biped_skeleton.json'))
PARENT, REST, ORDER = {}, {}, []
def _w(n, p=None):
    REST[n['name']] = np.array(n['transform']).reshape(4, 4); PARENT[n['name']] = p; ORDER.append(n['name'])
    for c in n['children']: _w(c, n['name'])
for h in SK['hierarchy']: _w(h)
def fk(local):
    G = {}
    for n in ORDER:
        p = PARENT[n]; G[n] = local[n] if p is None else G[p] @ local[n]
    return G
G0 = fk(REST)

def load_anim(path):
    d = json.load(open(path))['animation']
    return {j['name']: (np.array(j['time']), np.array(j['transform']).reshape(-1, 4, 4)) for j in d}

def slerp(a, b, t):
    d = np.dot(a, b)
    if d < 0: b = -b; d = -d
    if d > 0.9995:
        r = a + t * (b - a); return r / np.linalg.norm(r)
    th = math.acos(d); return (math.sin((1 - t) * th) * a + math.sin(t * th) * b) / math.sin(th)

def mat2quat(R):
    t = np.trace(R)
    if t > 0:
        s = math.sqrt(t + 1) * 2; return np.array([(R[2, 1] - R[1, 2]) / s, (R[0, 2] - R[2, 0]) / s, (R[1, 0] - R[0, 1]) / s, 0.25 * s])
    i = int(np.argmax([R[0, 0], R[1, 1], R[2, 2]])); j, k = (i + 1) % 3, (i + 2) % 3
    s = math.sqrt(R[i, i] - R[j, j] - R[k, k] + 1) * 2; q = np.zeros(4)
    q[i] = 0.25 * s; q[j] = (R[j, i] + R[i, j]) / s; q[k] = (R[k, i] + R[i, k]) / s; q[3] = (R[k, j] - R[j, k]) / s
    return q
def quat2mat(q):
    x, y, z, w = q
    return np.array([[1-2*(y*y+z*z), 2*(x*y-z*w), 2*(x*z+y*w)], [2*(x*y+z*w), 1-2*(x*x+z*z), 2*(y*z-x*w)], [2*(x*z-y*w), 2*(y*z+x*w), 1-2*(x*x+y*y)]])

def sample(anim, t):
    loc = {}
    for name, (ts, M) in anim.items():
        i = int(np.searchsorted(ts, t, 'right')) - 1
        i = max(0, min(i, len(ts) - 1)); j = min(i + 1, len(ts) - 1)
        f = 0.0 if j == i else (t - ts[i]) / (ts[j] - ts[i])
        f = min(max(f, 0), 1)
        q = slerp(mat2quat(M[i][:3, :3]), mat2quat(M[j][:3, :3]), f)
        T = np.eye(4); T[:3, :3] = quat2mat(q); T[:3, 3] = M[i][:3, 3] * (1 - f) + M[j][:3, 3] * f
        loc[name] = T
    return loc

def pose_model(model, loc):
    """devuelve dict bone -> matriz 4x4 (espacio EF) para huesos del modelo y resortes (sin simulacion: giro 0)."""
    G = fk(loc)
    J = model['joints']
    Rrel = {n: G[J[n]['ef'] if 'ef' in J[n] else n][:3, :3] @ G0[J[n].get('ef', n)][:3, :3].T for n in J}
    k = model['hip_height'] / G0['Root'][2, 3]
    P = {}
    out = {}
    # raiz del modelo: 'Root'
    root_ef = J['Root'].get('ef', 'Root')
    Troot = G[root_ef][:3, 3] - G0[root_ef][:3, 3]
    # jerarquia: padre mas cercano del esqueleto EF presente en el modelo
    efpar = {}
    for n in J:
        e = J[n].get('ef', n); p = PARENT[e]
        while p is not None and p not in [J[m].get('ef', m) for m in J]: p = PARENT[p]
        efpar[n] = next((m for m in J if J[m].get('ef', m) == p), None) if p else None
    done = {}
    def posed_pivot(n):
        if n in done: return done[n]
        p0 = np.array(J[n]['pivot'])
        par = efpar[n]
        if par is None: r = p0 + Troot * k
        else: r = posed_pivot(par) + Rrel[par] @ (p0 - np.array(J[par]['pivot']))
        done[n] = r; return r
    for n in J:
        M = np.eye(4); M[:3, :3] = Rrel[n]; M[:3, 3] = posed_pivot(n) - Rrel[n] @ np.array(J[n]['pivot'])
        out[n] = M
    # resortes en reposo (sin giro propio)
    for sn, sc in model.get('springs', {}).items():
        par = out[sc['parent']]
        out[sn] = par.copy()  # mismo transform que el padre
    return out

def build_tris(model, tex):
    parts = []
    for p in model['parts']:
        for q in p['quads']:
            v = np.array(q['v']).reshape(4, 8)
            parts.append((p['bone'], q['t'], v))
    return parts

def raster(model, texs, mats, view='front', S=700, yaw=0.0, parts=None):
    parts = parts or build_tris(model, None)
    pos_all = []
    items = []
    for bone, tx, v in parts:
        M = mats[bone]
        P = (M @ np.c_[v[:, :3], np.ones(4)].T).T[:, :3]
        items.append((P, v[:, 3:5], tx)); pos_all.append(P)
    allp = np.concatenate(pos_all)
    ca, sa = math.cos(yaw), math.sin(yaw)
    def proj(P):
        x = P[:, 0] * ca + P[:, 1] * sa; d = -P[:, 0] * sa + P[:, 1] * ca   # d>0 = hacia el frente (+Y)
        return x, d
    xs, d = proj(allp)
    lo_x, hi_x = xs.min(), xs.max(); lo_z, hi_z = allp[:, 2].min(), allp[:, 2].max()
    sc = S * 0.85 / max(hi_z - lo_z, hi_x - lo_x); cxm = (lo_x + hi_x) / 2
    img = np.zeros((S, S, 4), np.uint8); img[:] = (225, 225, 230, 255); zb = np.full((S, S), -1e9)
    for P, uv, tx in items:
        x, dd = proj(P)
        sx = S / 2 - (x - cxm) * sc          # derecha del personaje a la izquierda de la imagen si lo vemos de frente
        sy = S * 0.93 - (P[:, 2] - lo_z) * sc
        tex = texs[tx]; H, W = tex.shape[:2]
        for tri in ((0, 1, 2), (0, 2, 3)):
            a, b, c = tri
            xa, ya = sx[[a, b, c]], sy[[a, b, c]]
            den = (ya[1] - ya[2]) * (xa[0] - xa[2]) + (xa[2] - xa[1]) * (ya[0] - ya[2])
            if abs(den) < 1e-9: continue
            x0, x1 = int(max(0, math.floor(xa.min()))), int(min(S - 1, math.ceil(xa.max())))
            y0, y1 = int(max(0, math.floor(ya.min()))), int(min(S - 1, math.ceil(ya.max())))
            if x1 < x0 or y1 < y0: continue
            X, Y = np.meshgrid(np.arange(x0, x1 + 1) + .5, np.arange(y0, y1 + 1) + .5)
            l0 = ((ya[1] - ya[2]) * (X - xa[2]) + (xa[2] - xa[1]) * (Y - ya[2])) / den
            l1 = ((ya[2] - ya[0]) * (X - xa[2]) + (xa[0] - xa[2]) * (Y - ya[2])) / den
            l2 = 1 - l0 - l1
            m = (l0 >= -1e-3) & (l1 >= -1e-3) & (l2 >= -1e-3)
            zz = l0 * dd[a] + l1 * dd[b] + l2 * dd[c]
            u = l0 * uv[a, 0] + l1 * uv[b, 0] + l2 * uv[c, 0]; vv = l0 * uv[a, 1] + l1 * uv[b, 1] + l2 * uv[c, 1]
            col = tex[np.clip((vv * H).astype(int), 0, H - 1), np.clip((u * W).astype(int), 0, W - 1)]
            sub = zb[y0:y1 + 1, x0:x1 + 1]; ok = m & (zz > sub) & (col[..., 3] > 12)
            sub[ok] = zz[ok]; im = img[y0:y1 + 1, x0:x1 + 1]; im[ok] = col[ok]
    return Image.fromarray(img)

def load_model(dirn, name):
    m = json.load(open(f'{dirn}/{name}.json'))
    texs = [np.array(Image.open(f'{dirn}/{t}').convert('RGBA')) for t in m['textures']]
    return m, texs

if __name__ == '__main__':
    dirn, anim_path, outp = sys.argv[1], sys.argv[2], sys.argv[3]
    times = [float(x) for x in sys.argv[4:]] or [0.0]
    m, texs = load_model(dirn, 'miku')
    anim = load_anim(anim_path) if anim_path != 'rest' else None
    ims = []
    for t in times:
        loc = sample(anim, t) if anim else dict(REST)
        mats = pose_model(m, loc)
        ims.append(raster(m, texs, mats, 'front', 520)); ims.append(raster(m, texs, mats, 'front', 520, yaw=math.pi / 2))
    w = Image.new('RGB', (520 * len(ims), 520)); [w.paste(i, (k * 520, 0)) for k, i in enumerate(ims)]; w.save(outp)
