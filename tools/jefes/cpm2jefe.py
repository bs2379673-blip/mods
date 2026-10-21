#!/usr/bin/env python3
"""Convierte un .cpmmodel (formato clasico de Custom Player Models) al formato propio del mod 'jefes'.
Uso: python3 cpm2jefe.py <modelo.cpmmodel> <id_del_jefe> <carpeta_salida>
Salida: <id>.json (huesos/cubos con sus quads ya calculados + animaciones) y <id>.png (textura).
El mod NO depende de Custom Player Models: solo usa estos dos archivos."""
import sys, os, io, json, math, struct

DIV = 32767 // 48


class R:
    def __init__(s, d): s.d = d; s.p = 0
    def read(s, n=1):
        b = s.d[s.p:s.p + n]; s.p += n
        if len(b) < n: raise EOFError
        return b
    def ub(s): return s.read()[0]
    def sb(s): v = s.ub(); return v - 256 if v > 127 else v
    def short(s): return struct.unpack('>h', s.read(2))[0]
    def varint(s):
        i = 0; j = 0
        while True:
            b = s.ub(); i |= (b & 127) << (7 * j); j += 1
            if not (b & 128): break
        return i
    def utf(s):
        n = s.varint(); return s.read(n).decode('utf8') if n else ''
    def barr(s):
        n = s.varint(); return s.read(n) if n else b''
    def block(s): return R(s.read(s.varint()))


PT = ['END', 'PLAYER', 'TEMPLATE', 'DEFINITION', 'DEFINITION_LINK', 'SKIN', 'SKIN_LINK', 'PLAYER_PARTPOS', 'RENDER_EFFECT', 'UUID_LOCK', 'ANIMATION_DATA', 'SKIN_TYPE', 'MODEL_ROOT', 'LIST_ICON', 'DUP_ROOT', 'CLONEABLE', 'SCALE', 'TEXTURE', 'ANIMATED_TEX', 'TAGS', 'PACKAGE_LINK', 'CUBES', 'ROOT_INFO', 'ANIMATION_NEW']
AT = ['END', 'POSE', 'GESTURE', 'ROT', 'POS', 'VIS', 'COLOR', 'INFO', 'ENCODING', 'CTRL_IDS', 'EXTRA', 'INTERP', 'SCALE', 'LAYER_DEFAULT', 'ORDER', 'IS_PROPERTY', 'GROUP', 'MODEL_PROFILES_ID', 'COMMAND', 'ENC', 'MUST_FINISH', 'SLIDER', 'BTN_HIDDEN']
VP = 'CUSTOM,STANDING,WALKING,RUNNING,SNEAKING,SWIMMING,FALLING,SLEEPING,RIDING,FLYING,DYING,SKULL_RENDER,GLOBAL,CREATIVE_FLYING,EATING_LEFT,EATING_RIGHT,RETRO_SWIMMING,JUMPING,SNEAK_WALK,PUNCH_LEFT,PUNCH_RIGHT,ARMOR_HEAD,ARMOR_BODY,ARMOR_LEGS,ARMOR_BOOTS,WEARING_ELYTRA,BOW_LEFT,BOW_RIGHT,CROSSBOW_LEFT,CROSSBOW_RIGHT,CROSSBOW_CH_LEFT,CROSSBOW_CH_RIGHT,TRIDENT_LEFT,TRIDENT_RIGHT,TRIDENT_SPIN,SPYGLASS_LEFT,SPYGLASS_RIGHT,HOLDING_LEFT,HOLDING_RIGHT,WEARING_SKULL,BLOCKING_LEFT,BLOCKING_RIGHT,PARROT_LEFT,PARROT_RIGHT,HURT,ON_FIRE,FREEZING,ON_LADDER,CLIMBING_ON_LADDER,SPEAKING,TOOT_HORN_LEFT,TOOT_HORN_RIGHT,IN_GUI,FIRST_PERSON_MOD,VOICE_MUTED,VR_FIRST_PERSON,VR_THIRD_PERSON_SITTING,VR_THIRD_PERSON_STANDING,FIRST_PERSON_HAND,HEALTH,HUNGER,AIR,IN_MENU,INVISIBLE,LIGHT,HEAD_ROTATION_YAW,HEAD_ROTATION_PITCH,BRUSH_LEFT,BRUSH_RIGHT,CRAWLING,SPEAR_LEFT,SPEAR_RIGHT'.split(',')


def v6(b): return [b.short() / DIV for _ in range(3)]
def ang(b): return [b.short() / 65535 * 2 * math.pi for _ in range(3)]


def old_cube(r):
    c = {}
    c['size'] = [r.ub() / 10 for _ in range(3)]
    c['pos'] = v6(r); c['off'] = v6(r); c['rot'] = ang(r)
    c['parent'] = r.varint()
    t = r.sb(); c['tex'] = t
    if t == 0: c['rgb'] = (r.ub(), r.ub(), r.ub()); c['u'] = c['v'] = 0
    else: c['u'] = r.ub(); c['v'] = r.ub(); c['rgb'] = None
    return c


