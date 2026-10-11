#!/usr/bin/env python3
"""Convierte un modelo Blockbench exportado a glTF en un modelo 'dancers' (JSON + texturas).

Uso:  python3 gltf2dancer.py <config.json> <salida_dir>
La config (ver miku_config.json) dice que nodo del glTF va a que hueso, donde estan los pivotes y los resortes.
El resultado queda en el espacio de reposo de Epic Fight: X = derecha del personaje, Y = frente, Z = arriba.
"""
import json, base64, sys, os, math
import numpy as np
from PIL import Image

def load(path):
    g = json.load(open(path))
    buf = base64.b64decode(g['buffers'][0]['uri'].split(',', 1)[1])
    return g, buf

def acc(g, buf, i):
    a = g['accessors'][i]; bv = g['bufferViews'][a['bufferView']]
    n = {'SCALAR': 1, 'VEC2': 2, 'VEC3': 3, 'VEC4': 4}[a['type']]
    dt = {5126: '<f4', 5123: '<u2', 5125: '<u4'}[a['componentType']]
    off = bv.get('byteOffset', 0) + a.get('byteOffset', 0)
    return np.frombuffer(buf, dtype=dt, count=a['count'] * n, offset=off).reshape(-1, n)

def local(n):
    if 'matrix' in n:
        return np.array(n['matrix']).reshape(4, 4).T
    T = np.eye(4); T[:3, 3] = n.get('translation', [0, 0, 0])
    x, y, z, w = n.get('rotation', [0, 0, 0, 1])
    R = np.eye(4)
    R[:3, :3] = [[1-2*(y*y+z*z), 2*(x*y-z*w), 2*(x*z+y*w)], [2*(x*y+z*w), 1-2*(x*x+z*z), 2*(y*z-x*w)], [2*(x*z-y*w), 2*(y*z+x*w), 1-2*(x*x+y*y)]]
    S = np.diag(list(n.get('scale', [1, 1, 1])) + [1])
    return T @ R @ S

def rot_y(deg):
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])

