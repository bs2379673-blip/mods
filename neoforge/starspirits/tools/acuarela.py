"""Texturas acuarela pastel de StarSpirits (propiedad de Starlevi), 256x256, para las formas
redondeadas (zonas iguales a Mesh.Uv en Java). Uso: python3 acuarela.py"""
import math, os, random
import numpy as np
from PIL import Image, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "src", "main", "resources", "assets", "starspirits")
ENT = os.path.join(RES, "textures", "entity")
S = 256
Z = {  # zonas (x0, y0, x1, y1)
    "BODY": (0, 0, 256, 112), "LIMB": (0, 112, 64, 144), "LID": (64, 112, 96, 128), "STEM": (96, 112, 128, 144),
    "PETAL": (128, 112, 192, 144), "LEAF": (192, 112, 256, 144), "CAP": (0, 144, 256, 224),
    "UNDER": (0, 224, 64, 256), "CENTER": (64, 224, 128, 256), "EXTRA": (128, 224, 256, 256),
}
EYE_U, EYE_V = 0.062, 0.53
PLUM = (107, 59, 74)


def hexc(s):
    s = s.lstrip("#")
    return np.array([int(s[i:i + 2], 16) for i in (0, 2, 4)], float)


def noise(w, h, seed, scales=((6, 0.5), (14, 0.3), (30, 0.2))):
    """Ruido suave (manchas de acuarela) entre -1 y 1."""
    rnd = np.random.RandomState(seed)
    out = np.zeros((h, w))
    for blur, amp in scales:
        a = rnd.rand(h, w) * 255
        im = Image.fromarray(a.astype("uint8")).filter(ImageFilter.GaussianBlur(blur))
        b = np.asarray(im, float)
        b = (b - b.mean()) / (b.std() + 1e-6)
        out += b * amp
    return np.clip(out / 1.6, -1, 1)


class Canvas:
    def __init__(self):
        self.rgb = np.zeros((S, S, 3))
        self.a = np.zeros((S, S))

    def region(self, name):
        x0, y0, x1, y1 = Z[name]
        return x0, y0, x1, y1

    def uvgrid(self, name):
        x0, y0, x1, y1 = Z[name]
        ys, xs = np.mgrid[y0:y1, x0:x1]
        return (xs - x0 + 0.5) / (x1 - x0), (ys - y0 + 0.5) / (y1 - y0)

    def wash(self, name, top, bottom=None, var=0.10, seed=0, grain=0.03):
        """Lavado de acuarela: degrade de arriba hacia abajo + manchas + grano de papel."""
        x0, y0, x1, y1 = Z[name]
        u, v = self.uvgrid(name)
        top = hexc(top)
        bottom = hexc(bottom) if bottom else top
        col = top[None, None, :] * (1 - v[..., None]) + bottom[None, None, :] * v[..., None]
        n = noise(x1 - x0, y1 - y0, seed)
        g = (np.random.RandomState(seed + 7).rand(y1 - y0, x1 - x0) - 0.5) * 2
        k = 1 + n[..., None] * var + g[..., None] * grain
        self.rgb[y0:y1, x0:x1] = np.clip(col * k, 0, 255)
        self.a[y0:y1, x0:x1] = 255

    def paint(self, name, mask, color, alpha=1.0):
        """Mezcla un color con una mascara (0..1) dentro de la zona."""
        x0, y0, x1, y1 = Z[name]
        c = hexc(color) if isinstance(color, str) else np.array(color, float)
        m = np.clip(mask, 0, 1)[..., None] * alpha
        self.rgb[y0:y1, x0:x1] = self.rgb[y0:y1, x0:x1] * (1 - m) + c * m

    def cut(self, name, mask):
        x0, y0, x1, y1 = Z[name]
        self.a[y0:y1, x0:x1] = np.clip(mask, 0, 1) * 255

    def save(self, path):
        img = np.dstack([self.rgb, self.a]).clip(0, 255).astype("uint8")
        Image.fromarray(img, "RGBA").save(path)


