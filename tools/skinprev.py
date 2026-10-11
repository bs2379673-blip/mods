"""Vista previa en Python de un modelo formato 2 (malla con pesos). Uso: skinprev.py <dir> <nombre> <salida.png> [pose.json]"""
import json, math, sys
import numpy as np
from PIL import Image

def load(d, name):
    m = json.load(open(f'{d}/{name}.json'))
    texs = [np.array(Image.open(f'{d}/{t}').convert('RGBA')) for t in m['textures']]
    return m, texs

def skin_positions(m, mats=None):
    sk = m['skin']; v = np.array(sk['v'], float).reshape(-1, 8); n = len(v)
    bi = np.array(sk['bi']).reshape(n, 4); bw = np.array(sk['bw']).reshape(n, 4)
    names = sk['bones']
    P = v[:, :3].copy()
    if mats is None: return P, v
    out = np.zeros((n, 3))
    for k in range(4):
        for b in set(bi[:, k]):
            sel = (bi[:, k] == b) & (bw[:, k] > 0)
            if not sel.any(): continue
            M = mats[names[b]]
            out[sel] += bw[sel, k, None] * (v[sel, :3] @ M[:3, :3].T + M[:3, 3])
    return out, v

def raster(m, texs, P, v, view='front', S=640, yaw=0.0, lo_hi=None):
    sk = m['skin']; tris = np.array(sk['tris']).reshape(-1, 3)
    ca, sa = math.cos(yaw), math.sin(yaw)
    x = P[:, 0] * ca + P[:, 1] * sa; d = -P[:, 0] * sa + P[:, 1] * ca
    lo_x, hi_x = (lo_hi or (x.min(), x.max())); lo_z, hi_z = P[:, 2].min(), P[:, 2].max()
    sc = S * 0.9 / max(hi_z - lo_z, hi_x - lo_x); cxm = (lo_x + hi_x) / 2
    img = np.zeros((S, S, 4), np.uint8); img[:] = (225, 225, 230, 255); zb = np.full((S, S), -1e9)
    sx = S / 2 - (x - cxm) * sc; sy = S * 0.95 - (P[:, 2] - lo_z) * sc
    for g in sk['groups']:
        T = texs[g['tex']]; H, W = T.shape[:2]
        for t in tris[g['start']:g['start'] + g['count']]:
            xa, ya = sx[t], sy[t]
            den = (ya[1] - ya[2]) * (xa[0] - xa[2]) + (xa[2] - xa[1]) * (ya[0] - ya[2])
            if abs(den) < 1e-9: continue
            x0, x1 = int(max(0, math.floor(xa.min()))), int(min(S - 1, math.ceil(xa.max())))
            y0, y1 = int(max(0, math.floor(ya.min()))), int(min(S - 1, math.ceil(ya.max())))
            if x1 < x0 or y1 < y0: continue
            X, Y = np.meshgrid(np.arange(x0, x1 + 1) + .5, np.arange(y0, y1 + 1) + .5)
            l0 = ((ya[1] - ya[2]) * (X - xa[2]) + (xa[2] - xa[1]) * (Y - ya[2])) / den
            l1 = ((ya[2] - ya[0]) * (X - xa[2]) + (xa[0] - xa[2]) * (Y - ya[2])) / den
            l2 = 1 - l0 - l1
            mk = (l0 >= -1e-3) & (l1 >= -1e-3) & (l2 >= -1e-3)
            zz = l0 * d[t[0]] + l1 * d[t[1]] + l2 * d[t[2]]
            u = l0 * v[t[0], 3] + l1 * v[t[1], 3] + l2 * v[t[2], 3]; vv = l0 * v[t[0], 4] + l1 * v[t[1], 4] + l2 * v[t[2], 4]
            col = T[np.clip(((vv % 1) * H).astype(int), 0, H - 1), np.clip(((u % 1) * W).astype(int), 0, W - 1)]
            sub = zb[y0:y1 + 1, x0:x1 + 1]; ok = mk & (zz > sub) & (col[..., 3] > 12)
            sub[ok] = zz[ok]; im = img[y0:y1 + 1, x0:x1 + 1]; im[ok] = col[ok]
    return Image.fromarray(img)

if __name__ == '__main__':
    d, name, outp = sys.argv[1:4]
    m, texs = load(d, name)
    mats = None
    if len(sys.argv) > 4:
        P = json.load(open(sys.argv[4])); mats = {}
        for b, a in P.items():
            M = np.eye(4); M[:3, :3] = np.array(a[:9]).reshape(3, 3); M[:3, 3] = a[9:12]; mats[b] = M
    pos, v = skin_positions(m, mats)
    ims = [raster(m, texs, pos, v, yaw=0), raster(m, texs, pos, v, yaw=math.pi / 2), raster(m, texs, pos, v, yaw=math.pi)]
    w = Image.new('RGB', (640 * 3, 640)); [w.paste(i, (k * 640, 0)) for k, i in enumerate(ims)]; w.save(outp)
