import numpy as np
from PIL import Image, ImageDraw
from tk import *
from t_wings import flame_cell

def glow():
    w = h = 64; X, Y = grid(w, h); r = np.sqrt((X - .5) ** 2 + (Y - .5) ** 2) * 2
    a = np.clip(np.exp(-(r / 0.42) ** 2) * (1 - smoothstep(0.8, 1.0, r)), 0, 1)
    save('glow', np.ones((h, w, 3)) * 255, a)
    w = h = 32; X, Y = grid(w, h); r = np.sqrt((X - .5) ** 2 + (Y - .5) ** 2) * 2
    a = np.clip(np.exp(-(r / 0.25) ** 2) * 1.0 + 0.35 * np.exp(-(r / 0.6) ** 2), 0, 1) * (1 - smoothstep(0.85, 1.0, r))
    save('spark', np.ones((h, w, 3)) * 255, a)
    w = h = 64; X, Y = grid(w, h); x = X * 2 - 1; y = Y * 2 - 1; r = np.sqrt(x * x + y * y)
    spikes = np.exp(-9 * np.sqrt(np.abs(x) * np.abs(y) + 1e-6)) * np.clip(1 - r, 0, 1) ** 0.8
    core = np.exp(-(r / 0.16) ** 2)
    save('star', np.ones((h, w, 3)) * 255, np.clip(spikes * 1.4 + core * 0.9 + 0.12 * np.exp(-(r / 0.6) ** 2), 0, 1))

def orb():
    w = h = 64; X, Y = grid(w, h); x = X * 2 - 1; y = Y * 2 - 1; r2 = x * x + y * y
    z = np.sqrt(np.clip(1 - r2, 0, 1))
    n = np.stack([x, -y, z], -1); L = np.array([-0.4, 0.55, 0.75]); L /= np.linalg.norm(L)
    dif = np.clip(n @ L, 0, 1); H = L + np.array([0, 0, 1.0]); H /= np.linalg.norm(H)
    spec = np.clip(n @ H, 0, 1) ** 40
    fres = (1 - z) ** 2
    col = grad(0.25 + 0.6 * dif + 0.4 * fres, [(0, (20, 70, 140)), (0.5, (90, 200, 255)), (1, (230, 250, 255))]) + spec[..., None] * 255
    a = smoothstep(1.0, 0.93, np.sqrt(r2))
    save('orb', col, a)

def flames():
    rgb = np.zeros((64, 256, 3)); a = np.zeros((64, 256))
    for k in range(4):
        c, al, g, core = flame_cell(64, 64, 90 + k * 7, True)
        # llama completa ancha: la punta arriba
        rgb[:, k * 64:(k + 1) * 64] = grad(core, [(0, (255, 120, 30)), (0.5, (255, 190, 70)), (1, (255, 250, 200))])
        a[:, k * 64:(k + 1) * 64] = np.clip(g * 1.1, 0, 1)
    save('flame_sheet', rgb, a)
    rgb = np.zeros((64, 256, 3)); a = np.zeros((64, 256))
    for k in range(4):
        X, Y = grid(64, 64); r = np.sqrt((X - .5) ** 2 + (Y - .5) ** 2) * 2
        n = fbm(64, 64, 10, 120 + k * 5, 4)
        al = np.clip((1 - r) * 1.6 * (0.5 + n) - 0.1, 0, 1) ** 1.2
        shade = 0.55 + 0.45 * n
        rgb[:, k * 64:(k + 1) * 64] = np.stack([shade * 170] * 3, -1) + 30
        a[:, k * 64:(k + 1) * 64] = al * 0.85
    save('smoke_sheet', rgb, a)

def streaks():
    w = h = 64; X, Y = grid(w, h); n = fbm(w, h, 8, 6, 3)
    a = np.exp(-((X - 0.5) / 0.22) ** 2) * (0.35 + 0.65 * n) * smoothstep(0, 0.2, Y) * smoothstep(1, 0.8, Y)
    save('wisp', np.ones((h, w, 3)) * np.array([90, 70, 120.]), np.clip(a, 0, 1))
    w, h = 32, 64; X, Y = grid(w, h)
    a = np.exp(-((X - 0.5) / 0.2) ** 2) * (0.5 + 0.5 * (1 - Y))
    save('trail', np.ones((h, w, 3)) * 255, np.clip(a, 0, 1))
    w, h = 32, 64; X, Y = grid(w, h)
    core = np.exp(-((X - 0.5) / 0.06) ** 2); halo = 0.45 * np.exp(-((X - 0.5) / 0.3) ** 2)
    save('bolt', np.stack([200 + 55 * core, 235 + 20 * core, np.full((h, w), 255.)], -1), np.clip(core + halo, 0, 1))

def runes_draw(draw, cx, cy, r0, r1, n, rnd, ss):
    for i in range(n):
        a = 2 * np.pi * i / n
        rc = (r0 + r1) / 2; gx = cx + np.cos(a) * rc; gy = cy + np.sin(a) * rc; s = (r1 - r0) * 0.38
        ca, sa = np.cos(a), np.sin(a)
        for _ in range(rnd.integers(2, 4)):
            x0, y0 = rnd.uniform(-1, 1, 2) * s; x1, y1 = rnd.uniform(-1, 1, 2) * s
            p0 = (gx + x0 * ca - y0 * sa, gy + x0 * sa + y0 * ca); p1 = (gx + x1 * ca - y1 * sa, gy + x1 * sa + y1 * ca)
            draw.line([p0, p1], fill=255, width=int(2 * ss))
        if rnd.random() < 0.5:
            draw.ellipse([gx - s * 0.25, gy - s * 0.25, gx + s * 0.25, gy + s * 0.25], outline=255, width=int(1.5 * ss))