def blob(u, v, cu, cv, ru, rv, soft=0.35):
    """Mancha eliptica suave (1 adentro, 0 afuera)."""
    d = np.sqrt(((u - cu) / ru) ** 2 + ((v - cv) / rv) ** 2)
    return np.clip((1 - d) / soft, 0, 1)


def dot(u, v, cu, cv, ru, rv):
    """Circulo nitido con borde suavizado."""
    d = np.sqrt(((u - cu) / ru) ** 2 + ((v - cv) / rv) ** 2)
    return np.clip((1 - d) * 6, 0, 1)


def wrap(u, cu):
    """Distancia en u que da la vuelta (textura que envuelve una esfera)."""
    d = u - cu
    return cu + (d + 0.5) % 1 - 0.5


def face(cv, body_top, body_bottom, seed, eye="#4A2C3A", blush="#F59AA8", mouth=None, sleepy=False):
    u, v = cv.uvgrid("BODY")
    # cachetes
    for s in (-1, 1):
        cv.paint("BODY", blob(u, v, 0.5 + s * 0.112, 0.605, 0.042, 0.055, 0.8), blush, 0.75)
    # ojitos (punto con brillito)
    for s in (-1, 1):
        cu = 0.5 + s * EYE_U
        cv.paint("BODY", dot(u, v, cu, EYE_V, 0.016, 0.042), eye)
        cv.paint("BODY", dot(u, v, cu - 0.005, EYE_V - 0.015, 0.0055, 0.014), (255, 252, 248), 0.95)
    # boquita (curvita)
    mc = hexc(mouth or eye)
    du = (u - 0.5) / 0.022
    curve = 0.585 + 0.012 * (1 - du ** 2)
    m = np.clip(1 - np.abs(v - curve) / 0.0085, 0, 1) * (np.abs(du) < 1)
    cv.paint("BODY", m, mc, 0.9)
    # pecas / brillitos
    rnd = random.Random(seed)
    for _ in range(14):
        cu, cvv = rnd.random(), 0.25 + rnd.random() * 0.6
        if abs(wrap(cu, 0.5) - 0.5) < 0.16 and 0.45 < cvv < 0.7:
            continue
        cv.paint("BODY", dot(wrap(u, cu), v, cu, cvv, 0.006, 0.014), (255, 255, 255), 0.35)
    # parpados: color del cuerpo con el ojo cerrado (arquito)
    cv.wash("LID", body_top, body_top, var=0.03, seed=seed + 3, grain=0.0)
    lu, lv = cv.uvgrid("LID")
    arc = 0.55 - 0.18 * (1 - ((lu - 0.5) / 0.38) ** 2)
    cv.paint("LID", np.clip(1 - np.abs(lv - arc) / 0.09, 0, 1) * (np.abs(lu - 0.5) < 0.38), eye)


def body(cv, top, bottom, seed):
    cv.wash("BODY", top, bottom, var=0.08, seed=seed)
    u, v = cv.uvgrid("BODY")
    # luz suave arriba y sombra calida abajo
    cv.paint("BODY", np.clip((0.35 - v) / 0.35, 0, 1) * 0.5, (255, 255, 255), 0.35)
    cv.paint("BODY", np.clip((v - 0.8) / 0.2, 0, 1), PLUM, 0.18)
    cv.wash("LIMB", bottom, bottom, var=0.06, seed=seed + 1)
    lu, lv = cv.uvgrid("LIMB")
    cv.paint("LIMB", np.clip((lv - 0.75) / 0.25, 0, 1), PLUM, 0.25)


def spots(cv, zone, color, n, seed, rmin=0.025, rmax=0.05, vmin=0.1, vmax=0.85, alpha=0.95, aspect=None):
    u, v = cv.uvgrid(zone)
    x0, y0, x1, y1 = Z[zone]
    asp = aspect or (x1 - x0) / (y1 - y0)
    rnd = random.Random(seed)
    m = np.zeros_like(u)
    for _ in range(n):
        cu, cvv = rnd.random(), vmin + rnd.random() * (vmax - vmin)
        r = rmin + rnd.random() * (rmax - rmin)
        m = np.maximum(m, blob(wrap(u, cu), v, cu, cvv, r, r * asp, 0.25))
    cv.paint(zone, m, color, alpha)
    return m


