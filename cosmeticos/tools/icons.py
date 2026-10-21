import math, numpy as np
from PIL import Image, ImageDraw, ImageFilter
OUT = '/home/claude/mods/cosmeticos/src/main/resources/assets/cosmeticos/textures/mob_effect/'
S = 18; K = 16; N = S * K

def newc(): return Image.new('RGBA', (N, N), (0, 0, 0, 0))
def hexc(c): return ((c >> 16) & 255, (c >> 8) & 255, c & 255)
def mix(a, b, t): return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))
def dark(c, k): return tuple(int(v * k) for v in c)
def lite(c, k): return tuple(min(255, int(v + (255 - v) * k)) for v in c)
def P(x, y): return (x * K, y * K)

def finish(layer, name):
    a = layer.split()[3]
    outline = a.filter(ImageFilter.MaxFilter(K * 2 + 1)).filter(ImageFilter.GaussianBlur(K * 0.25))
    base = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    ol = Image.new('RGBA', (N, N), (12, 8, 18, 255)); base.paste(ol, (0, 0), outline.point(lambda v: min(255, v * 2)))
    base.alpha_composite(layer)
    im = base.resize((S, S), Image.LANCZOS)
    im.save(OUT + name + '.png')
    return im

def poly(d, pts, fill): d.polygon([P(*p) for p in pts], fill=fill)

def smooth(pts, n=10):
    out = []; m = len(pts)
    for i in range(m - 1):
        p0, p1, p2, p3 = pts[max(i - 1, 0)], pts[i], pts[i + 1], pts[min(i + 2, m - 1)]
        for t in np.linspace(0, 1, n, endpoint=False):
            out.append(tuple(0.5 * ((2 * p1[k]) + (-p0[k] + p2[k]) * t + (2 * p0[k] - 5 * p1[k] + 4 * p2[k] - p3[k]) * t * t + (-p0[k] + 3 * p1[k] - 3 * p2[k] + p3[k]) * t ** 3) for k in range(2)))
    out.append(pts[-1]); return out

def thick(d, pts, w0, w1, col, hi=None):
    n = len(pts)
    for i in range(n - 1):
        w = w0 + (w1 - w0) * i / (n - 1); x, y = P(*pts[i]); x2, y2 = P(*pts[i + 1])
        d.line([(x, y), (x2, y2)], fill=col, width=int(w * K))
        d.ellipse([x - w * K / 2, y - w * K / 2, x + w * K / 2, y + w * K / 2], fill=col)
    if hi:
        for i in range(n - 1):
            x, y = P(*pts[i]); x2, y2 = P(*pts[i + 1]); w = (w0 + (w1 - w0) * i / (n - 1)) * 0.3
            d.line([(x - K * 0.3, y - K * 0.3), (x2 - K * 0.3, y2 - K * 0.3)], fill=hi, width=max(1, int(w * K)))

def wings_fan(c, c2, name):
    l = newc(); d = ImageDraw.Draw(l)
    for side in (-1, 1):
        cx = 9
        for i in range(7):
            f = i / 6; a = math.radians(200 - 150 * f) if side > 0 else math.radians(-20 + 150 * f)
            a = math.radians(-70 + 130 * f) if side > 0 else math.radians(180 + 70 - 130 * f)
            ln = 5 + 4.5 * math.sin(math.pi * (0.2 + 0.8 * f) * 0.8)
            x0, y0 = cx + side * 1.0, 11 - 4 * f
            tip = (x0 + side * ln * math.cos(math.radians(70 - 100 * f)) * 1.0, y0 - ln * math.sin(math.radians(60 - 130 * f)) * 0.9)
            mid1 = (x0 + (tip[0] - x0) * 0.5 + side * 0.9, y0 + (tip[1] - y0) * 0.5 - 0.5 )
            col = mix(c, c2, f)
            poly(d, [(x0, y0 + 0.6), (mid1[0], mid1[1] - 0.8), tip, (mid1[0] + side * 0.2, mid1[1] + 1.0)], col + (255,))
            d.line([P(x0, y0 + 0.3), P(*tip)], fill=lite(col, 0.5) + (255,), width=int(K * 0.35))
    d.ellipse([P(8.2, 10), P(9.8, 13)], fill=lite(c, 0.4) + (255,))
    finish(l, name)