def rings():
    S = 256; ss = 4; rnd = np.random.default_rng(5)
    im = Image.new('L', (S * ss, S * ss), 0); d = ImageDraw.Draw(im); c = S * ss / 2
    def circ(r, w): d.ellipse([c - r * ss, c - r * ss, c + r * ss, c + r * ss], outline=255, width=int(w * ss))
    circ(124, 3); circ(116, 1.5); circ(84, 2.5); circ(78, 1.2); circ(46, 2); circ(40, 1)
    for i in range(72):
        a = 2 * np.pi * i / 72; r0, r1 = (116, 122) if i % 3 else (112, 124)
        d.line([(c + np.cos(a) * r0 * ss, c + np.sin(a) * r0 * ss), (c + np.cos(a) * r1 * ss, c + np.sin(a) * r1 * ss)], fill=255, width=int(1.5 * ss))
    runes_draw(d, c, c, 86 * ss, 114 * ss, 28, rnd, ss)
    runes_draw(d, c, c, 48 * ss, 76 * ss, 16, rnd, ss)
    for k in range(3):   # triangulo interior
        a0 = 2 * np.pi * k / 3 - np.pi / 2; a1 = a0 + 2 * np.pi / 3
        d.line([(c + np.cos(a0) * 38 * ss, c + np.sin(a0) * 38 * ss), (c + np.cos(a1) * 38 * ss, c + np.sin(a1) * 38 * ss)], fill=255, width=int(2 * ss))
    a = np.asarray(im.resize((S, S), Image.LANCZOS)) / 255.0
    X, Y = grid(S, S); r = np.sqrt((X - .5) ** 2 + (Y - .5) ** 2) * 2
    a = np.clip(a + 0.10 * np.exp(-((r - 0.65) / 0.3) ** 2), 0, 1)
    save('rune_ring', np.ones((S, S, 3)) * np.array([200, 160, 255.]), a)
    im = Image.new('L', (S * ss, S * ss), 0); d = ImageDraw.Draw(im)
    circ(124, 3); circ(110, 1.5); circ(70, 1.5)
    for k in range(6):  # hexagrama
        pass
    for tri in range(2):
        pts = [(c + np.cos(2 * np.pi * k / 3 + tri * np.pi - np.pi / 2) * 108 * ss, c + np.sin(2 * np.pi * k / 3 + tri * np.pi - np.pi / 2) * 108 * ss) for k in range(3)]
        d.polygon(pts, outline=255, width=int(3 * ss))
    runes_draw(d, c, c, 112 * ss, 122 * ss, 36, rnd, ss)
    for k in range(6):
        a0 = 2 * np.pi * k / 6
        d.ellipse([c + np.cos(a0) * 86 * ss - 7 * ss, c + np.sin(a0) * 86 * ss - 7 * ss, c + np.cos(a0) * 86 * ss + 7 * ss, c + np.sin(a0) * 86 * ss + 7 * ss], outline=255, width=int(2 * ss))
    d.ellipse([c - 18 * ss, c - 18 * ss, c + 18 * ss, c + 18 * ss], outline=255, width=int(3 * ss))
    a = np.asarray(im.resize((S, S), Image.LANCZOS)) / 255.0
    save('rune_ring2', np.ones((S, S, 3)) * np.array([215, 185, 255.]), np.clip(a + 0.08 * np.exp(-((r - 0.5) / 0.35) ** 2), 0, 1))

def eye():
    w, h = 128, 64
    X, Y = grid(w, h)
    x = (X - 0.5) * 2; y = (Y - 0.5) * 2
    # el frente de la esfera ocupa el centro; ancho = 2 * alto
    rr = np.sqrt((x * 0.5) ** 2 + y ** 2)
    n = fbm(w, h, 6, 12, 3)
    sclera = np.array([238, 226, 222.]) * (0.9 + 0.12 * n)[..., None]
    veins = np.zeros((h, w))
    for k in range(16):
        a = k * 0.39 + 0.1
        da = np.abs(((np.arctan2(y, x * 0.5) - a + np.pi) % (2 * np.pi)) - np.pi)
        veins = np.maximum(veins, np.exp(-(da * rr * 7) ** 2) * smoothstep(0.25, 0.75, rr) * (0.4 + 0.6 * fbm(w, h, 4, k, 2)))
    col = sclera * (1 - veins[..., None] * 0.5) + np.array([200, 30, 30]) * veins[..., None] * 0.5
    ir = smoothstep(0.33, 0.30, rr)
    ia = np.arctan2(y, x * 0.5)
    fib = 0.6 + 0.4 * np.sin(ia * 30 + n * 6)
    icol = grad(rr / 0.33, [(0, (255, 140, 30)), (0.5, (230, 40, 20)), (1, (110, 8, 8))]) * fib[..., None]
    col = col * (1 - ir[..., None]) + icol * ir[..., None]
    pupil = smoothstep(1.0, 0.8, np.sqrt((x * 0.5 / 0.055) ** 2 + (y / 0.26) ** 2))
    col = col * (1 - pupil[..., None])
    sp = smoothstep(0.06, 0.03, np.sqrt((x * 0.5 + 0.11) ** 2 + (y + 0.12) ** 2))
    col = col + sp[..., None] * 255
    # sombra en los bordes (esfera)
    col = col * (0.55 + 0.45 * smoothstep(1.0, 0.4, rr))[..., None]
    save('eye', col, np.ones((h, w)))
