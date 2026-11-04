"""Personaje de prueba con la forma de un export de Roblox Studio (R15 + un sombrero), para el test visual.
Frente hacia -z, derecha +x, brazos abiertos 30 grados (para probar que se bajan). Uso: muneco.py <carpeta>"""
import math, os, struct, sys, zlib

out = sys.argv[1]
os.makedirs(out, exist_ok=True)
V, VT, groups = [], [], []


def box(name, mat, x0, y0, z0, x1, y1, z1, rot=None, piv=None, uvr=(0, 0, 1, 1)):
    """Caja con UV de la region uvr de la textura; rot = angulo (z) alrededor de piv."""
    vs = [(x, y, z) for x in (x0, x1) for y in (y0, y1) for z in (z0, z1)]
    if rot:
        c, s = math.cos(rot), math.sin(rot)
        vs = [(piv[0] + c * (x - piv[0]) - s * (y - piv[1]), piv[1] + s * (x - piv[0]) + c * (y - piv[1]), z) for x, y, z in vs]
    base = len(V)
    V.extend(vs)
    u0, v0, u1, v1 = uvr
    tb = len(VT)
    VT.extend([(u0, v0), (u1, v0), (u1, v1), (u0, v1)])
    idx = lambda x, y, z: base + x * 4 + y * 2 + z
    # caras (al escribir el .obj se dan vuelta para quedar como Roblox)
    faces = [
        [idx(0, 0, 0), idx(1, 0, 0), idx(1, 1, 0), idx(0, 1, 0)],  # -z (frente)
        [idx(1, 0, 1), idx(0, 0, 1), idx(0, 1, 1), idx(1, 1, 1)],  # +z
        [idx(0, 0, 1), idx(0, 0, 0), idx(0, 1, 0), idx(0, 1, 1)],  # -x
        [idx(1, 0, 0), idx(1, 0, 1), idx(1, 1, 1), idx(1, 1, 0)],  # +x
        [idx(0, 1, 0), idx(1, 1, 0), idx(1, 1, 1), idx(0, 1, 1)],  # +y
        [idx(0, 0, 1), idx(1, 0, 1), idx(1, 0, 0), idx(0, 0, 0)],  # -y
    ]
    groups.append((name, mat, faces, tb))


# frente -z: el orden de arriba da normales hacia afuera si x crece a la derecha... se arma igual que Roblox
P = 'Muneco'
box(P + '1', 'Cuerpo', 0.05, 2.0, -0.45, 0.95, 4.0, 0.45, uvr=(0.5, 0.5, 1, 1))      # pierna der (+x)
box(P + '2', 'Cuerpo', -0.95, 2.0, -0.45, -0.05, 4.0, 0.45, uvr=(0.5, 0.5, 1, 1))    # pierna izq
box(P + '3', 'Cuerpo', -1.0, 4.0, -0.5, 1.0, 6.0, 0.5, uvr=(0, 0.5, 0.5, 1))         # torso
ang = math.radians(30)
box(P + '4', 'Derecho', 1.0, 4.0, -0.5, 2.0, 6.0, 0.5, rot=ang, piv=(1.0, 6.0))      # brazo der abierto
box(P + '5', 'Izquierdo', -2.0, 4.0, -0.5, -1.0, 6.0, 0.5, rot=-ang, piv=(-1.0, 6.0))  # brazo izq abierto
box(P + '6', 'Cara', -0.6, 6.0, -0.6, 0.6, 7.2, 0.6)                                   # cabeza
box(P + '7', 'Cuerpo', 0.05, 1.7, -0.5, 0.95, 2.0, 0.3, uvr=(0, 0, 0.5, 0.5))         # pie der
box(P + '8', 'Cuerpo', -0.95, 1.7, -0.5, -0.05, 2.0, 0.3, uvr=(0, 0, 0.5, 0.5))       # pie izq
box(P + '9', 'Cuerpo', 0.2, 1.0, -0.4, 0.8, 1.7, 0.2, uvr=(0, 0, 0.5, 0.5))           # (relleno para que haya varias piezas)
# sombrero (accesorio): copa y ala
box('Handle1', 'Handle1Mtl', -0.75, 7.15, -0.75, 0.75, 7.3, 0.75)
box('Handle1', 'Handle1Mtl', -0.45, 7.3, -0.45, 0.45, 8.0, 0.45)
# pantalon 3D (ropa en capas): una sola pieza que cubre cadera y las dos piernas, bien subdividida
def sbox(name, mat, x0, y0, z0, x1, y1, z1, n=8):
    tb = len(VT)
    VT.extend([(0, 0), (1, 0), (1, 1), (0, 1)])
    faces = []
    def quad(p):
        base = len(V)
        V.extend(p)
        faces.append([base, base + 1, base + 2, base + 3])
    for i in range(n):
        for j in range(n):
            a0, a1, b0, b1 = i / n, (i + 1) / n, j / n, (j + 1) / n
            X = lambda t: x0 + (x1 - x0) * t
            Y = lambda t: y0 + (y1 - y0) * t
            Z = lambda t: z0 + (z1 - z0) * t
            quad([(X(a0), Y(b0), z0), (X(a1), Y(b0), z0), (X(a1), Y(b1), z0), (X(a0), Y(b1), z0)])  # frente
            quad([(X(a1), Y(b0), z1), (X(a0), Y(b0), z1), (X(a0), Y(b1), z1), (X(a1), Y(b1), z1)])  # atras
            quad([(x0, Y(b0), Z(a1)), (x0, Y(b0), Z(a0)), (x0, Y(b1), Z(a0)), (x0, Y(b1), Z(a1))])  # -x
            quad([(x1, Y(b0), Z(a0)), (x1, Y(b0), Z(a1)), (x1, Y(b1), Z(a1)), (x1, Y(b1), Z(a0))])  # +x
    groups.append((name, mat, faces, tb))


