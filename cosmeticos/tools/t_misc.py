import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage as ndi
from tk import *

def catmull(pts, n=12, closed=True):
    P = np.array(pts, float); out = []
    m = len(P)
    for i in range(m if closed else m - 1):
        p0, p1, p2, p3 = P[(i - 1) % m], P[i], P[(i + 1) % m], P[(i + 2) % m]
        for t in np.linspace(0, 1, n, endpoint=False):
            out.append(0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t + (-p0 + 3 * p1 - 3 * p2 + p3) * t ** 3))
    return out

def poly_mask(w, h, pts, ss=4):
    im = Image.new('L', (w * ss, h * ss), 0)
    ImageDraw.Draw(im).polygon([(x * w * ss, y * h * ss) for x, y in catmull(pts)], fill=255)
    return np.asarray(im.resize((w, h), Image.LANCZOS)) / 255.0

def edge_dist(mask):
    return ndi.distance_transform_edt(mask > 0.5)

UPPER = [(0.0, 0.92), (0.04, 0.6), (0.16, 0.26), (0.4, 0.05), (0.72, 0.0), (0.93, 0.08), (0.99, 0.26), (0.93, 0.46), (0.78, 0.64), (0.58, 0.8), (0.36, 0.92), (0.16, 0.97)]
LOWER = [(0.0, 0.05), (0.2, 0.03), (0.5, 0.1), (0.78, 0.26), (0.95, 0.5), (0.97, 0.75), (0.86, 0.95), (0.7, 0.9), (0.55, 0.78), (0.38, 0.62), (0.18, 0.46), (0.0, 0.34)]