def wings_bat(c, c2, name, glow):
    l = newc(); d = ImageDraw.Draw(l)
    for side in (-1, 1):
        w = (9 + side * 0.8, 10.5); wr = (9 + side * 7.0, 3.0)
        tips = [(9 + side * 8.6, 3.0), (9 + side * 8.4, 8.0), (9 + side * 6.2, 12.2), (9 + side * 2.5, 13.6)]
        pts = [(9 + side * 1.0, 11.5), wr] + [(9 + side * 7.8, 5.8), tips[1], (9 + side * 7.1, 10.2), tips[2], (9 + side * 4.4, 12.4), tips[3]]
        poly(d, [(9 + side * 1.0, 11.5), (9 + side * 4.0, 4.5), wr, tips[1], (9 + side * 6.5, 9.2), tips[2], (9 + side * 4.0, 11.4), tips[3]], c + (255,))
        for tp in tips[1:]:
            d.line([P(9 + side * 4.0, 5.2), P(*tp)], fill=c2 + (255,), width=int(K * 0.6))
        d.line([P(9 + side * 1.0, 11.5), P(9 + side * 4.0, 4.5), P(*wr)], fill=lite(c2, 0.2) + (255,), width=int(K * 0.9))
    finish(l, name)

def wings_butterfly(c, c2, name):
    l = newc(); d = ImageDraw.Draw(l)
    for side in (-1, 1):
        ox = 9
        poly(d, [(ox + side * .5, 9), (ox + side * 3, 3.2), (ox + side * 7.6, 2.4), (ox + side * 8.6, 6), (ox + side * 5.5, 9.4)], c + (255,))
        poly(d, [(ox + side * .5, 9.3), (ox + side * 5.2, 10), (ox + side * 6.6, 13.5), (ox + side * 3.5, 15.8), (ox + side * .8, 12.5)], c2 + (255,))
        d.ellipse([P(ox + side * 5.6 - .9, 5 - .9), P(ox + side * 5.6 + .9, 5 + .9)], fill=(255, 255, 255, 255))
        d.ellipse([P(ox + side * 4.2 - .6, 12.3 - .6), P(ox + side * 4.2 + .6, 12.3 + .6)], fill=(255, 255, 255, 255))
    d.rounded_rectangle([P(8.5, 5), P(9.5, 14)], radius=K * 0.4, fill=(40, 24, 50, 255))
    finish(l, name)

def tentacles(c, c2, name, tip=False):
    l = newc(); d = ImageDraw.Draw(l)
    for i, (x0, sw) in enumerate([(5, 1), (9, -1), (13, 1)]):
        pts = smooth([(x0, 17), (x0 + sw * 1.6, 13), (x0 - sw * 1.2, 9.5), (x0 + sw * 1.6, 6), (x0 + sw * 0.2, 2.2)])
        thick(d, pts, 2.2, 0.5, mix(c, c2, i / 2) + (255,), lite(c, 0.35) + (255,))
        if tip:
            x, y = pts[-1]; d.ellipse([P(x - .9, y - .9), P(x + .9, y + .9)], fill=lite(c2, 0.5) + (255,))
    finish(l, name)

def tail(c, c2, name):
    l = newc(); d = ImageDraw.Draw(l)
    pts = smooth([(3, 16), (6, 11.5), (4.5, 7.5), (8, 5.5), (12, 6.2), (14.5, 4)])
    thick(d, pts, 2.4, 0.9, c + (255,), lite(c, 0.35) + (255,))
    x, y = pts[-1]
    poly(d, [(x - .5, y + .8), (x + 1.2, y - 2.6), (x + 2.9, y - .2), (x + 1.4, y + 1.2)], c2 + (255,))
    finish(l, name)

def flower(c, c2, name, n=6, many=False):
    l = newc(); d = ImageDraw.Draw(l)
    def one(cx, cy, r, rot=0):
        for k in range(n):
            a = rot + 2 * math.pi * k / n
            tx, ty = cx + math.cos(a) * r, cy + math.sin(a) * r
            nx, ny = -math.sin(a) * r * .38, math.cos(a) * r * .38
            poly(d, [(cx, cy), (cx + (tx - cx) * .55 + nx, cy + (ty - cy) * .55 + ny), (tx, ty), (cx + (tx - cx) * .55 - nx, cy + (ty - cy) * .55 - ny)], mix(c, c2, k / n) + (255,))
        d.ellipse([P(cx - r * .25, cy - r * .25), P(cx + r * .25, cy + r * .25)], fill=(255, 226, 120, 255))
    if many:
        one(5.2, 5.5, 3.2); one(12.4, 7.2, 3.6, .4); one(7.5, 13, 3.2, .8)
    else:
        one(9, 9, 7.2)
    finish(l, name)

def vines(c, c2, name):
    l = newc(); d = ImageDraw.Draw(l)
    pts = smooth([(4, 17), (11, 14), (5, 10.5), (12, 7), (6, 3.5), (10, 1.8)])
    thick(d, pts, 1.3, 0.7, c + (255,))
    for (x, y, s) in [(11, 14, 1), (5, 10.5, -1), (12, 7, 1), (6, 3.5, -1)]:
        poly(d, [(x, y), (x + s * 2.2, y - 1.6), (x + s * 3.8, y - 0.3), (x + s * 2.0, y + 1.0)], c2 + (255,))
    finish(l, name)

