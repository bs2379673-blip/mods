"""Texturas de StarSpirits (propiedad de Starlevi). Genera:
  - texturas de los 8 espiritus (64x80, mismo mapa UV que las formas del mod) + brillos
  - plantilla ejemplo_textura.png para que los admins pinten sus espiritus
  - texturas de bloques: pilar de extraccion y maceta
Uso: python3 texturas.py  (desde la carpeta tools)"""
import math, os, random
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "src", "main", "resources", "assets", "starspirits")
ENT = os.path.join(RES, "textures", "entity")
BLK = os.path.join(RES, "textures", "block")
os.makedirs(ENT, exist_ok=True)
os.makedirs(BLK, exist_ok=True)
W, H = 64, 80

# piezas: nombre -> (u, v, ancho, alto, fondo)  (UV de caja, igual que Forms.java)
PARTS = {
    "cuerpo": (0, 0, 6, 6, 6), "pierna_der": (24, 0, 2, 3, 2), "pierna_izq": (32, 0, 2, 3, 2),
    "brazo_der": (40, 0, 1, 3, 1), "brazo_izq": (44, 0, 1, 3, 1), "parpados": (48, 0, 5, 2, 1),
    "tallo": (60, 0, 1, 4, 1),
    "copa": (0, 12, 10, 3, 10), "copa_top": (40, 12, 6, 2, 6),
    "petalos": (0, 25, 12, 0, 12), "flor_centro": (48, 25, 4, 2, 4),
    "copa_arbol": (0, 37, 7, 5, 7), "hoja_copa": (28, 37, 4, 3, 4), "petalo": (44, 37, 3, 0, 6),
    "campana": (0, 49, 6, 5, 6), "campana_borde": (24, 49, 7, 1, 7),
    "rosa": (0, 60, 5, 4, 5), "rosa_base": (20, 60, 6, 2, 6), "hoja": (44, 60, 3, 0, 2),
}


def faces(u, v, w, h, d):
    """Rectangulos (x0, y0, x1, y1) de cada cara en la textura."""
    return {
        "arriba": (u + d, v, u + d + w, v + d), "abajo": (u + d + w, v, u + d + w + w, v + d),
        "der": (u, v + d, u + d, v + d + h), "frente": (u + d, v + d, u + d + w, v + d + h),
        "izq": (u + d + w, v + d, u + d + w + d, v + d + h), "atras": (u + d + w + d, v + d, u + d + w + d + w, v + d + h),
    }