def parse(path):
    d = open(path, 'rb').read(); r = R(d)
    if r.ub() != 0x53: raise SystemExit('no es un .cpmmodel')
    name = r.utf(); r.utf(); r.barr(); ovf = r.barr()
    if not ovf: raise SystemExit('este .cpmmodel no es del formato clasico (sin bloque de cubos); exportalo de nuevo desde CPM 0.6 como "modelo con cubos"')
    o = R(ovf); o.ub()
    n = o.varint(); cubes = [old_cube(o) for _ in range(n)]
    m = dict(name=name, cubes=cubes, ppos={}, sheet=None, tex=None, anim=None)
    while True:
        t = o.sb(); size = o.varint(); b = R(o.read(size)); nm = PT[t]
        if nm == 'END': break
        if nm == 'SKIN':
            m['sheet'] = (b.short(), b.short()); n2 = b.varint(); m['tex'] = b.read(n2)
        elif nm == 'PLAYER_PARTPOS':
            i = b.varint(); m['ppos'][i] = (v6(b), ang(b))
        elif nm == 'ANIMATION_DATA':
            m['anim'] = b.d
    return m


def parse_anims(data):
    b = R(data); A = {}
    def get(i): return A.setdefault(i, dict(pos={}, rot={}, add=False, loop=False, interp=0, frames=0, dur=1000))
    while True:
        t = b.sb()
        if t < 0 or t >= len(AT): break
        nm = AT[t]
        if nm == 'END': break
        blk = b.block()
        if nm == 'POSE':
            p = blk.sb(); i = blk.ub(); fl = blk.ub(); a = get(i); a['name'] = VP[p]; a['kind'] = 'pose'; a['add'] = bool(fl & 1)
            if VP[p] == 'CUSTOM': a['name'] = blk.utf()
        elif nm == 'GESTURE':
            i = blk.ub(); name = blk.utf(); fl = blk.ub(); a = get(i); a['name'] = name; a['kind'] = 'gesture'; a['add'] = bool(fl & 1); a['loop'] = bool(fl & 2)
        elif nm == 'INFO':
            i = blk.ub(); cnt = blk.ub(); fr = blk.ub(); dur = blk.short(); a = get(i)
            a['comps'] = [blk.varint() for _ in range(cnt)]; a['frames'] = fr; a['dur'] = dur
        elif nm == 'POS':
            i = blk.ub(); c = blk.ub(); a = get(i); a['pos'][c] = [v6(blk) for _ in range(a['frames'])]
        elif nm == 'ROT':
            i = blk.ub(); c = blk.ub(); a = get(i); a['rot'][c] = [ang(blk) for _ in range(a['frames'])]
        elif nm == 'INTERP':
            i = blk.ub(); get(i)['interp'] = blk.sb()
    out = []
    for i in sorted(A):
        a = A[i]
        if 'name' not in a or not a.get('comps'): continue
        tracks = []
        for ci, cid in enumerate(a['comps']):
            r = a['rot'].get(ci); p = a['pos'].get(ci)
            if r is None and p is None: continue
            tr = {'c': cid}
            if r: tr['rot'] = [round(x, 5) for f in r for x in f]
            if p: tr['pos'] = [round(x, 3) for f in p for x in f]
            tracks.append(tr)
        out.append({'name': a['name'], 'kind': a['kind'], 'frames': a['frames'], 'dur': a['dur'], 'add': a['add'], 'loop': a['loop'], 'interp': a['interp'], 'tracks': tracks})
    return out


def quads_for(c, SW, SH, TW, TH):
    """caras del cubo en coordenadas locales (px). Cada quad: 4 vertices (x,y,z,u,v) con normal hacia afuera."""
    x, y, z = c['off']; w, h, d = c['size']
    ex, ey, ez = x + w, y + h, z + d
    V = {'7': (x, y, z), 'v': (ex, y, z), '1': (ex, ey, z), '2': (x, ey, z), '3': (x, y, ez), '4': (ex, y, ez), '5': (ex, ey, ez), '6': (x, ey, ez)}
    ts = abs(c['tex']) if c['tex'] != 0 else 1
    dx = math.ceil(w * ts); dy = math.ceil(h * ts); dz = math.ceil(d * ts)
    U = c['u'] * ts; Vv = c['v'] * ts
    f4 = U; f5 = U + dz; f6 = U + dz + dx; f7 = U + dz + dx + dx; f8 = U + dz + dx + dz; f9 = U + dz + dx + dz + dx
    f10 = Vv; f11 = Vv + dz; f12 = Vv + dz + dy
    # (vertices, u1, v1, u2, v2, direccion exterior)
    faces = [(('4', '3', '7', 'v'), f5, f10, f6, f11, (0, -1, 0)), (('1', '2', '6', '5'), f6, f11, f7, f10, (0, 1, 0)),
             (('7', '3', '6', '2'), f4, f11, f5, f12, (-1, 0, 0)), (('v', '7', '2', '1'), f5, f11, f6, f12, (0, 0, -1)),
             (('4', 'v', '1', '5'), f6, f11, f8, f12, (1, 0, 0)), (('3', '4', '5', '6'), f8, f11, f9, f12, (0, 0, 1))]
    out = []
    for names, u1, v1, u2, v2, nd in faces:
        P = [V[k] for k in names]
        uvs = [(u2, v1), (u1, v1), (u1, v2), (u2, v2)]
        # area nula: cara degenerada
        a = [P[1][i] - P[0][i] for i in range(3)]; b = [P[3][i] - P[0][i] for i in range(3)]
        n = [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]
        ln = math.sqrt(sum(t * t for t in n))
        if ln < 1e-6: continue
        if c['tex'] != 0 and (u2 - u1 == 0 or v2 - v1 == 0): continue
        if sum(n[i] * nd[i] for i in range(3)) < 0:
            P = P[::-1]; uvs = uvs[::-1]
        q = []
        for (px, py, pz), (u, v) in zip(P, uvs):
            if c['tex'] == 0: u, v = 0.5, TH - 0.5      # pixel de color (ver colorear)
            q += [round(px, 3), round(py, 3), round(pz, 3), round(u / SW, 5), round(v / SH, 5)]
        out.append(q)
    return out


