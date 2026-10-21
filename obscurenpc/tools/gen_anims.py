"""Genera las animaciones de gestos del NPC (formato Epic Fight) a partir de la animacion idle de Epic Fight.
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

# ---- puntos (mano derecha; la izquierda se espeja) ----
CHEST = (0.12, 0.35, 1.2); OPEN = (0.33, 0.45, 1.16); OPEN2 = (0.38, 0.52, 1.26); UP = (0.4, 0.3, 1.45)
LOW = (0.36, 0.42, 1.0); WAVE = (0.45, 0.1, 1.85); WAVE2 = (0.56, 0.1, 1.8); POINT = (0.3, 0.6, 1.38)
CHIN = (0.04, 0.2, 1.38); CROSS = (-0.15, 0.2, 1.2); BACK = (0.1, -0.25, 0.85); SALUTE = (0.14, 0.22, 1.66)
WIDE = (0.5, 0.35, 1.2); HIP = (0.22, 0.05, 1.0); PALM = (0.3, 0.42, 1.3)

N = lambda x=0, y=0, z=0: (x, y, z)

# gestos al hablar (terminan en reposo)
build('talk_a', 2.4, [(0, {}), (0.35, {'R': OPEN, 'head': N(-6)}), (0.8, {'R': CHEST}), (1.25, {'R': OPEN2, 'head': N(-8, 6)}), (1.7, {'R': UP}), (2.05, {'R': CHEST, 'head': N(-5)}), (2.4, {})])
build('talk_b', 2.6, [(0, {}), (0.4, {'R': OPEN, 'L': OPEN2, 'head': N(-5)}), (0.9, {'R': OPEN2, 'L': OPEN}), (1.4, {'R': OPEN, 'L': OPEN2, 'head': N(-7, -6)}), (1.9, {'R': OPEN2, 'L': OPEN}), (2.6, {})])
build('talk_c', 2.2, [(0, {}), (0.35, {'L': OPEN, 'head': N(-5, -5)}), (0.85, {'L': CHEST}), (1.3, {'L': UP, 'head': N(-6, 5)}), (1.75, {'L': CHEST}), (2.2, {})])
build('talk_d', 2.0, [(0, {}), (0.3, {'R': UP, 'head': N(-4)}), (0.55, {'R': LOW}), (0.85, {'R': UP}), (1.1, {'R': LOW, 'head': N(-8)}), (1.4, {'R': UP}), (2.0, {})])
build('talk_e', 2.4, [(0, {}), (0.4, {'R': PALM, 'L': PALM}), (1.0, {'R': PALM, 'L': PALM, 'head': N(-5)}), (1.5, {'R': OPEN2, 'L': OPEN2}), (2.4, {})])
build('point', 1.8, [(0, {}), (0.4, {'R': POINT, 'head': N(0, -8)}), (1.3, {'R': POINT}), (1.8, {})])
build('wave', 2.4, [(0, {}), (0.4, {'R': WAVE}), (0.7, {'R': WAVE2}), (1.0, {'R': WAVE}), (1.3, {'R': WAVE2}), (1.6, {'R': WAVE}), (2.4, {})])
build('think', 3.0, [(0, {}), (0.5, {'R': CHIN, 'L': (0.2, 0.15, 1.12), 'head': N(-4, 0, 8)}), (2.4, {'R': CHIN, 'L': (0.2, 0.15, 1.12), 'head': N(-4, 0, 8)}), (3.0, {})])
build('salute', 1.8, [(0, {}), (0.35, {'R': SALUTE}), (1.2, {'R': SALUTE}), (1.8, {})])
build('shrug', 1.8, [(0, {}), (0.3, {'R': WIDE, 'L': WIDE, 'head': N(0, 0, 6)}), (1.3, {'R': WIDE, 'L': WIDE, 'head': N(0, 0, 6)}), (1.8, {})])
build('nod', 1.2, [(0, {}), (0.2, {'head': N(-16)}), (0.4, {'head': N(0)}), (0.6, {'head': N(-16)}), (0.85, {'head': N(0)}), (1.2, {})])
build('shake_head', 1.4, [(0, {}), (0.2, {'head': N(0, 22)}), (0.5, {'head': N(0, -22)}), (0.8, {'head': N(0, 22)}), (1.1, {'head': N(0, -10)}), (1.4, {})])
build('look_around', 3.0, [(0, {}), (0.6, {'head': N(0, 40)}), (1.4, {'head': N(0, 40)}), (2.2, {'head': N(0, -40)}), (2.7, {'head': N(0, -40)}), (3.0, {})])
build('facepalm', 2.2, [(0, {}), (0.45, {'R': (0.05, 0.2, 1.52), 'head': N(-14)}), (1.7, {'R': (0.05, 0.2, 1.52), 'head': N(-14)}), (2.2, {})])
build('beckon', 2.2, [(0, {}), (0.3, {'R': UP}), (0.6, {'R': PALM}), (0.9, {'R': UP}), (1.2, {'R': PALM}), (1.5, {'R': UP}), (2.2, {})])
build('stop', 1.8, [(0, {}), (0.3, {'R': (0.3, 0.5, 1.5)}), (1.4, {'R': (0.3, 0.5, 1.5)}), (1.8, {})])
# posturas (bucle: se quedan)
build('cross_arms', 3.0, [(0, {'R': CROSS, 'L': CROSS}), (3.0, {'R': CROSS, 'L': CROSS})], loop=True, breathe=0.6)
build('hands_back', 3.0, [(0, {'R': BACK, 'L': BACK}), (3.0, {'R': BACK, 'L': BACK})], loop=True, breathe=0.6)
build('hands_hip', 3.0, [(0, {'R': HIP, 'L': HIP}), (3.0, {'R': HIP, 'L': HIP})], loop=True, breathe=0.6)
build('think_pose', 3.0, [(0, {'R': CHIN, 'L': (0.2, 0.15, 1.12), 'head': N(-4, 0, 8)}), (3.0, {'R': CHIN, 'L': (0.2, 0.15, 1.12), 'head': N(-4, 0, 8)})], loop=True, breathe=0.4)
build('guard', 3.0, [(0, {'R': (0.08, 0.28, 1.0), 'L': (0.08, 0.28, 1.0), 'head': N(0)}), (3.0, {'R': (0.08, 0.28, 1.0), 'L': (0.08, 0.28, 1.0)})], loop=True, breathe=0.5)