sbox('Handle2', 'Handle2Mtl', -1.1, 2.2, -0.6, 1.1, 4.3, 0.6)
# piso del lugar (tiene que ignorarse)
box('Baseplate1', 'Piso', -50, -1, -50, 50, 0, 50)

with open(os.path.join(out, 'muneco.obj'), 'w') as f:
    f.write('mtllib muneco.mtl\n')
    for v in V:
        f.write('v %f %f %f\n' % v)
    for t in VT:
        f.write('vt %f %f\n' % t)
    last = None
    for name, mat, faces, tb in groups:
        if name != last:
            f.write('g %s\n' % name)
            last = name
        f.write('usemtl %s\n' % mat)
        for q in faces:
            # orden como Roblox (antihorario visto desde afuera)
            f.write('f ' + ' '.join('%d/%d' % (q[i] + 1, tb + i + 1) for i in (3, 2, 1, 0)) + '\n')
with open(os.path.join(out, 'muneco.mtl'), 'w') as f:
    for m, col, tex, ov in [('Cuerpo', '0.2 0.5 0.9', 'cuerpo.png', True), ('Derecho', '0.9 0.2 0.2', None, False),
                            ('Izquierdo', '0.2 0.8 0.3', None, False), ('Cara', '1 0.85 0.3', 'cara.png', True),
                            ('Handle1Mtl', '0.37 0.37 0.38', 'Handle1_diff.png', True), ('Handle2Mtl', '0.95 0.6 0.1', None, False), ('Piso', '0.3 0.3 0.3', None, False)]:
        f.write('newmtl %s\nKd %s\nd 1\n' % (m, col))
        if tex:
            f.write('map_Kd %s\n' % tex)
        if ov:
            f.write('rbx_alphamode overlay\n')
        f.write('\n')
def png(path, w, h, px):
    """PNG RGBA sin librerias (px(x, y) -> (r, g, b, a))."""
    raw = b''.join(b'\x00' + bytes(c for x in range(w) for c in px(x, y)) for y in range(h))
    chunk = lambda t, d: struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    data = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b'')
    open(path, 'wb').write(data)


png(os.path.join(out, 'cuerpo.png'), 64, 64, lambda x, y: (255, 255, 255, 255) if 4 <= x <= 28 and 4 <= y <= 12 else (0, 0, 0, 0))


def cara(x, y):
    if (x - 21) ** 2 + (y - 26) ** 2 < 30 or (x - 43) ** 2 + (y - 26) ** 2 < 30:
        return (0, 0, 0, 255)
    if 36 < y < 42 and 22 < x < 42 and abs((x - 32) ** 2 / 100 + 36 - y) < 2:
        return (120, 0, 0, 255)
    return (0, 0, 0, 0)


png(os.path.join(out, 'cara.png'), 64, 64, cara)
png(os.path.join(out, 'Handle1_diff.png'), 32, 32, lambda x, y: (230, 180, 40, 255) if 12 <= y <= 18 else (40, 20, 60, 255))
open(os.path.join(out, 'avatar.json'), 'w').write('{"nombre": "Muñeco de prueba"}')
print('muneco listo en', out)
