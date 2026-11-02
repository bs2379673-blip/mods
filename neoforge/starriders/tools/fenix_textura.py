"""Textura pintada del Fenix de StarRiders (propiedad de Starlevi): 512x512 + capa de brillo.
Zonas iguales a PhoenixModel.java. Uso: python3 fenix_textura.py"""
import math, os
import numpy as np
from PIL import Image, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "src", "main", "resources", "assets", "starriders", "textures", "entity")
os.makedirs(OUT, exist_ok=True)
S = 512
Z = {"BODY": (0, 0, 256, 128), "NECK": (256, 0, 384, 64), "HEAD": (384, 0, 512, 64), "BEAK": (256, 64, 320, 96),
     "CREST": (320, 64, 384, 128), "LEG": (384, 64, 448, 96), "FOOT": (448, 64, 512, 96), "ARM": (384, 96, 512, 128),
     "WING": (0, 128, 128, 256), "COVERT": (128, 128, 256, 256), "TAIL_A": (256, 128, 320, 320), "TAIL_B": (256, 320, 320, 512),
     "TAIL2_A": (320, 128, 384, 320), "TAIL2_B": (320, 320, 384, 512)}
EYE_U, EYE_V = 0.17, 0.42
rgb = np.zeros((S, S, 3))
alpha = np.zeros((S, S))
grgb = np.zeros((S, S, 3))
galpha = np.zeros((S, S))


def C(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], float)


def grid(z):
    x0, y0, x1, y1 = Z[z]
    ys, xs = np.mgrid[y0:y1, x0:x1]
    return (xs - x0 + 0.5) / (x1 - x0), (ys - y0 + 0.5) / (y1 - y0)


def noise(w, h, seed, blur=4):
    r = np.random.RandomState(seed).rand(h, w) * 255
    b = np.asarray(Image.fromarray(r.astype("uint8")).filter(ImageFilter.GaussianBlur(blur)), float)
    return (b - b.mean()) / (b.std() + 1e-6)


def grad(z, stops, along="v"):
    """Degrade por v (o u) con paradas [(t, color)]."""
    u, v = grid(z)
    t = v if along == "v" else u
    out = np.zeros(t.shape + (3,))
    for i in range(len(stops) - 1):
        t0, c0 = stops[i]
        t1, c1 = stops[i + 1]
        m = (t >= t0) & (t <= t1)
        k = ((t - t0) / max(1e-6, t1 - t0))[..., None]
        out[m] = (C(c0) * (1 - k) + C(c1) * k)[m]
    out[t < stops[0][0]] = C(stops[0][1])
    out[t > stops[-1][0]] = C(stops[-1][1])
    return out


def put(z, col, a=None, seed=0, var=0.08):
    x0, y0, x1, y1 = Z[z]
    n = noise(x1 - x0, y1 - y0, seed)[..., None]
    rgb[y0:y1, x0:x1] = np.clip(col * (1 + n * var), 0, 255)
    alpha[y0:y1, x0:x1] = 255 if a is None else np.clip(a, 0, 1) * 255


def mix(z, mask, color, k=1.0):
    x0, y0, x1, y1 = Z[z]
    m = np.clip(mask, 0, 1)[..., None] * k
    c = C(color) if isinstance(color, str) else np.array(color, float)
    rgb[y0:y1, x0:x1] = rgb[y0:y1, x0:x1] * (1 - m) + c * m


def glow(z, mask, color, k=1.0):
    x0, y0, x1, y1 = Z[z]
    m = np.clip(mask, 0, 1) * k
    c = C(color)
    grgb[y0:y1, x0:x1] = np.maximum(grgb[y0:y1, x0:x1], c[None, None, :] * m[..., None])
    galpha[y0:y1, x0:x1] = np.maximum(galpha[y0:y1, x0:x1], m * 255)


def scales(z, rows, cols, edge, light, k=0.6, seed=0):
    """Plumitas superpuestas (escamas de pluma): borde oscuro abajo y brillo arriba de cada una."""
    u, v = grid(z)
    fv = v * rows
    row = np.floor(fv)
    fu = u * cols + (row % 2) * 0.5
    cu = fu - np.floor(fu) - 0.5
    cv = fv - row
    d = np.sqrt(cu ** 2 * 1.4 + (cv - 0.15) ** 2)
    rim = np.clip(1 - np.abs(d - 0.5) / 0.08, 0, 1) * (cv > 0.2)
    mix(z, rim, edge, k)
    mix(z, np.clip(1 - d / 0.35, 0, 1) * (cv < 0.6), light, k * 0.35)


def feather(z, stops, rachis, barbs, edge, seed, glow_tip=None, eye=None):
    """Pluma: u = ancho, v = base (0) a punta (1). Raquis al medio, barbas en diagonal, borde."""
    col = grad(z, stops)
    u, v = grid(z)
    put(z, col, None, seed, 0.06)
    du = np.abs(u - 0.5)
    mix(z, np.clip(1 - du / 0.03, 0, 1) * (v < 0.95), rachis, 0.85)
    lines = np.abs(((v * 26 - du * 9) % 1) - 0.5) < 0.12
    mix(z, lines * (du > 0.05) * (du < 0.47), barbs, 0.35)
    mix(z, np.clip((du - 0.38) / 0.12, 0, 1), edge, 0.6)
    if eye is not None:
        ev, rings = eye
        r = np.sqrt(((u - 0.5) / 0.42) ** 2 + ((v - ev) / 0.09) ** 2)
        for rr, c in rings:
            mix(z, (r < rr).astype(float), c, 1.0)
        mix(z, np.clip(1 - np.sqrt(((u - 0.44) / 0.08) ** 2 + ((v - ev + 0.02) / 0.02) ** 2), 0, 1), "#FFFFFF", 0.7)
        glow(z, (r < rings[0][0]) * 1.0, "#7FE6FF", 0.75)
    if glow_tip:
        glow(z, np.clip((v - glow_tip) / (1 - glow_tip), 0, 1) * np.clip(1 - du / 0.5, 0, 1), "#FFB347", 0.85)


