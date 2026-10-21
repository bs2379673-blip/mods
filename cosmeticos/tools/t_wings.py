import numpy as np
from tk import *

def feather_cell(w, h, seed, pal, kind='long'):
    """plumas: devuelve rgb (h,w,3), alpha, glow alpha. Y=0 punta, Y=1 base"""
    X, Y = grid(w, h)
    X = X * 2 - 1
    r = rng(seed)
    wob = (fbm(w, h, 14, seed, 3) - 0.5) * 0.10
    if kind == 'long':
        hw = np.where(Y > 0.55, 0.26 + 0.74 * smoothstep(1.0, 0.55, Y) , np.sin(np.clip(Y / 0.55, 0, 1) * np.pi / 2) ** 0.62)
        hw = hw * (1 + wob * 0.6)
        curve = 0.05 * np.sin(Y * 3.2 + seed)         # el raquis se curva un poco
    else:
        hw = np.sqrt(np.clip(1 - ((Y - 0.55) / 0.5) ** 2, 0, 1)) * (1 + wob)
        hw = np.where(Y > 0.9, hw * 0.55 + 0.1, hw)
        curve = 0.0
    Xc = X - curve
    u = Xc / np.maximum(hw, 1e-3)
    inside = np.clip((1 - np.abs(u)) * hw * w * 0.5, 0, 1)      # borde suave de ~1px
    # barbas
    N = 46 if kind == 'long' else 22
    s = Y * N + np.abs(u) * (0.9 if kind == 'long' else 0.5) * N * 0.35 + fbm(w, h, 5, seed + 3, 2) * 1.5
    stripe = 0.5 + 0.5 * np.sin(s * 6.2832)
    fiber = fbm(w, h, 2.2, seed + 9, 2)
    # cortes entre barbas cerca del borde
    gaps = smoothstep(0.78, 1.0, np.abs(u)) * smoothstep(0.55, 0.9, fbm(w, h, 3.0, seed + 5, 2) + 0.25 * stripe)
    alpha = inside * (1 - 0.9 * gaps * (Y < 0.85))
    alpha = np.clip(alpha, 0, 1)
    # color
    core = grad(1 - Y, pal['core'])
    edge = grad(1 - Y, pal['edge'])
    t = np.clip(np.abs(u), 0, 1) ** 1.8
    col = core * (1 - t[..., None]) + edge * t[..., None]
    shade = 0.90 + 0.14 * stripe + 0.10 * fiber - 0.05 * u + 0.0
    ao = 1 - 0.20 * np.exp(-(np.abs(Xc) / 0.10) ** 2)                       # sombra junto al raquis
    col = col * (shade * ao)[..., None]
    # raquis
    rach = np.exp(-(np.abs(Xc) / 0.032) ** 2) * (Y < (0.97 if kind == 'long' else 0.85))
    rcol = np.array(pal['rachis'])
    col = col * (1 - rach[..., None] * 0.9) + rcol * rach[..., None] * 0.9
    shadow = np.exp(-((Xc - 0.075) / 0.05) ** 2) * 0.18
    col = col * (1 - shadow[..., None])
    # brillo de seda en un lado
    sheen = np.exp(-((u + 0.35) / 0.3) ** 2) * 0.10 * smoothstep(1.0, 0.3, Y)
    col = col + sheen[..., None] * 255
    # brillo emisivo: borde dorado y raquis
    edged = np.clip(1 - np.abs(u), 0, 1)
    rim = np.exp(-(edged / 0.14) ** 1.4)
    glow = (0.55 * rim * (0.6 + 0.4 * stripe) + 0.35 * rach) * (alpha > 0.3) * smoothstep(0.0, 0.25, Y + 0.05)
    return col, alpha, np.clip(glow, 0, 1) * alpha