def petal_zone(cv, zone, base, tip, edge, seed, vein=None, shape="petal"):
    """Petalo u hoja: u = ancho, v = 0 base a 1 punta. Borde de contorno suave."""
    cv.wash(zone, base, tip, var=0.08, seed=seed)
    u, v = cv.uvgrid(zone)
    dist = np.abs(u - 0.5) * 2  # 0 en el medio, 1 en el borde
    cv.paint(zone, np.clip((dist - 0.78) / 0.22, 0, 1), edge, 0.85)
    cv.paint(zone, np.clip((v - 0.93) / 0.07, 0, 1), edge, 0.6)
    if vein:
        cv.paint(zone, np.clip(1 - np.abs(u - 0.5) / 0.035, 0, 1) * (v < 0.9), vein, 0.7)
        if shape == "leaf":
            for k in range(1, 5):
                y = k * 0.18
                side = np.clip(1 - np.abs((v - y) - np.abs(u - 0.5) * 0.7) / 0.03, 0, 1) * (np.abs(u - 0.5) < 0.38)
                cv.paint(zone, side, vein, 0.5)
    # brillo suave al medio
    cv.paint(zone, blob(u, v, 0.5, 0.45, 0.25, 0.4, 0.9), (255, 255, 255), 0.18)


def stem(cv, color, dark, seed):
    cv.wash("STEM", color, dark, var=0.1, seed=seed)
    u, v = cv.uvgrid("STEM")
    for k in range(6):
        cu = (k + 0.3) / 6
        cv.paint("STEM", np.clip(1 - np.abs(u - cu) / 0.025, 0, 1), dark, 0.35)


def foliage(cv, zone, light, dark, seed, blossoms=None, sparkle=False):
    """Copa de hojas tipo nubecitas (como las nubes de la referencia, pero en verde)."""
    cv.wash(zone, light, dark, var=0.14, seed=seed)
    u, v = cv.uvgrid(zone)
    # curvas de nubecita (borde ciruela suave en las panzas)
    for k in range(5):
        cu = (k + 0.5) / 5
        arc = 0.62 + 0.1 * np.cos((wrap(u, cu) - cu) * math.pi * 10)
        cv.paint(zone, np.clip(1 - np.abs(v - arc) / 0.025, 0, 1) * (np.abs(wrap(u, cu) - cu) < 0.1), dark, 0.45)
    spots(cv, zone, light, 18, seed + 1, 0.02, 0.04, 0.1, 0.7, 0.55)
    if blossoms:
        m = spots(cv, zone, blossoms, 16, seed + 2, 0.012, 0.022, 0.1, 0.75, 1.0)
        cv.paint(zone, m * 0.6, (255, 255, 255), 0.4)
    if sparkle:
        spots(cv, zone, "#FFF3B8", 7, seed + 3, 0.006, 0.01, 0.1, 0.6, 1.0)


def under_disc(cv, base, line, seed):
    cv.wash("UNDER", base, base, var=0.06, seed=seed)
    u, v = cv.uvgrid("UNDER")
    du, dv = u - 0.5, v - 0.5
    r = np.sqrt(du ** 2 + dv ** 2) * 2
    a = np.arctan2(dv, du)
    gill = (np.abs(np.sin(a * 18)) < 0.25) * (r > 0.35) * (r < 0.97)
    cv.paint("UNDER", gill.astype(float), line, 0.55)
    cv.paint("UNDER", np.clip((r - 0.92) / 0.08, 0, 1), PLUM, 0.6)


def center(cv, color, dark, seed):
    cv.wash("CENTER", color, dark, var=0.1, seed=seed)
    spots(cv, "CENTER", dark, 22, seed + 4, 0.02, 0.035, 0.15, 0.8, 0.8)
    spots(cv, "CENTER", "#FFF6D0", 8, seed + 5, 0.015, 0.025, 0.1, 0.5, 0.7)