def halo(c, name):
    l = newc(); d = ImageDraw.Draw(l)
    d.ellipse([P(2, 5), P(16, 11)], outline=c + (255,), width=int(K * 2))
    d.ellipse([P(2.7, 5.6), P(15.3, 10.4)], outline=lite(c, 0.6) + (255,), width=int(K * 0.5))
    for (x, y) in [(3, 3), (15, 3), (9, 2)]:
        poly(d, [(x, y - 1.5), (x + .4, y - .4), (x + 1.5, y), (x + .4, y + .4), (x, y + 1.5), (x - .4, y + .4), (x - 1.5, y), (x - .4, y - .4)], (255, 250, 210, 255))
    finish(l, name)

def crown(c, name):
    l = newc(); d = ImageDraw.Draw(l)
    pts = [(2, 12), (3.4, 6), (5.2, 11), (7, 4.5), (9, 11), (11, 4.5), (12.8, 11), (14.6, 6), (16, 12)]
    poly(d, pts + [(16, 14), (2, 14)], c + (255,))
    d.line([P(2, 13), P(16, 13)], fill=lite(c, 0.3) + (255,), width=int(K * 1.2))
    for x in (5, 9, 13): d.ellipse([P(x - .6, 11.4), P(x + .6, 12.6)], fill=(255, 60, 60, 255))
    finish(l, name)

def horns(c, name):
    l = newc(); d = ImageDraw.Draw(l)
    for side in (-1, 1):
        pts = smooth([(9 + side * 3, 15), (9 + side * 5, 11), (9 + side * 6.4, 6.5), (9 + side * 5.4, 2.2)])
        thick(d, pts, 3.4, 0.6, c + (255,), lite(c, 0.4) + (255,))
    finish(l, name)

def flame(c, c2, name):
    l = newc(); d = ImageDraw.Draw(l)
    poly(d, [(9, 1.2), (12.4, 6), (14.6, 10), (13.2, 14.8), (9, 16.8), (4.8, 14.8), (3.4, 10), (5.6, 7), (6.6, 9.4), (7.6, 5)], c + (255,))
    poly(d, [(9, 6.5), (11.4, 10.2), (10.8, 14), (9, 15.4), (7.2, 14), (6.8, 10.6)], c2 + (255,))
    poly(d, [(9, 10), (10.2, 12.5), (9, 14.6), (7.8, 12.5)], (255, 244, 180, 255))
    finish(l, name)

def smoke(c, name):
    l = newc(); d = ImageDraw.Draw(l)
    for (x, y, r, k) in [(6, 12, 4.2, .7), (11.5, 11, 4.6, .8), (9, 7, 4.2, 1.0), (5.5, 5.2, 2.5, 1.2), (12.5, 4.5, 2.2, 1.3)]:
        d.ellipse([P(x - r, y - r), P(x + r, y + r)], fill=lite(c, k * 0.15) + (255,))
    d.ellipse([P(7.8, 8.2), P(8.8, 9.6)], fill=(200, 80, 255, 255)); d.ellipse([P(10, 8.2), P(11, 9.6)], fill=(200, 80, 255, 255))
    finish(l, name)

def orbs(c, name):
    l = newc(); d = ImageDraw.Draw(l)
    d.ellipse([P(2, 4.5), P(16, 13.5)], outline=lite(c, .2) + (255,), width=int(K * .7))
    d.ellipse([P(6.4, 6.4), P(11.6, 11.6)], fill=c + (255,)); d.ellipse([P(7.4, 7.2), P(9, 8.8)], fill=(235, 250, 255, 255))
    for (x, y) in [(2.4, 9), (15.4, 8)]: d.ellipse([P(x - 1.2, y - 1.2), P(x + 1.2, y + 1.2)], fill=lite(c, .3) + (255,))
    finish(l, name)

def rings(c, name):
    l = newc(); d = ImageDraw.Draw(l)
    for (y, w, col) in [(13, 15, c), (9.2, 11.5, lite(c, .25)), (5.4, 8, lite(c, .5))]:
        d.ellipse([P(9 - w / 2, y - w / 5), P(9 + w / 2, y + w / 5)], outline=col + (255,), width=int(K * 1.1))
    finish(l, name)