def hexc(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def shade(c, k):
    return tuple(max(0, min(255, int(x * k))) for x in c[:3]) + (c[3],)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


def paint_box(img, part, base, top=None, noise=0.05, seed=0):
    rnd = random.Random(seed)
    u, v, w, h, d = PARTS[part]
    px = img.load()
    for name, (x0, y0, x1, y1) in faces(u, v, w, h, d).items():
        for y in range(y0, y1):
            for x in range(x0, x1):
                if not (0 <= x < W and 0 <= y < H):
                    continue
                c = top if (name == "arriba" and top) else base
                k = 1.0
                if name in ("der", "frente", "izq", "atras") and y1 - y0 > 1:
                    k = 1.08 - 0.22 * (y - y0) / (y1 - y0 - 1)
                if name == "abajo":
                    k = 0.78
                if name in ("der", "izq"):
                    k *= 0.93
                k *= 1 + (rnd.random() - 0.5) * noise
                px[x, y] = shade(c, k)


def face_rect(part, face):
    return faces(*PARTS[part])[face]


def dot(img, x, y, c):
    if 0 <= x < W and 0 <= y < H:
        img.load()[x, y] = c


def face_and_lids(img, body, eye="#2B1B2E", cheek="#FF8FA3", mouth="#8C3B4A"):
    # carita en el frente del cuerpo: frente = (6,6)-(12,12)
    e = hexc(eye)
    for (x, y) in [(7, 8), (7, 9), (10, 8), (10, 9)]:
        dot(img, x, y, e)
    dot(img, 7, 8, mix(e, (255, 255, 255, 255), 0.55))  # brillito
    dot(img, 10, 8, mix(e, (255, 255, 255, 255), 0.55))
    dot(img, 6, 10, hexc(cheek))
    dot(img, 11, 10, hexc(cheek))
    dot(img, 8, 10, hexc(mouth))
    dot(img, 9, 10, hexc(mouth))
    # parpados: color del cuerpo un poco mas oscuro, con la linea del ojo cerrado
    paint_box(img, "parpados", shade(body, 0.95), noise=0.02)
    x0, y0, x1, y1 = face_rect("parpados", "frente")
    for x in (x0 + 1, x0 + 3):
        dot(img, x, y0 + 1, shade(e, 1.6))


def plane(img, part, fn):
    """Planos de grosor 0 (petalos, hojas): fn(x01, y01) -> color o None, en arriba y abajo."""
    for fname in ("arriba", "abajo"):
        x0, y0, x1, y1 = face_rect(part, fname)
        for y in range(y0, y1):
            for x in range(x0, x1):
                c = fn((x - x0 + 0.5) / (x1 - x0), (y - y0 + 0.5) / (y1 - y0))
                if c is not None:
                    dot(img, x, y, c if fname == "arriba" else shade(c, 0.85))


def spots(img, part, color, n, seed, face="arriba"):
    rnd = random.Random(seed)
    x0, y0, x1, y1 = face_rect(part, face)
    for _ in range(n):
        x, y = rnd.randrange(x0, x1), rnd.randrange(y0, y1)
        dot(img, x, y, color)
        if rnd.random() < 0.5:
            dot(img, x + 1, y, color)


def side_spots(img, part, color, n, seed):
    for i, f in enumerate(("der", "frente", "izq", "atras")):
        spots(img, part, color, n, seed + i, f)


def legs_arms(img, body):
    for p in ("pierna_der", "pierna_izq"):
        paint_box(img, p, shade(body, 0.86), noise=0.04, seed=3)
    for p in ("brazo_der", "brazo_izq"):
        paint_box(img, p, shade(body, 0.95), noise=0.04, seed=4)


def new():
    return Image.new("RGBA", (W, H), (0, 0, 0, 0))


def base(body_hex, seed):
    img = new()
    body = hexc(body_hex)
    paint_box(img, "cuerpo", body, noise=0.06, seed=seed)
    legs_arms(img, body)
    face_and_lids(img, body)
    return img, body


def daisy(white, yellow_tint):
    def fn(x, y):
        dx, dy = x - 0.5, y - 0.5
        r = math.hypot(dx, dy)
        a = math.atan2(dy, dx)
        petal = 0.32 + 0.16 * abs(math.cos(a * 4))
        if r > petal + 0.02:
            return None
        k = 1 - 0.18 * (r / 0.5)
        c = mix(hexc(white), hexc(yellow_tint), max(0, 0.35 - r) * 1.4)
        return shade(c, k)
    return fn


def lotus_petal(base_hex, tip_hex):
    def fn(x, y):
        # y=0 punta (lejos del centro), y=1 base
        width = 0.5 * math.sin(math.pi * min(1, (1 - y) * 0.9 + 0.1))
        if abs(x - 0.5) > width + 0.15:
            return None
        return mix(hexc(base_hex), hexc(tip_hex), 1 - y)
    return fn


def leaf(hex_):
    def fn(x, y):
        return shade(hexc(hex_), 0.9 + 0.2 * y)
    return fn


def save(img, name):
    img.save(os.path.join(ENT, name + ".png"))


# ---------------------------------------------------------------- espiritus
img, body = base("#F3E3C8", 1)  # Champi
red = hexc("#E5483F")
paint_box(img, "copa", red, noise=0.06, seed=11)
paint_box(img, "copa_top", shade(red, 1.05), noise=0.06, seed=12)
for p, n in (("copa", 9), ("copa_top", 4)):
    spots(img, p, hexc("#FFF6EA"), n, 21)
side_spots(img, "copa", hexc("#FFF6EA"), 3, 30)
x0, y0, x1, y1 = face_rect("copa", "abajo")  # laminillas debajo del sombrero
for y in range(y0, y1):
    for x in range(x0, x1):
        dot(img, x, y, hexc("#EBD2B0") if (x + y) % 2 else hexc("#D9BC95"))
save(img, "champi")

img, body = base("#D9D2F5", 2)  # Luminon
teal = hexc("#2A6F8F")
paint_box(img, "copa", teal, noise=0.08, seed=13)
paint_box(img, "copa_top", shade(teal, 1.1), noise=0.08, seed=14)
glow = new()
for p, n in (("copa", 10), ("copa_top", 5)):
    spots(img, p, hexc("#7FF7FF"), n, 41)
    spots(glow, p, hexc("#7FF7FF"), n, 41)
for i, f in enumerate(("der", "frente", "izq", "atras")):
    spots(img, "copa", hexc("#7FF7FF"), 3, 50 + i, f)
    spots(glow, "copa", hexc("#7FF7FF"), 3, 50 + i, f)
for (x, y) in [(7, 8), (7, 9), (10, 8), (10, 9)]:
    dot(img, x, y, hexc("#3BD6FF"))
    dot(glow, x, y, hexc("#9AF4FF"))
save(img, "luminon")
save(glow, "luminon_brillo")

img, body = base("#BFE8A0", 3)  # Margarita
plane(img, "petalos", daisy("#FFFFFF", "#FFF2B3"))
paint_box(img, "flor_centro", hexc("#FFD23F"), noise=0.12, seed=15)
spots(img, "flor_centro", hexc("#E8A317"), 5, 60)
save(img, "margarita")

img, body = base("#DDEFB8", 4)  # Brotecito
paint_box(img, "tallo", hexc("#6E8B3D"), noise=0.05, seed=16)
paint_box(img, "hoja_copa", hexc("#5FB548"), noise=0.18, seed=17)
side_spots(img, "hoja_copa", hexc("#3F8F34"), 3, 70)
spots(img, "hoja_copa", hexc("#8BDB6A"), 4, 75)
plane(img, "hoja", leaf("#4FA83D"))
save(img, "brotecito")

img, body = base("#CFE6FF", 5)  # Campanita
blue = hexc("#5B7CFA")
paint_box(img, "campana", blue, top=shade(blue, 1.15), noise=0.06, seed=18)
paint_box(img, "campana_borde", hexc("#8FA7FF"), noise=0.05, seed=19)
paint_box(img, "tallo", hexc("#5E9E4A"), noise=0.05, seed=20)
side_spots(img, "campana", hexc("#7E98FF"), 2, 80)
save(img, "campanita")

img, body = base("#F7C6CF", 6)  # Rosita
rose = hexc("#D7263D")
paint_box(img, "rosa", rose, noise=0.06, seed=21)
paint_box(img, "rosa_base", shade(rose, 0.85), noise=0.06, seed=22)
x0, y0, x1, y1 = face_rect("rosa", "arriba")  # espiral de la rosa
for y in range(y0, y1):
    for x in range(x0, x1):
        dx, dy = x - (x0 + x1 - 1) / 2, y - (y0 + y1 - 1) / 2
        r = math.hypot(dx, dy)
        a = math.atan2(dy, dx)
        if abs(((a / (2 * math.pi)) * 2 + r * 0.55) % 1 - 0.5) < 0.18:
            dot(img, x, y, shade(rose, 0.7))
for f in ("der", "frente", "izq", "atras"):
    x0, y0, x1, y1 = face_rect("rosa", f)
    for x in range(x0, x1, 2):
        dot(img, x, y0 + 1, shade(rose, 0.75))
plane(img, "hoja", leaf("#3E8E41"))
save(img, "rosita")

img, body = base("#FBE9F1", 7)  # Loto lunar
glow = new()
plane(img, "petalo", lotus_petal("#F48FB8", "#FFFFFF"))
plane(glow, "petalo", lambda x, y: (255, 220, 240, 255) if y < 0.35 and abs(x - 0.5) < 0.3 else None)
paint_box(img, "flor_centro", hexc("#FFD65C"), noise=0.1, seed=23)
paint_box(glow, "flor_centro", hexc("#FFE89A"), noise=0.0, seed=23)
for (x, y) in [(7, 8), (10, 8)]:
    dot(glow, x, y, hexc("#FFFFFF", 160))
save(img, "loto")
save(glow, "loto_brillo")

img, body = base("#E9DDBB", 8)  # Bonsai ancestral
paint_box(img, "tallo", hexc("#7A4E2D"), noise=0.08, seed=24)
paint_box(img, "copa_arbol", hexc("#4E9A55"), noise=0.2, seed=25)
paint_box(img, "hoja_copa", hexc("#5DAE60"), noise=0.2, seed=26)
for p in ("copa_arbol", "hoja_copa"):
    spots(img, p, hexc("#FFB7D5"), 5, 90)
    side_spots(img, p, hexc("#FFB7D5"), 2, 95)
    side_spots(img, p, hexc("#356F3B"), 3, 99)
save(img, "bonsai")

# ---------------------------------------------------------------- plantilla para admins
tpl = new()
cols = ["#E57373", "#64B5F6", "#81C784", "#FFD54F", "#BA68C8", "#4DD0E1", "#FF8A65", "#A1887F", "#90A4AE", "#F06292"]
for i, (name, (u, v, w, h, d)) in enumerate(PARTS.items()):
    c = hexc(cols[i % len(cols)])
    if h == 0:
        plane(tpl, name, lambda x, y, c=c: c)
    else:
        paint_box(tpl, name, c, noise=0.0)
        fr = face_rect(name, "frente")
        for x in range(fr[0], fr[2]):
            dot(tpl, x, fr[1], shade(c, 0.6))
face_and_lids(tpl, hexc(cols[0]))
tpl.save(os.path.join(RES, "espiritus", "ejemplo_textura.png"))

# ---------------------------------------------------------------- bloques


def tex16(fn, name, seed=0):
    rnd = random.Random(seed)
    t = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            c = fn(x, y, rnd)
            if c is not None:
                t.load()[x, y] = c
    t.save(os.path.join(BLK, name + ".png"))


def stone(x, y, r):
    v = 120 + r.randint(-14, 14)
    if (x + y * 3) % 7 == 0:
        v -= 18
    return (v, v + 4, v + 10, 255)


def mossy(x, y, r):
    c = stone(x, y, r)
    if r.random() < 0.35 + 0.3 * (1 - y / 15):
        g = r.randint(-12, 12)
        return (70 + g, 125 + g, 60 + g, 255)
    return c


def runes(x, y, r):
    c = stone(x, y, r)
    # runitas verdes que brillan apenas
    if (x in (4, 11) and 3 <= y <= 12) or (y in (4, 11) and 6 <= x <= 9) or ((x - y) % 6 == 0 and 5 <= x <= 10 and 5 <= y <= 10):
        return (150, 230, 160, 255)
    return c


def bowl_top(x, y, r):
    d = math.hypot(x - 7.5, y - 7.5)
    if d < 5:
        g = r.randint(-10, 10)
        return (80 + g, 60 + g, 45 + g, 255)  # tierra
    return mossy(x, y, r)


def flowers(x, y, r):
    # matita con florcitas (para los planos cruzados)
    if y >= 9 and (x * 7 + y * 3) % 5 < 2 and r.random() < 0.8:
        g = r.randint(-15, 15)
        return (60 + g, 140 + g, 60 + g, 255)
    if y in (5, 6, 7) and x in (3, 4, 11, 12) or y in (3, 4) and x in (7, 8):
        return [(255, 235, 240, 255), (255, 214, 92, 255), (190, 160, 255, 255)][(x + y) % 3]
    if 7 <= y <= 15 and x in (3, 7, 8, 11) or 5 <= y <= 15 and x in (4, 12):
        return (70, 150, 70, 255)
    return None


def terracotta(x, y, r):
    v = r.randint(-8, 8)
    base_c = (196 + v, 110 + v, 72 + v, 255)
    if y in (3, 4) or (y == 10 and x % 4 == 0):
        return (235, 190, 120, 255)  # guarda dorada
    if y == 7 and x % 3 == 1:
        return (120, 180, 110, 255)  # hojitas pintadas
    return base_c


def soil(x, y, r):
    g = r.randint(-14, 14)
    if r.random() < 0.12:
        return (110 + g, 170 + g, 90 + g, 255)
    return (92 + g, 64 + g, 44 + g, 255)


tex16(stone, "pilar_piedra", 1)
tex16(mossy, "pilar_musgo", 2)
tex16(runes, "pilar_runas", 3)
tex16(bowl_top, "pilar_cuenco", 4)
tex16(flowers, "pilar_flores", 5)
tex16(terracotta, "maceta", 6)
tex16(soil, "maceta_tierra", 7)
print("listo")
