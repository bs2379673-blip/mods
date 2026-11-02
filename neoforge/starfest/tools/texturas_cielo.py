# Texturas de los cielos de StarFest (se generan una vez). Propiedad de Starlevi.
import numpy as np, sys
from PIL import Image, ImageFilter
OUT = sys.argv[1]
rng = np.random.default_rng(11)

def octave(w, h, cells_x, cells_y, wrap=True):
    g = rng.random((cells_y + 1, cells_x + 1))
    if wrap:
        g[:, -1] = g[:, 0]
    im = Image.fromarray((g * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC)
    return np.asarray(im, np.float32) / 255.0

def fbm(w, h, base=4, oct=7, wrap=True, aspect=2):
    s = np.zeros((h, w), np.float32); a = 0.5; tot = 0
    for i in range(oct):
        c = base * 2 ** i
        s += a * octave(w, h, c * aspect if aspect else c, c, wrap); tot += a; a *= 0.55
    return s / tot

def ridged(n):
    return 1 - np.abs(n * 2 - 1)

def save(arr, name):
    arr = np.clip(arr, 0, 1)
    Image.fromarray((arr * 255).astype(np.uint8)).save(f"{OUT}/{name}.png")

W, H = 2048, 1024
y = np.linspace(-1, 1, H)[:, None]
# --- nebulosa de colores (panorama completo, ruido deformado para que parezca gas)
def warp_fbm(base, oct=7):
    q = fbm(W, H, base, oct)
    qx = (fbm(W, H, base + 1, 4) - 0.5) * 0.25
    qy = (fbm(W, H, base + 2, 4) - 0.5) * 0.25
    ys, xs = np.mgrid[0:H, 0:W].astype(np.float32)
    xi = ((xs + qx * W) % W).astype(int); yi = np.clip(ys + qy * H, 0, H - 1).astype(int)
    return q[yi, xi]
gas = warp_fbm(3)
gas2 = warp_fbm(5)
fil = ridged(warp_fbm(4))
dens = np.clip((gas - 0.33) * 2.2, 0, 1) ** 1.35
wisp = np.clip((fil - 0.78) * 4.5, 0, 1) ** 2
hue = gas2
pal = [np.array(c) for c in ([0.85, 0.12, 0.65], [0.35, 0.15, 0.95], [0.10, 0.55, 1.00], [1.00, 0.45, 0.20])]
t = hue * 3
i0 = np.clip(np.floor(t).astype(int), 0, 2); f = (t - i0)[..., None]
P = np.stack(pal)
base_col = P[i0] * (1 - f) + P[i0 + 1] * f
col = base_col * dens[..., None] * 1.5 + (base_col * 0.6 + 0.4) * wisp[..., None] * 0.3 * dens[..., None]
core = np.clip((gas - 0.66) * 4, 0, 1) ** 2
col += np.array([1.0, 0.85, 0.95]) * core[..., None] * 0.5
dust = np.clip((fbm(W, H, 10, 5) - 0.45) * 2.5, 0, 1)[..., None]
col *= (0.5 + 0.5 * dust)
st = rng.random((H, W))
stars = (st > 0.9975).astype(np.float32) * rng.random((H, W)) * 2 + (st > 0.99985) * 3
stars = np.asarray(Image.fromarray(np.clip(stars * 85, 0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.7)), np.float32) / 255 * 3
fade = np.clip(1.25 - np.abs(y) ** 3 * 0.6, 0, 1)[..., None]
col = np.asarray(Image.fromarray((np.clip(col * fade, 0, 1) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.2)), np.float32) / 255
col += stars[..., None] * fade
save(col, "nebulosa")
# --- nubes en gris (se tinen con la paleta de la cancion)
g = np.clip((fbm(W, H, 3) - 0.38) * 2.2, 0, 1) ** 1.4 * (0.6 + 0.4 * ridged(fbm(W, H, 6)))
save(np.repeat((g * fade[..., 0])[..., None], 3, 2), "nubes")
# --- galaxia espiral (con transparencia)
S = 1024
yy, xx = np.mgrid[-1:1:S * 1j, -1:1:S * 1j]
r = np.sqrt(xx ** 2 + yy ** 2) + 1e-6; th = np.arctan2(yy, xx)
nz = fbm(S, S, 8, 6, False, 1)
spiral = th - 2.6 * np.log(r + 0.02) + (nz - 0.5) * 1.2
arms = (0.5 + 0.5 * np.cos(2 * spiral)) ** 2.2
lanes = np.clip(1 - (0.5 + 0.5 * np.cos(2 * spiral + 0.55)) ** 8 * 0.8, 0, 1)
core = np.exp(-r ** 2 / 0.01) * 1.6 + np.exp(-r ** 2 / 0.05) * 0.5
disk = np.exp(-r / 0.38)
clumps = np.clip((fbm(S, S, 24, 4, False, 1) - 0.55) * 4, 0, 1) * arms * disk
b = disk * (0.18 + 0.95 * arms) * lanes
gal = np.stack([b * 0.75, b * 0.70, b * 1.25], 2) + np.stack([core, core * 0.85, core * 0.6], 2) + np.stack([clumps * 1.2, clumps * 0.5, clumps * 1.5], 2)
st = (rng.random((S, S)) > 0.997) * np.exp(-r / 0.5) * 2
gal += st[..., None]
a = np.clip(np.max(gal, axis=2) * 1.3, 0, 1) * np.clip((1 - r) * 2.5, 0, 1)
save(np.concatenate([np.clip(gal, 0, 1), a[..., None]], 2), "galaxia")
# --- brillo suave, estrella con destello, anillo, haz y aurora
S = 256
yy, xx = np.mgrid[-1:1:S * 1j, -1:1:S * 1j]; r = np.sqrt(xx ** 2 + yy ** 2)
glow = np.exp(-r ** 2 / 0.08)
save(np.stack([glow] * 4, 2), "brillo")
flare = np.exp(-r ** 2 / 0.004) + 0.6 * np.exp(-(xx ** 2) / 0.0006) * np.exp(-np.abs(yy) * 3) + 0.6 * np.exp(-(yy ** 2) / 0.0006) * np.exp(-np.abs(xx) * 3) + 0.25 * np.exp(-r ** 2 / 0.05)
flare = np.clip(flare, 0, 1)
save(np.stack([flare] * 4, 2), "estrella")
ring = np.exp(-((r - 0.78) ** 2) / 0.0035) + 0.25 * np.exp(-((r - 0.78) ** 2) / 0.03)
ring = np.clip(ring, 0, 1)
save(np.stack([ring] * 4, 2), "anillo")
u = np.linspace(-1, 1, 64)[None, :]; v = np.linspace(0, 1, 256)[:, None]
beam = np.exp(-(u ** 2) / 0.08) * (1 - v) ** 1.5
save(np.stack([beam] * 4, 2), "haz")
AW, AH = 1024, 256
u = np.linspace(0, 1, AW)[None, :]; v = np.linspace(0, 1, AH)[:, None]   # v: 0 arriba, 1 abajo
n1 = octave(AW, 1, 160, 1)[0]; n2 = octave(AW, 1, 40, 1)[0]; n3 = octave(AW, 1, 10, 1)[0]
streak = (0.25 + 0.75 * (0.55 * n1 + 0.3 * n2 + 0.15 * n3) ** 2)[None, :]
streak = np.asarray(Image.fromarray((np.repeat(streak, 4, 0) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.5)), np.float32)[:1] / 255
smooth = lambda e0, e1, x: np.clip((x - e0) / (e1 - e0), 0, 1) ** 2 * (3 - 2 * np.clip((x - e0) / (e1 - e0), 0, 1))
body = 1.5 * v ** 1.1 * (1 - smooth(0.80, 1.0, v))
edge = 0.6 * np.exp(-((v - 0.8) ** 2) / 0.004)
au = np.clip(streak * (body + edge), 0, 1)
save(np.stack([au, au, au, au], 2), "aurora")
print("texturas listas")