def eyes(c, name):
    l = newc(); d = ImageDraw.Draw(l)
    for (x, y, r) in [(9, 9, 6.4), (3.4, 4, 2.6), (14.8, 14, 2.6)]:
        d.ellipse([P(x - r, y - r * .7), P(x + r, y + r * .7)], fill=(240, 232, 228, 255))
        d.ellipse([P(x - r * .5, y - r * .5), P(x + r * .5, y + r * .5)], fill=c + (255,))
        d.ellipse([P(x - r * .12, y - r * .42), P(x + r * .12, y + r * .42)], fill=(10, 4, 8, 255))
    finish(l, name)

def stars(c, name):
    l = newc(); d = ImageDraw.Draw(l)
    def st(x, y, r):
        poly(d, [(x, y - r), (x + r * .28, y - r * .28), (x + r, y), (x + r * .28, y + r * .28), (x, y + r), (x - r * .28, y + r * .28), (x - r, y), (x - r * .28, y - r * .28)], c + (255,))
    st(9, 8.6, 6.8); st(3.6, 3.4, 2.6); st(14.6, 14.4, 2.8); st(14.8, 3.6, 1.8)
    finish(l, name)

def bolt(c, name):
    l = newc(); d = ImageDraw.Draw(l)
    poly(d, [(10.5, .8), (3.6, 10), (8, 10), (6, 17.2), (14.4, 7.2), (9.6, 7.2), (12.2, .8)], c + (255,))
    poly(d, [(10, 3.4), (6.4, 9), (9.4, 9), (8.2, 13.2), (12, 8.6), (8.6, 8.6), (10.6, 3.4)], (240, 252, 255, 255))
    finish(l, name)

def bflies(c, name):
    l = newc(); d = ImageDraw.Draw(l)
    def b(x, y, s, col):
        for side in (-1, 1):
            poly(d, [(x, y), (x + side * 3.4 * s, y - 2.6 * s), (x + side * 3.8 * s, y + .6 * s), (x + side * 1.2 * s, y + 1.2 * s)], col + (255,))
            poly(d, [(x, y + .4 * s), (x + side * 2.8 * s, y + 1.4 * s), (x + side * 2.0 * s, y + 3.4 * s)], lite(col, .35) + (255,))
    b(6, 7, 1.15, c); b(13, 12.5, .9, lite(c, .3))
    finish(l, name)

def flies(c, name):
    l = newc(); d = ImageDraw.Draw(l)
    for (x, y, r) in [(5, 5, 1.7), (12.5, 4, 1.3), (9, 10, 2.1), (4, 13.5, 1.2), (14, 14, 1.6)]:
        d.ellipse([P(x - r * 2.2, y - r * 2.2), P(x + r * 2.2, y + r * 2.2)], fill=c + (70,))
        d.ellipse([P(x - r, y - r), P(x + r, y + r)], fill=lite(c, .5) + (255,))
    finish(l, name)

def build():
    ic = {}
    wings_fan(hexc(0xFFE9A0), (255, 255, 255), 'alas_luz')
    wings_bat(hexc(0x7A28B8), (190, 90, 230), 'alas_mortifilia', 0)
    wings_bat(hexc(0x8A1010), (220, 60, 30), 'alas_demonio', 0)
    wings_fan(hexc(0x4FC8E8), (225, 255, 255), 'alas_cristal')
    wings_fan(hexc(0xE04010), (255, 200, 40), 'alas_fuego')
    wings_butterfly(hexc(0xFF70D0), (130, 80, 230), 'alas_mariposa')
    tentacles((30, 28, 40), (90, 86, 120), 'tentaculos_slender')
    tentacles(hexc(0x7A2AB0), (230, 110, 255), 'tentaculos_mortifilia', True)
    tail(hexc(0xA01818), (255, 90, 40), 'cola_demonio')
    flower(hexc(0xB040E8), (240, 170, 250), 'flores_mortifilia', 6, True)
    vines(hexc(0x3E8A3A), (130, 200, 80), 'enredadera')
    flower(hexc(0xC860F0), (250, 190, 255), 'flor_cabeza', 7)
    halo(hexc(0xFFD040), 'aureola')
    crown(hexc(0x8A2A28), 'corona_espinas')
    horns(hexc(0x7A1818), 'cuernos_demonio')
    flame(hexc(0xE84A10), (255, 190, 50), 'aura_fuego')
    smoke(hexc(0x3A2A5A), 'aura_oscura')
    orbs(hexc(0x50B8FF), 'orbitas')
    rings(hexc(0x8A60F0), 'anillos_arcanos')
    eyes(hexc(0xE02020), 'ojos_flotantes')
    stars(hexc(0xFFE680), 'estrellas')
    bolt(hexc(0x50C0FF), 'rayos')
    bflies(hexc(0x50F0D0), 'mariposas_luz')
    flies(hexc(0xB8F030), 'luciernagas')

build()