def cap_rim(cv, zone, color, width=0.1, alpha=0.85):
    u, v = cv.uvgrid(zone)
    cv.paint(zone, np.clip((v - (1 - width)) / width, 0, 1), color, alpha)


def glow_from(cv, zones):
    g = Canvas()
    for z in zones:
        x0, y0, x1, y1 = Z[z]
        g.rgb[y0:y1, x0:x1] = cv.rgb[y0:y1, x0:x1]
    return g


def save(cv, name):
    cv.save(os.path.join(ENT, name + ".png"))


# ================================================================ espiritus

# Champi: hongo rojo coral con lunares crema
cv = Canvas()
body(cv, "#FBF2E6", "#EED9C3", 1)
face(cv, "#FBF2E6", "#EED9C3", 1)
cv.wash("CAP", "#EE8A7C", "#D9574F", var=0.12, seed=11)
spots(cv, "CAP", "#FFF6EA", 12, 12, 0.03, 0.06, 0.1, 0.75)
cap_rim(cv, "CAP", "#7A3B45", 0.08)
under_disc(cv, "#F1DCC8", "#C79C8E", 13)
save(cv, "champi")

# Luminon: hongo azul noche con lunares que brillan
cv = Canvas()
body(cv, "#EEE9FB", "#D9D0F2", 2)
face(cv, "#EEE9FB", "#D9D0F2", 2, eye="#3B3A6E", blush="#C7A8F0")
cv.wash("CAP", "#5C88B8", "#2F4F7A", var=0.15, seed=21)
gl = Canvas()
m = spots(cv, "CAP", "#A8F6F5", 14, 22, 0.025, 0.05, 0.1, 0.75)
cap_rim(cv, "CAP", "#232E52", 0.1)
under_disc(cv, "#D3EEF6", "#7CCAD8", 23)
u, v = cv.uvgrid("CAP")
x0, y0, x1, y1 = Z["CAP"]
gl.rgb[y0:y1, x0:x1] = np.array([150, 250, 248])[None, None, :] * m[..., None]
gl.a[y0:y1, x0:x1] = m * 255
bu, bv = cv.uvgrid("BODY")
for s in (-1, 1):
    em = dot(bu, bv, 0.5 + s * EYE_U, EYE_V, 0.016, 0.042)
    cv.paint("BODY", em, "#4FD8F0")
    x0, y0, x1, y1 = Z["BODY"]
    gl.rgb[y0:y1, x0:x1] = np.maximum(gl.rgb[y0:y1, x0:x1], np.array([170, 245, 255])[None, None, :] * em[..., None])
    gl.a[y0:y1, x0:x1] = np.maximum(gl.a[y0:y1, x0:x1], em * 255)
save(cv, "luminon")
save(gl, "luminon_brillo")

# Margarita: cuerpito salvia con corona de petalos blancos
cv = Canvas()
body(cv, "#E2EFCF", "#C9DFB0", 3)
face(cv, "#E2EFCF", "#C9DFB0", 3)
petal_zone(cv, "PETAL", "#EFE6F3", "#FFFEFA", "#9C7A92", 31)
center(cv, "#F7D35A", "#D9952B", 32)
save(cv, "margarita")

# Brotecito: arbustito verde con hojitas
cv = Canvas()
body(cv, "#EEF6DD", "#D8E9C1", 4)
face(cv, "#EEF6DD", "#D8E9C1", 4)
foliage(cv, "CAP", "#A8D392", "#5E9257", 41)
stem(cv, "#8DAA62", "#5F7A40", 42)
petal_zone(cv, "LEAF", "#7DB46A", "#A6D38C", "#4D7A46", 43, vein="#4D7A46", shape="leaf")
save(cv, "brotecito")

# Campanita: flor azul lavanda tipo gorrito
cv = Canvas()
body(cv, "#EAF1FB", "#D5E2F4", 5)
face(cv, "#EAF1FB", "#D5E2F4", 5, blush="#F2A9C0")
cv.wash("CAP", "#B3C3F5", "#7D92E3", var=0.12, seed=51)
u, v = cv.uvgrid("CAP")
for k in range(5):  # nervaduras de la campana
    cu = (k + 0.5) / 5
    cv.paint("CAP", np.clip(1 - np.abs(wrap(u, cu) - cu) / 0.012, 0, 1) * (v > 0.12) * (v < 0.9), "#6A7BD0", 0.45)