def feather_atlas(name, pal, glow_rgb, seed0):
    rgb = np.zeros((256, 256, 3)); a = np.zeros((256, 256)); ga = np.zeros((256, 256))
    for k in range(8):
        c, al, g = feather_cell(32, 192, seed0 + k * 5, pal, 'long')
        rgb[0:192, k * 32:(k + 1) * 32] = c; a[0:192, k * 32:(k + 1) * 32] = al; ga[0:192, k * 32:(k + 1) * 32] = g
    for k in range(4):
        c, al, g = feather_cell(64, 64, seed0 + 100 + k * 7, pal, 'short')
        rgb[192:256, k * 64:(k + 1) * 64] = c; a[192:256, k * 64:(k + 1) * 64] = al; ga[192:256, k * 64:(k + 1) * 64] = g
    save(name, rgb, a)
    save(name + '_g', np.ones((256, 256, 3)) * np.array(glow_rgb), ga)

def flame_cell(w, h, seed, hot):
    X, Y = grid(w, h); X = X * 2 - 1
    wave = np.sin(Y * 9 + seed) * 0.14 * (1 - Y) + np.sin(Y * 17 + seed * 2) * 0.05 * (1 - Y)
    hw = np.sin(np.clip(Y / 0.7, 0, 1) * np.pi / 2) ** 0.8 * np.where(Y > 0.7, 1 - 0.55 * (Y - 0.7) / 0.3, 1)
    u = (X - wave) / np.maximum(hw, 1e-3)
    n = fbm(w, h, 6, seed, 3)
    body = np.clip(1 - np.abs(u) ** 1.7 - 0.25 * (n - 0.5) * 2 * (1 - Y), 0, 1)
    alpha = smoothstep(0.0, 0.35, body)
    core = smoothstep(0.35, 0.95, body)
    rgb_s = grad(1 - core * (0.7 + 0.3 * (1 - Y)), [(0, (40, 8, 4)), (0.35, (200, 40, 6)), (0.7, (255, 140, 20)), (1, (255, 220, 90))])
    glow_a = alpha * (0.5 + 0.5 * core)
    return rgb_s, alpha * (0.35 + 0.65 * smoothstep(0, 1, 1 - Y * 0.3)), glow_a, core

def flame_atlas():
    rgb = np.zeros((256, 256, 3)); a = np.zeros((256, 256)); ga = np.zeros((256, 256)); grgb = np.zeros((256, 256, 3))
    for k in range(8):
        c, al, g, core = flame_cell(32, 192, 40 + k * 3, True)
        rgb[0:192, k * 32:(k + 1) * 32] = c; a[0:192, k * 32:(k + 1) * 32] = al; ga[0:192, k * 32:(k + 1) * 32] = g
        grgb[0:192, k * 32:(k + 1) * 32] = grad(core, [(0, (255, 90, 10)), (0.6, (255, 170, 40)), (1, (255, 245, 170))])
    save('flame_wing', rgb, a)
    save('flame_wing_g', grgb, ga)

def crystal():
    w = h = 128
    X, Y = grid(w, h)
    rgb = np.zeros((h, w, 3)); a = np.zeros((h, w)); g = np.zeros((h, w))
    n = fbm(w, h, 9, 5, 4)
    face = np.floor(X * 4).astype(int)
    fx = X * 4 - face
    bright = np.array([0.72, 1.0, 0.82, 0.58])[face]
    diag = 0.5 + 0.5 * np.sin((fx * 2.2 + Y * 1.7 + face) * 6.28)
    base = grad(1 - Y, [(0, (20, 120, 150)), (0.4, (70, 190, 220)), (0.8, (170, 240, 255)), (1, (255, 255, 255))])
    col = base * (bright * (0.85 + 0.25 * diag) * (0.92 + 0.16 * n))[..., None]
    veins = np.exp(-((fx - 0.5) / 0.04) ** 2) * 0.25 + np.exp(-(((fx * 1.0 + Y * 0.6) % 1 - 0.5) / 0.03) ** 2) * 0.18
    col = col + veins[..., None] * 200
    rgb[:] = col
    a[:] = 0.78 + 0.2 * smoothstep(0.3, 1, 1 - Y)
    edge = np.maximum(np.exp(-(np.minimum(fx, 1 - fx) / 0.05) ** 2), 0)
    g[:] = np.clip(edge * 0.8 + veins * 1.2 + 0.25 * smoothstep(0.55, 1.0, 1 - Y), 0, 1)
    save('crystal', rgb, a)
    save('crystal_g', np.ones((h, w, 3)) * np.array([120, 235, 255.]), g)

