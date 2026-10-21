"""herramientas de pintado: ruido, degradados, mascaras suaves"""
import numpy as np
from PIL import Image, ImageFilter, ImageDraw
OUT = '/home/claude/mods/cosmeticos/src/main/resources/assets/cosmeticos/textures/cos/'
import os
os.makedirs(OUT, exist_ok=True)

def rng(seed): return np.random.default_rng(seed)

def value_noise(w, h, cell, seed, tile=False):
    """ruido de valor suave; cell = tamano de celda en pixeles"""
    r = rng(seed)
    gw, gh = int(np.ceil(w / cell)) + 2, int(np.ceil(h / cell)) + 2
    g = r.random((gh, gw))
    if tile:
        gw = int(round(w / cell)); gh = int(round(h / cell)); g = r.random((gh, gw)); g = np.pad(g, ((0, 2), (0, 2)), mode='wrap')
    xs = np.arange(w) / cell; ys = np.arange(h) / cell
    x0 = np.floor(xs).astype(int); y0 = np.floor(ys).astype(int)
    fx = xs - x0; fy = ys - y0
    fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy)
    a = g[np.ix_(y0, x0)]; b = g[np.ix_(y0, x0 + 1)]; c = g[np.ix_(y0 + 1, x0)]; d = g[np.ix_(y0 + 1, x0 + 1)]
    top = a + (b - a) * fx[None, :]; bot = c + (d - c) * fx[None, :]
    return top + (bot - top) * fy[:, None]

def fbm(w, h, cell, seed, octaves=4, tile=False):
    tot = np.zeros((h, w)); amp = 1.0; s = 0
    for o in range(octaves):
        tot += amp * value_noise(w, h, max(1, cell / (2 ** o)), seed + o * 17, tile); s += amp; amp *= 0.5
    return tot / s

def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0 + 1e-9), 0, 1); return t * t * (3 - 2 * t)

def lerp(a, b, t): return a + (b - a) * t

def grad(t, stops):
    """degradado: stops = [(pos, (r,g,b)), ...] con t array 0..1 -> (...,3)"""
    t = np.clip(t, 0, 1)
    pos = [s[0] for s in stops]; out = np.zeros(t.shape + (3,))
    for ch in range(3):
        out[..., ch] = np.interp(t, pos, [s[1][ch] for s in stops])
    return out

def mk(w, h): return np.zeros((h, w, 4))

def save(name, rgb, a=None):
    """rgb en 0..255 (h,w,3) o (h,w,4)"""
    arr = rgb if a is None else np.dstack([rgb, a * 255 if a.max() <= 1.0001 else a])
    if arr.shape[2] == 3: arr = np.dstack([arr, np.full(arr.shape[:2], 255.0)])
    im = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), 'RGBA')
    im.save(OUT + name + '.png')
    return im

def blur(a, r):
    im = Image.fromarray(np.clip(a * 255, 0, 255).astype(np.uint8)); return np.asarray(im.filter(ImageFilter.GaussianBlur(r))) / 255.0

def over(base_rgb, base_a, top_rgb, top_a):
    a = top_a[..., None]
    rgb = base_rgb * (1 - a) + top_rgb * a
    return rgb, np.maximum(base_a, top_a)

def grid(w, h):
    y, x = np.mgrid[0:h, 0:w]; return (x + 0.5) / w, (y + 0.5) / h