def main():
    cfg = json.load(open(sys.argv[1])); out = sys.argv[2]
    base = os.path.dirname(os.path.abspath(sys.argv[1]))
    g, buf = load(os.path.join(base, cfg['gltf']))
    N = g['nodes']
    root_name = cfg['root']
    # glTF (x derecha, y arriba, z atras) -> EF (x derecha, y frente, z arriba)
    E = np.array([[1, 0, 0], [0, 0, -1], [0, 1, 0]], float)
    mats = [g['materials'][i]['pbrMetallicRoughness']['baseColorTexture']['index'] for i in range(len(g['materials']))]
    tex_src = [g['textures'][t]['source'] for t in mats]
    quads = []   # (bone, tex, [4 x (pos(3), uv(2), nrm(3))])
    def assign(path, rules, default):
        b = default
        for nm in path:   # el nodo mas profundo manda
            if nm in rules: b = rules[nm]
        return b
    def walk(i, P, path, active):
        n = N[i]; M = P @ local(n); nm = n.get('name'); path = path + [nm]
        if nm == root_name: active = True
        if active and 'mesh' in n:
            bone = assign(path, cfg['bones'], cfg['default_bone'])
            for p in g['meshes'][n['mesh']]['primitives']:
                pos = acc(g, buf, p['attributes']['POSITION']); uv = acc(g, buf, p['attributes']['TEXCOORD_0']); nr = acc(g, buf, p['attributes']['NORMAL'])
                w = (M @ np.c_[pos, np.ones(len(pos))].T).T[:, :3]
                nw = (M[:3, :3] @ nr.T).T
                for k in range(0, len(pos), 4):
                    quads.append((bone, p.get('material', 0), [(E @ w[k+j], uv[k+j], E @ (nw[k+j] / (np.linalg.norm(nw[k+j]) + 1e-9))) for j in (0, 2, 3, 1)]))
        for c in n.get('children', []): walk(c, M, path, active)
    for r in g['scenes'][0]['nodes']: walk(r, np.eye(4), [], False)
    # centrar: pies en z=0, centro x/y
    pts = np.array([v[0] for q in quads for v in q[2]])
    lo, hi = pts.min(0), pts.max(0)
    cx, cy = float(cfg.get('center_x', (lo[0] + hi[0]) / 2)), float(cfg.get('center_y', (lo[1] + hi[1]) / 2))
    origin = np.array([cx, cy, lo[2]])
    s = float(cfg['scale'])
    def tf(p): return (p - origin) * s
    def tfg(p):  # punto dado en coordenadas glTF -> espacio final
        return tf(E @ np.array(p, float))
    joints = {jn: tfg(jc['pivot']) for jn, jc in cfg['joints'].items()}
    springs = {}
    for sn, sc in cfg.get('springs', {}).items():
        springs[sn] = dict(sc)
        springs[sn]['pivot'] = tfg(sc['pivot'])
        springs[sn]['tip'] = tfg(sc['tip'])
    final = []
    for bone, tx, vs in quads:
        final.append([bone, tx, [[tf(v[0]), v[1], v[2]] for v in vs]])
    # pose de reposo EF: los brazos del modelo estan en T; se giran en bloque sobre el hombro para que cuelguen
    for fx in cfg.get('rest_fix', []):
        piv = tfg(fx['pivot']); R = rot_y(fx['deg_y'])
        grp = set(fx['bones'])
        for q in final:
            if q[0] in grp:
                for v in q[2]:
                    v[0] = piv + R @ (v[0] - piv); v[2] = R @ v[2]
        for nm in fx.get('move_joints', []):
            joints[nm] = piv + R @ (joints[nm] - piv)
    # salida
    os.makedirs(out, exist_ok=True)
    tex_names = []
    used = sorted(set(q[1] for q in final))
    tmap = {}
    for k, mi in enumerate(used):
        uri = g['images'][tex_src[mi]]['uri']
        data = base64.b64decode(uri.split(',', 1)[1])
        fn = f"{cfg['name']}_{k}.png"
        open(os.path.join(out, fn), 'wb').write(data)
        tmap[mi] = k; tex_names.append(fn)
    bones = {}
    for bone, tx, vs in final:
        bones.setdefault(bone, []).append((tmap[tx], vs))
    model = {'format': 1, 'name': cfg['name'], 'textures': tex_names,
             'joints': {jn: {'ef': cfg['joints'][jn].get('ef', jn), 'pivot': [round(float(x), 5) for x in joints[jn]]} for jn in joints},
             'springs': {sn: {'parent': sc['parent'], 'pivot': [round(float(x), 5) for x in sc['pivot']], 'tip': [round(float(x), 5) for x in sc['tip']],
                              'stiffness': sc.get('stiffness', 6.0), 'damping': sc.get('damping', 3.0), 'gravity': sc.get('gravity', 9.0), 'max_angle': sc.get('max_angle', 80)} for sn, sc in springs.items()},
             'hip_height': float(joints[cfg.get('root_joint', 'Root')][2]),
             'parts': []}
    for bone, lst in bones.items():
        arr = []
        for tx, vs in lst:
            q = []
            for p, uv, nr in vs:
                q += [round(float(p[0]), 5), round(float(p[1]), 5), round(float(p[2]), 5), round(float(uv[0]), 5), round(float(uv[1]), 5), round(float(nr[0]), 3), round(float(nr[1]), 3), round(float(nr[2]), 3)]
            arr.append({'t': tx, 'v': q})
        model['parts'].append({'bone': bone, 'quads': arr})
    json.dump(model, open(os.path.join(out, cfg['name'] + '.json'), 'w'), separators=(',', ':'))
    allp = np.array([v[0] for q in final for v in q[2]])
    print('quads', len(final), 'bbox', allp.min(0).round(3), allp.max(0).round(3))
    for b, l in bones.items(): print(b, len(l))

main()
