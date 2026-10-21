"""Genera las animaciones de escenario (posturas de pelea, burla, victoria, derrota) (formato Epic Fight) a partir de la animacion idle de Epic Fight.
Usa cinematica inversa para llevar la mano a puntos concretos. Ejecutar: python3 gen_anims.py <carpeta de salida>"""
import sys, json, os, numpy as np
sys.path.insert(0, os.path.dirname(__file__))
sys.path.insert(0, '/tmp/gest')
from ik import *   # fk, load, rot, R3, apply, solve, BASE_POSE

OUT = sys.argv[1] if len(sys.argv) > 1 else '/tmp/gest/out'
os.makedirs(OUT, exist_ok=True)
FPS = 30
REST = (0.0, 0.0, 0.0, 0.0)

W0_ = fk(BASE_POSE)
ARM0 = {s_: pos(W0_, 'Arm_' + s_) for s_ in 'RL'}
def mirror(t):
    # punto en espacio del brazo derecho -> el equivalente respecto al hombro izquierdo
    d = np.array(t) - ARM0['R']
    return tuple(ARM0['L'] + np.array([-d[0], d[1], d[2]]))

_cache = {}
def ang(side, target):
    if target is None: return REST
    key = (side, tuple(target))
    if key not in _cache:
        tt = target if side == 'R' else mirror(target)
        a, e = solve(tt, side)
        if e > 0.06: print('  aviso: punto lejos', side, target, round(e, 3))
        _cache[key] = a
    return _cache[key]

def smooth(t): return t * t * (3 - 2 * t)

def build(name, T, keys, loop=False, breathe=0.0):
    """keys: lista de (t, {'R':punto|None, 'L':punto|None, 'head':(x,y,z)}) ordenada; se asume reposo al inicio y al final salvo loop"""
    times = [round(i / FPS, 4) for i in range(int(T * FPS) + 1)]
    def at(t, side):
        prev = keys[0]; nxt = keys[-1]
        for k in keys:
            if k[0] <= t: prev = k
        for k in reversed(keys):
            if k[0] >= t: nxt = k
        a0 = np.array(ang(side, prev[1].get(side))); a1 = np.array(ang(side, nxt[1].get(side)))
        if nxt[0] == prev[0]: return a0
        f = smooth((t - prev[0]) / (nxt[0] - prev[0]))
        return a0 * (1 - f) + a1 * f
    def head(t):
        prev = keys[0]; nxt = keys[-1]
        for k in keys:
            if k[0] <= t: prev = k
        for k in reversed(keys):
            if k[0] >= t: nxt = k
        h0 = np.array(prev[1].get('head', (0, 0, 0)), float); h1 = np.array(nxt[1].get('head', (0, 0, 0)), float)
        if nxt[0] == prev[0]: return h0
        f = smooth((t - prev[0]) / (nxt[0] - prev[0]))
        return h0 * (1 - f) + h1 * f
    tr = {j: [] for j in order}
    for t in times:
        pose = dict(BASE_POSE)
        for side in 'RL':
            ax, ay, az, hx = at(t, side)
            pose['Arm_' + side] = BASE_POSE['Arm_' + side] @ R3(ax, ay, az)
            pose['Hand_' + side] = BASE_POSE['Hand_' + side] @ rot('x', hx)
        hx_, hy_, hz_ = head(t)
        pose['Head'] = BASE_POSE['Head'] @ R3(hx_, hy_, hz_)
        if breathe:
            pose['Chest'] = BASE_POSE['Chest'] @ rot('x', breathe * np.sin(2 * np.pi * t / max(T, 0.1)))
        for j in order: tr[j].append([float(round(v, 5)) for v in pose[j].flatten()])
    data = {'animation': [{'name': j, 'time': times, 'transform': tr[j]} for j in order]}
    with open(os.path.join(OUT, name + '.json'), 'w') as f: json.dump(data, f, separators=(',', ':'))
    print('ok', name, T)

# ---- puntos ----
GUARD = (0.16, 0.32, 1.28); GUARD2 = (0.1, 0.3, 1.18)
PUMP = (0.3, 0.1, 1.95); UPHI = (0.28, 0.15, 1.98); LIMP = (0.12, 0.0, 0.7)
N = lambda x=0, y=0, z=0: (x, y, z)

# postura de pelea (bucle): punos arriba, cabeza baja
build('stance', 2.0, [(0, {'R': GUARD, 'L': GUARD2, 'head': N(-6)}), (1.0, {'R': GUARD2, 'L': GUARD, 'head': N(-8)}), (2.0, {'R': GUARD, 'L': GUARD2, 'head': N(-6)})], loop=True, breathe=0.8)
# burla: puno al aire
build('taunt', 1.8, [(0, {}), (0.25, {'R': PUMP, 'head': N(8)}), (0.5, {'R': UPHI}), (0.75, {'R': PUMP}), (1.0, {'R': UPHI, 'head': N(10)}), (1.8, {})])
# victoria: brazos arriba, se mantiene
build('win', 3.0, [(0, {}), (0.4, {'R': UPHI, 'L': UPHI, 'head': N(12)}), (2.4, {'R': UPHI, 'L': UPHI, 'head': N(14)}), (3.0, {'R': UPHI, 'L': UPHI, 'head': N(14)})])
# derrota: brazos caidos, cabeza abajo
build('slump', 3.0, [(0, {}), (0.6, {'R': LIMP, 'L': LIMP, 'head': N(-32)}), (3.0, {'R': LIMP, 'L': LIMP, 'head': N(-34)})])