cap_rim(cv, "CAP", "#4E5AA8", 0.07)
stem(cv, "#7DAE66", "#557E45", 52)
save(cv, "campanita")

# Rosita espinosa: capullo con espiral y petalos
cv = Canvas()
body(cv, "#FBE6E8", "#F2CDD3", 6)
face(cv, "#FBE6E8", "#F2CDD3", 6)
cv.wash("CAP", "#E86A78", "#C23B52", var=0.1, seed=61)
u, v = cv.uvgrid("CAP")
spiral = np.abs(((u * 2 + v * 1.6) % 1) - 0.5) < 0.07
cv.paint("CAP", spiral * (v < 0.7), "#9C2C42", 0.55)
petal_zone(cv, "PETAL", "#E15A6C", "#F28C99", "#A12E45", 62)
petal_zone(cv, "LEAF", "#6FA36B", "#94C489", "#40703F", 63, vein="#40703F", shape="leaf")
save(cv, "rosita")

# Loto lunar: petalos rosados que brillan en las puntas
cv = Canvas()
body(cv, "#FFF6F9", "#F6E2EB", 7)
face(cv, "#FFF6F9", "#F6E2EB", 7, eye="#5B3550")
petal_zone(cv, "PETAL", "#F3A4C2", "#FFF9FC", "#C2779A", 71)
center(cv, "#FBE08A", "#E7B54D", 72)
gl = Canvas()
pu, pv = cv.uvgrid("PETAL")
gm = np.clip((pv - 0.55) / 0.45, 0, 1) * np.clip(1 - np.abs(pu - 0.5) * 2.2, 0, 1)
x0, y0, x1, y1 = Z["PETAL"]
gl.rgb[y0:y1, x0:x1] = np.array([255, 225, 240])[None, None, :]
gl.a[y0:y1, x0:x1] = gm * 200
x0, y0, x1, y1 = Z["CENTER"]
gl.rgb[y0:y1, x0:x1] = np.array([255, 236, 160])[None, None, :]
gl.a[y0:y1, x0:x1] = 170
save(cv, "loto")
save(gl, "loto_brillo")

# Bonsai ancestral: tronquito y copa de nubecitas verdes con flores de cerezo
cv = Canvas()
body(cv, "#F6EDD6", "#E8D7B3", 8)
face(cv, "#F6EDD6", "#E8D7B3", 8)
foliage(cv, "CAP", "#9FCB8E", "#5C8F5A", 81, blossoms="#F7B9D0", sparkle=True)
foliage(cv, "EXTRA", "#A9D296", "#628F5B", 82, blossoms="#F7B9D0")
stem(cv, "#8A5E3E", "#5C3A27", 83)
save(cv, "bonsai")

# ================================================================ plantilla para admins
cv = Canvas()
cols = {"BODY": "#F3C6C6", "LIMB": "#E8A9A9", "LID": "#D88F8F", "STEM": "#A7C59B", "PETAL": "#F6E3A1", "LEAF": "#9FD39B",
        "CAP": "#A9C8F2", "UNDER": "#C8B6E8", "CENTER": "#F2D27A", "EXTRA": "#B9E3D2"}
for i, (z, c) in enumerate(cols.items()):
    cv.wash(z, c, c, var=0.05, seed=90 + i)
    u, v = cv.uvgrid(z)
    x0, y0, x1, y1 = Z[z]
    border = ((u * (x1 - x0) < 1.5) | (u * (x1 - x0) > (x1 - x0) - 1.5) | (v * (y1 - y0) < 1.5) | (v * (y1 - y0) > (y1 - y0) - 1.5))
    cv.paint(z, border.astype(float), PLUM, 1.0)
face(cv, "#F3C6C6", "#F3C6C6", 99)
cv.save(os.path.join(RES, "espiritus", "ejemplo_textura.png"))
print("listo")