def main():
    src, bid, outdir = sys.argv[1:4]
    m = parse(src)
    cubes = m['cubes']; SW, SH = m['sheet']
    from PIL import Image
    img = Image.open(io.BytesIO(m['tex'])).convert('RGBA')
    TW, TH = img.size
    # los cubos de un solo color usan un pixel extra al final de la textura
    colored = [c for c in cubes if c['tex'] == 0]
    cmap = {}
    if colored:
        img2 = Image.new('RGBA', (TW, TH + 1)); img2.paste(img, (0, 0))
        for i, c in enumerate(sorted({tuple(c['rgb']) for c in colored})):
            cmap[tuple(c)] = i
            if i < TW: img2.putpixel((i, TH), (*c, 255))
        img = img2
    ids = len(cubes)
    base = {0: (0, 0, 0), 1: (0, 0, 0), 2: (5, 2, 0), 3: (-5, 2, 0), 4: (1.9, 12, 0), 5: (-1.9, 12, 0), 6: (0, 0, 0)}
    root_off = {}
    for i in range(7):
        b = list(base[i])
        if i in m['ppos']:
            p, r = m['ppos'][i]; b = [b[k] + p[k] for k in range(3)]
        root_off[i] = b
    used_roots = sorted({c['parent'] for c in cubes if c['parent'] < 10})
    out_cubes = []
    for i, c in enumerate(cubes):
        cid = i + 10
        q = []
        if c['size'][0] or c['size'][1] or c['size'][2]:
            q = quads_for(c, SW, img.size[1] if False else SH, TW, SH)
        out_cubes.append({'id': cid, 'p': c['parent'], 'pos': [round(t, 3) for t in c['pos']], 'rot': [round(t, 5) for t in c['rot']], 'q': q})
    # altura del suelo: el punto mas bajo del modelo en reposo
    import numpy as np
    def rotzyx(x, y, z):
        cx, sx = math.cos(x), math.sin(x); cy, sy = math.cos(y), math.sin(y); cz, sz = math.cos(z), math.sin(z)
        return np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]) @ np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]) @ np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    world = {}
    def wm(cid):
        if cid in world: return world[cid]
        c = cubes[cid - 10]; M = np.eye(4); M[:3, :3] = rotzyx(*c['rot']); M[:3, 3] = c['pos']
        if c['parent'] >= 10: P = wm(c['parent'])
        else:
            P = np.eye(4); P[:3, 3] = root_off[c['parent']]
        world[cid] = P @ M; return world[cid]
    lo = -1e9; hi = 1e9
    for i, c in enumerate(out_cubes):
        M = wm(c['id'])
        for q in c['q']:
            for k in range(4):
                p = M @ np.array([q[k * 5], q[k * 5 + 1], q[k * 5 + 2], 1.0])
                lo = max(lo, p[1]); hi = min(hi, p[1])
    anims = parse_anims(m['anim']) if m['anim'] else []
    model = {'id': bid, 'name': m['name'], 'roots': {str(r): [round(t, 3) for t in root_off[r]] for r in used_roots}, 'ground': round(lo, 3), 'top': round(hi, 3), 'cubes': out_cubes, 'anims': anims}
    os.makedirs(outdir, exist_ok=True)
    with open(os.path.join(outdir, bid + '.json'), 'w') as f: json.dump(model, f, separators=(',', ':'))
    img.save(os.path.join(outdir, bid + '.png'))
    print(f"{m['name']}: {len(cubes)} cubos, {sum(len(c['q']) for c in out_cubes)} quads, suelo y={lo:.1f}px, tope y={hi:.1f}px, animaciones: {[a['name'] for a in anims]}")


if __name__ == '__main__':
    main()