LUZ = dict(core=[(0, (250, 236, 205)), (0.45, (255, 253, 245)), (1, (255, 255, 255))],
           edge=[(0, (255, 215, 140)), (0.5, (255, 230, 170)), (1, (255, 246, 215))], rachis=(255, 244, 205))

def membrane(name, base_stops, vein_rgb, glow_rgb, seed, mort=False):
    w = h = 128
    X, Y = grid(w, h)          # Y=1 -> muneca (abajo), Y=0 -> borde del ala
    r = 1 - Y
    n = fbm(w, h, 12, seed, 4)
    vein = np.zeros((h, w))
    # venas desde el centro-abajo hacia el borde con ramas
    for k, x0 in enumerate([0.5, 0.28, 0.72, 0.12, 0.88]):
        sway = (fbm(w, h, 20, seed + k * 3, 2) - 0.5) * 0.12
        xc = 0.5 + (x0 - 0.5) * (0.15 + 1.25 * (1 - Y)) + sway * (1 - Y)
        d = np.abs(X - xc)
        wdt = 0.022 if k == 0 else 0.014
        vein = np.maximum(vein, np.exp(-(d / wdt) ** 2) * smoothstep(1.0, 0.85, Y + 0.0) * (0.9 if k < 3 else 0.7))
    cross = np.exp(-(((Y * 7 + n * 1.6) % 1 - 0.5) / 0.08) ** 2) * 0.22 * smoothstep(0.1, 0.5, 1 - Y)
    mem = grad(0.35 + 0.35 * n + 0.3 * (1 - Y), base_stops)
    # transparencia: mas fina entre venas
    lightness = 0.85 + 0.25 * fbm(w, h, 5, seed + 7, 3)
    col = mem * lightness[..., None]
    col = col * (1 - 0.7 * vein[..., None]) + np.array(vein_rgb) * vein[..., None] * 0.7
    col = col * (1 - 0.3 * cross[..., None])
    # borde desgarrado
    edge_noise = fbm(w, h, 6, seed + 11, 3)
    alpha = np.clip(0.93 - smoothstep(0.88, 1.0, 1 - Y + (edge_noise - 0.5) * 0.35) * 1.5 - 0.0, 0, 1)
    alpha = np.where(1 - Y < 0.86, np.maximum(alpha, 0.78 + 0.15 * (1 - vein)), alpha)
    # lateral: los bordes entre dedos son opacos
    gl = np.clip(vein * 1.1 + cross * 0.7, 0, 1) * smoothstep(0.0, 0.3, 1 - Y + 0.1)
    if mort:
        spots = smoothstep(0.62, 0.7, fbm(w, h, 7, seed + 21, 2)) * smoothstep(0.1, 0.6, 1 - Y)
        gl = np.clip(gl + spots * 0.9, 0, 1)
        col = col + spots[..., None] * np.array([120, 40, 150]) * 0.5
    save(name, col, alpha)
    save(name + '_g', np.ones((h, w, 3)) * np.array(glow_rgb), gl * alpha)

def bone(name, stops, seed, ridge):
    w = h = 64
    X, Y = grid(w, h)
    n = fbm(w, h, 6, seed, 3, tile=True)
    bands = 0.5 + 0.5 * np.sin(Y * 6.2832 * ridge)
    around = 0.55 + 0.45 * np.cos((X - 0.3) * 6.2832)         # luz de un lado, sombra del otro
    col = grad(0.25 + 0.5 * around + 0.12 * n - 0.1 * bands, stops)
    save(name, col, np.ones((h, w)))