# ---------------------------------------------------------------- cuerpo, cuello, cabeza
put("BODY", grad("BODY", [(0, "#0F6B47"), (0.35, "#169C6B"), (0.62, "#1FB5A5"), (0.82, "#E8B04A"), (1, "#F2C766")]), seed=1)
scales("BODY", 14, 22, "#0B3F33", "#7FF0C8", 0.55)
u, v = grid("BODY")
mix("BODY", np.clip((v - 0.7) / 0.3, 0, 1), "#F4C35A", 0.35)
put("NECK", grad("NECK", [(0, "#14986A"), (0.6, "#1CB3A0"), (1, "#E7B24B")]), seed=2)
scales("NECK", 10, 14, "#0B3F33", "#8DF2D2", 0.5)
put("ARM", grad("ARM", [(0, "#127A55"), (1, "#1DA88E")]), seed=3)
scales("ARM", 6, 20, "#0B3F33", "#7FF0C8", 0.5)
put("HEAD", grad("HEAD", [(0, "#0F7A52"), (0.5, "#18A57A"), (1, "#E9B54E")]), seed=4)
u, v = grid("HEAD")
mix("HEAD", np.clip(1 - np.abs(u - 0.5) / 0.1, 0, 1) * (v > 0.35), "#F2C14E", 0.8)  # cara dorada hacia el pico
for s in (-1, 1):
    cu = 0.5 + s * EYE_U
    r = np.sqrt(((u - cu) / 0.034) ** 2 + ((v - EYE_V) / 0.09) ** 2)
    mix("HEAD", (r < 1.45).astype(float), "#14110F", 1)       # delineado
    mix("HEAD", (r < 1.05).astype(float), "#FFB627", 1)       # iris ambar
    mix("HEAD", (r < 0.5).astype(float), "#2A0F05", 1)        # pupila
    mix("HEAD", (np.sqrt(((u - cu + 0.012) / 0.012) ** 2 + ((v - EYE_V + 0.04) / 0.035) ** 2) < 1).astype(float), "#FFFFFF", 1)
    # pluma roja tras el ojo
    tr = np.clip(1 - np.abs((v - EYE_V) - (u - cu) * s * 0.9) / 0.05, 0, 1) * (s * (u - cu) > 0.02) * (s * (u - cu) < 0.16)
    mix("HEAD", tr, "#E2452B", 0.9)
    glow("HEAD", (r < 1.05).astype(float), "#FFC75A", 0.8)
put("BEAK", grad("BEAK", [(0, "#C9661E"), (0.5, "#F2B035"), (1, "#FFE08A")]), seed=5)
put("LEG", grad("LEG", [(0, "#B5762A"), (1, "#7A4318")]), seed=6)
scales("LEG", 8, 6, "#4A240C", "#F2C36B", 0.6)
put("FOOT", grad("FOOT", [(0, "#8F521D"), (1, "#2B1607")]), seed=7)
# ---------------------------------------------------------------- plumas
feather("CREST", [(0, "#1AA38A"), (0.45, "#F2C14E"), (0.8, "#F0632C"), (1, "#FFD36B")], "#FFE9A8", "#B8401E", "#FF8A3D", 8, glow_tip=0.6)
feather("WING", [(0, "#137A57"), (0.35, "#1EB39C"), (0.6, "#E9B44C"), (0.85, "#EE5A2A"), (1, "#FFB347")], "#FFE6A0", "#0B5A44", "#FF7B2E", 9, glow_tip=0.72)
feather("COVERT", [(0, "#0F6B47"), (0.6, "#18A57A"), (1, "#E9C25A")], "#BDF7DF", "#0B3F33", "#F2C14E", 10)
feather("TAIL_A", [(0, "#1AA38A"), (0.3, "#E9B44C"), (0.7, "#E24A26"), (1, "#C42D1C")], "#FFD27A", "#7A1A10", "#FF8A3D", 11)
feather("TAIL_B", [(0, "#C42D1C"), (0.5, "#E8582A"), (0.9, "#FF9A3C"), (1, "#FFD36B")], "#FFD27A", "#7A1A10", "#FFB347", 12,
        glow_tip=0.85, eye=(0.72, [(1.0, "#F2C14E"), (0.78, "#13A37A"), (0.55, "#1D6FD6"), (0.32, "#0B1E4A")]))
feather("TAIL2_A", [(0, "#16936D"), (0.4, "#1FB5A5"), (0.8, "#E9B44C"), (1, "#F07A2E")], "#FFE6A0", "#0B4A3A", "#F2C14E", 13)
feather("TAIL2_B", [(0, "#F07A2E"), (0.6, "#E9B44C"), (1, "#FFE08A")], "#FFE6A0", "#7A3A10", "#FFD36B", 14,
        glow_tip=0.85, eye=(0.7, [(1.0, "#FFD36B"), (0.78, "#E24A26"), (0.55, "#13A37A"), (0.32, "#0B2A3A")]))
# ---------------------------------------------------------------- guardar
img = np.dstack([rgb, alpha]).clip(0, 255).astype("uint8")
Image.fromarray(img, "RGBA").save(os.path.join(OUT, "fenix.png"))
g = np.dstack([grgb, galpha]).clip(0, 255).astype("uint8")
Image.fromarray(g, "RGBA").save(os.path.join(OUT, "fenix_brillo.png"))
print("listo")