def butterfly_wing(w, h, pts, seed, hinge, eye):
    mask = poly_mask(w, h, pts)
    X, Y = grid(w, h)
    d = edge_dist(mask) / (w * 0.5)
    r = np.sqrt((X - hinge[0]) ** 2 + (Y - hinge[1]) ** 2)
    ang = np.arctan2(Y - hinge[1], X - hinge[0])
    n = fbm(w, h, 8, seed, 4)
    base = grad(np.clip(r * 1.15 + 0.15 * (n - 0.5), 0, 1), [(0, (40, 10, 90)), (0.25, (110, 30, 170)), (0.5, (225, 70, 180)), (0.75, (255, 150, 190)), (1, (255, 200, 140))])
    # venas
    veins = np.zeros((h, w))
    for k in range(9):
        a0 = (-1.2 + k * 0.3) if hinge[1] > 0.5 else (-0.2 + k * 0.3)
        da = np.abs(((ang - a0 + np.pi) % (2 * np.pi)) - np.pi) * r
        veins = np.maximum(veins, np.exp(-(da / 0.011) ** 2))
    scales = 0.9 + 0.2 * fbm(w, h, 2.0, seed + 4, 2)
    col = base * scales[..., None]
    col = col * (1 - 0.75 * veins[..., None])
    # borde negro con puntos blancos
    border = smoothstep(0.075, 0.03, d) * mask
    inner = smoothstep(0.1, 0.07, d) * mask
    col = col * (1 - inner[..., None] * 0.85) + np.array([14, 8, 24]) * inner[..., None] * 0.85
    dots = np.zeros((h, w))
    for i in range(14):
        t = i / 14
        for (cx, cy) in [(hinge[0] + (1 - hinge[0]) * 0.0 + 0, 0)]:
            pass
    # puntos: sobre el contorno, muestreando mascara
    ys, xs = np.nonzero((d > 0.032) & (d < 0.045))
    if len(xs):
        order = np.argsort(np.arctan2(ys - h * hinge[1], xs - w * hinge[0]))
        sel = order[::max(1, len(order) // 16)]
        for j in sel:
            rr = ((np.mgrid[0:h, 0:w][1] - xs[j]) ** 2 + (np.mgrid[0:h, 0:w][0] - ys[j]) ** 2)
            dots = np.maximum(dots, np.exp(-rr / (w * 0.0055) ** 2 * 6.0))
    col = col * (1 - dots[..., None]) + np.array([255, 250, 255]) * dots[..., None]
    # ocelo
    eyeg = np.zeros((h, w))
    if eye:
        cx, cy = eye
        rr = np.sqrt((X - cx) ** 2 + (Y - cy) ** 2)
        ring1 = smoothstep(0.11, 0.095, rr); ring2 = smoothstep(0.085, 0.07, rr); core = smoothstep(0.045, 0.03, rr)
        col = col * (1 - ring1[..., None]) + np.array([20, 10, 30]) * ring1[..., None]
        col = col * (1 - ring2[..., None]) + np.array([60, 190, 255]) * ring2[..., None]
        col = col * (1 - core[..., None]) + np.array([255, 255, 255]) * core[..., None]
        eyeg = np.maximum(ring2 * 0.6, core)
    glow = np.clip(veins * 0.35 + dots * 0.95 + eyeg, 0, 1) * mask
    return col, mask, glow

def butterfly():
    a = butterfly_wing(128, 128, UPPER, 3, (0.0, 0.88), (0.62, 0.3))
    b = butterfly_wing(128, 128, LOWER, 9, (0.0, 0.12), (0.62, 0.55))
    rgb = np.concatenate([a[0], b[0]], axis=1); al = np.concatenate([a[1], b[1]], axis=1); g = np.concatenate([a[2], b[2]], axis=1)
    save('butterfly', rgb, al)
    save('butterfly_g', np.ones((128, 256, 3)) * np.array([255, 190, 245.]), g)

def bfly_small():
    w, h = 64, 32
    X, Y = grid(w, h)
    pts = [(0.0, 0.5), (0.1, 0.1), (0.5, 0.0), (0.95, 0.12), (0.98, 0.4), (0.7, 0.5), (0.95, 0.62), (0.9, 0.92), (0.5, 0.98), (0.1, 0.9)]
    m = poly_mask(w, h, pts)
    r = np.sqrt(X ** 2 + (Y - 0.5) ** 2)
    col = grad(r * 1.1, [(0, (255, 255, 255)), (0.5, (150, 255, 235)), (1, (60, 200, 210))])
    ve = np.exp(-(((np.arctan2(Y - 0.5, X + 0.001) * r) % 0.25 - 0.125) / 0.012) ** 2) * 0.5
    col = col * (1 - ve[..., None] * 0.4)
    full = np.concatenate([m[:, ::-1], m], axis=1); fc = np.concatenate([col[:, ::-1], col], axis=1)
    save('bfly_small', fc, full)

def tube_tex(name, w, h, seed, base, spec, ring=16, glow=None):
    X, Y = grid(w, h)
    n = fbm(w, h, 6, seed, 3, tile=True)
    light = 0.5 + 0.5 * np.cos((X - 0.27) * 6.2832)
    sp = np.exp(-((((X - 0.27 + 0.5) % 1) - 0.5) / 0.07) ** 2)
    col = np.zeros((h, w, 3))
    for ch in range(3): col[..., ch] = base[0][ch] * (1 - light) + base[1][ch] * light
    col = col * (0.85 + 0.3 * n)[..., None] + sp[..., None] * np.array(spec) * 0.55
    rings = np.exp(-(((Y * h / ring) % 1 - 0.5) / 0.08) ** 2) * 0.3
    col = col * (1 - rings[..., None])
    return col, X, Y, n

def tentacles():
    col, X, Y, n = tube_tex('s', 64, 128, 4, [(5, 5, 9), (28, 30, 44)], (120, 140, 190))
    save('tentacle_slender', col, np.ones((128, 64)))
    col, X, Y, n = tube_tex('m', 64, 128, 7, [(40, 10, 70), (130, 50, 170)], (230, 170, 255))
    w, h = 64, 128
    # ventosas en un lado
    suck = np.zeros((h, w)); ring = np.zeros((h, w)); nod = np.zeros((h, w))
    for k in range(8):
        cy = (k + 0.5) * h / 8; cx = 0.5 * w
        rr = np.sqrt(((np.mgrid[0:h, 0:w][1] - cx) / 1.0) ** 2 + ((np.mgrid[0:h, 0:w][0] - cy) / 1.0) ** 2)
        suck = np.maximum(suck, smoothstep(7.5, 6.0, rr)); ring = np.maximum(ring, np.exp(-((rr - 7.0) / 1.4) ** 2))
    for k in range(10):
        cy = (k + 0.3) * h / 10; cx = 0.08 * w
        rr = np.sqrt((np.mgrid[0:h, 0:w][1] - cx) ** 2 + (np.mgrid[0:h, 0:w][0] - cy) ** 2)
        nod = np.maximum(nod, smoothstep(4.0, 1.5, rr))
        cx2 = 0.92 * w
        rr2 = np.sqrt((np.mgrid[0:h, 0:w][1] - cx2) ** 2 + (np.mgrid[0:h, 0:w][0] - cy) ** 2)
        nod = np.maximum(nod, smoothstep(4.0, 1.5, rr2))
    col = col * (1 - suck[..., None] * 0.55) + np.array([220, 150, 235]) * ring[..., None] * 0.9 + np.array([255, 120, 255]) * nod[..., None] * 0.9
    save('tentacle_mort', col, np.ones((h, w)))
    save('tentacle_mort_g', np.ones((h, w, 3)) * np.array([240, 110, 255.]), np.clip(nod * 0.95 + ring * 0.35, 0, 1))

def tail():
    w, h = 64, 128
    X, Y = grid(w, h)
    # escamas
    sx, sy = 8, 10
    row = np.floor(Y * h / sy); off = (row % 2) * 0.5
    fx = ((X * w / sx + off) % 1); fy = (Y * h / sy) % 1
    d = np.sqrt((fx - 0.5) ** 2 * 1.0 + (fy - 0.5) ** 2 * 0.8)
    scale = smoothstep(0.55, 0.2, d)
    n = fbm(w, h, 5, 12, 3, tile=True)
    light = 0.5 + 0.5 * np.cos((X - 0.25) * 6.2832)
    col = grad(0.2 + 0.5 * light * (0.4 + 0.6 * scale) + 0.15 * n, [(0, (30, 4, 6)), (0.4, (110, 14, 18)), (0.8, (200, 50, 30)), (1, (255, 120, 70))])
    belly = np.exp(-(((X - 0.75) % 1 - 0.5 + 0.5 - 0.5) / 0.12) ** 2) * 0
    save('tail_demon', col, np.ones((h, w)))
    cracks = smoothstep(0.62, 0.7, fbm(w, h, 5, 15, 3, tile=True)) * (1 - scale * 0.5)
    save('tail_demon_g', np.ones((h, w, 3)) * np.array([255, 110, 30.]), np.clip(cracks * 0.9, 0, 1))
    # pala
    w2 = h2 = 64
    X, Y = grid(w2, h2)
    rib = np.exp(-((X - 0.5) / 0.04) ** 2)
    col = grad(1 - Y * 0.6 + 0.2 * rib - np.abs(X - 0.5) * 0.4, [(0, (50, 6, 8)), (0.5, (170, 30, 24)), (1, (255, 110, 60))])
    al = np.ones((h2, w2))
    save('spade', col, al)
    save('spade_g', np.ones((h2, w2, 3)) * np.array([255, 120, 40.]), np.clip(rib * 0.8 + np.exp(-(np.minimum(X, 1 - X) / 0.06) ** 2) * 0.7, 0, 1))

def petals():
    w, h = 256, 256
    rgb = np.zeros((h, w, 3)); a = np.zeros((h, w))
    for k in range(4):
        cw = 64
        X, Y = grid(cw, h); X = X * 2 - 1
        t = 1 - Y
        hw = np.sin(np.clip(t, 0, 1) ** 0.75 * np.pi) ** 0.62 * (0.35 + 0.65 * np.minimum(1, t * 4))
        n = fbm(cw, h, 10, 30 + k, 3)
        hw = hw * (1 + (n - 0.5) * 0.12)
        u = X / np.maximum(hw, 1e-3)
        alpha = np.clip((1 - np.abs(u)) * hw * cw * 0.5, 0, 1)
        hue = [(0, 0, 0), (12, -8, 10), (-10, 6, 18), (16, 4, -10)][k]
        col = grad(t * (0.9 + 0.1 * np.cos(u * 3)) , [(0, (60, 14, 100)), (0.3, (130, 36, 170)), (0.65, (205, 80, 215)), (1, (245, 195, 250))]) + np.array(hue)
        veins = np.zeros((h, cw))
        for j in range(-4, 5):
            xv = j * 0.2 * (0.3 + 0.7 * t)
            veins = np.maximum(veins, np.exp(-(((X - xv) / np.maximum(hw, 0.2)) / 0.03) ** 2) * (0.5 if j else 0.8))
        col = col * (1 - 0.25 * veins[..., None]) + 18 * n[..., None]
        edge = np.exp(-((1 - np.abs(u)) / 0.12) ** 2)
        col = col + edge[..., None] * 25
        rgb[:, k * cw:(k + 1) * cw] = col; a[:, k * cw:(k + 1) * cw] = alpha * 0.97
    save('petal_mort', rgb, a)

def plants():
    # tallo
    w, h = 32, 64
    X, Y = grid(w, h); n = fbm(w, h, 5, 2, 3, tile=True); light = 0.5 + 0.5 * np.cos((X - 0.3) * 6.2832)
    col = grad(0.25 + 0.5 * light + 0.1 * n, [(0, (22, 40, 28)), (0.5, (60, 110, 55)), (1, (140, 190, 100))])
    save('stem', col, np.ones((h, w)))
    # enredadera
    w, h = 64, 64
    X, Y = grid(w, h); n = fbm(w, h, 6, 5, 4, tile=True); light = 0.5 + 0.5 * np.cos((X - 0.3) * 6.2832)
    gro = np.exp(-(((X * 5) % 1 - 0.5) / 0.15) ** 2) * 0.25
    col = grad(0.2 + 0.5 * light + 0.18 * n - gro, [(0, (18, 30, 20)), (0.5, (52, 92, 44)), (1, (120, 170, 80))]) + np.array([14, -4, 22]) * (n[..., None] > 0.6)
    save('vine', col, np.ones((h, w)))
    # hojas (2 variantes)
    w, h = 128, 128
    rgb = np.zeros((h, w, 3)); a = np.zeros((h, w))
    for k in range(2):
        cw = 64
        X, Y = grid(cw, h); X = X * 2 - 1; t = 1 - Y
        hw = np.sin(np.clip(t, 0, 1) ** 0.8 * np.pi) ** 0.8
        hw = hw * (1 + 0.1 * np.sin(Y * 18 + k * 3) * (1 - np.abs(t - 0.5)))
        u = X / np.maximum(hw, 1e-3)
        alpha = np.clip((1 - np.abs(u)) * hw * cw * 0.5, 0, 1)
        n = fbm(cw, h, 7, 40 + k, 3)
        rib = np.exp(-(X / 0.05) ** 2)
        side = np.exp(-((((np.abs(X) * 3 + (1 - t) * 0.9) * 3.2) % 1 - 0.5) / 0.1) ** 2) * 0.6 * np.minimum(1, t * 3)
        col = grad(t * 0.8 + 0.12 * n + 0.1 * (1 - np.abs(u)), [(0, (24, 54, 30)), (0.5, (60, 120, 52)), (1, (130, 185, 90))])
        col = col + np.array([90, 20, 120]) * (smoothstep(0.72, 1.0, np.abs(u)))[..., None] * 0.45
        col = col * (1 - 0.3 * side[..., None]) + rib[..., None] * 60
        rgb[:, k * cw:(k + 1) * cw] = col; a[:, k * cw:(k + 1) * cw] = alpha
    save('leaf', rgb, a)

def head_items():
    w = h = 64
    X, Y = grid(w, h)
    top = np.cos((X - 0.25) * 6.2832) * 0.5 + 0.5
    hi = np.exp(-(((X - 0.25 + 0.5) % 1 - 0.5) / 0.09) ** 2)
    orn = np.exp(-((((Y * 2) % 1) - 0.5) / 0.22) ** 2) * np.exp(-((X - 0.75) / 0.2) ** 2) * 0.5
    n = fbm(w, h, 5, 7, 3, tile=True)
    col = grad(0.35 + 0.45 * top + 0.25 * hi - 0.15 * orn + 0.08 * n, [(0, (120, 76, 14)), (0.4, (214, 160, 40)), (0.75, (255, 218, 110)), (1, (255, 250, 220))])
    save('halo', col, np.ones((h, w)))
    save('halo_g', np.ones((h, w, 3)) * np.array([255, 220, 120.]), np.clip(0.55 + 0.35 * hi + 0.2 * orn, 0, 1))
    # espinas / corteza
    n = fbm(w, h, 5, 21, 4, tile=True)
    gro = np.exp(-(((X * 6) % 1 - 0.5) / 0.14) ** 2)
    col = grad(0.25 + 0.45 * top + 0.2 * n - 0.25 * gro, [(0, (20, 8, 8)), (0.5, (82, 28, 28)), (1, (170, 88, 70))])
    save('thorn', col, np.ones((h, w)))
    # cuerno 64x128
    w, h = 64, 128
    X, Y = grid(w, h); n = fbm(w, h, 8, 25, 4, tile=True)
    top = 0.5 + 0.5 * np.cos((X - 0.25) * 6.2832)
    ridge = 0.5 + 0.5 * np.sin(Y * 6.2832 * 4 + n * 2)
    col = grad(0.12 + 0.42 * top + 0.25 * ridge + 0.1 * n, [(0, (12, 8, 10)), (0.4, (70, 26, 22)), (0.75, (150, 80, 52)), (1, (225, 170, 120))])
    save('horn', col, np.ones((h, w)))
    cr = smoothstep(0.6, 0.68, fbm(w, h, 7, 29, 3, tile=True)) * smoothstep(0.1, 0.6, 1 - Y * 0.6)
    save('horn_g', np.ones((h, w, 3)) * np.array([255, 120, 40.]), np.clip(cr, 0, 1))
