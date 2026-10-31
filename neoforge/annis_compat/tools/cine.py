# Genera las animaciones de Cinematic Interactions para Punchy (Bedrock 1.8.0, grados / pixeles).
# Basadas en las poses de Cinematic (HumanoidModelMixin) y mejoradas: cuerpo, cabeza y acciones.
import json, math, sys
S = math.sin; C = math.cos; PI = math.pi
W = 1.8                     # Cinematic: t = ticks * 0.09 rad -> 1.8 rad por segundo
L = 2 * 2 * PI / W          # bucle de ~6.98 s (admite frecuencias de medio paso)
D = 57.29578

def r(v): return round(v, 3)

def loop(bones, length=L, step=0.125):
    n = int(round(length / step))
    out = {}
    for b, chans in bones.items():
        bo = {}
        for ch, f in chans.items():
            if callable(f):
                keys = {}
                for i in range(n + 1):
                    t = length * i / n
                    keys[f"{r(t):g}" if t else "0.0"] = [r(x) for x in f(t * W)]
                bo[ch] = keys
            else:
                bo[ch] = [r(x) for x in f]
        out[b] = bo
    return {"loop": True, "animation_length": r(length), "bones": out}

def once(bones, length, step=1/24):
    n = int(math.ceil(length / step))
    out = {}
    for b, chans in bones.items():
        bo = {}
        for ch, f in chans.items():
            keys = {}
            for i in range(n + 1):
                t = min(length, i * step)
                keys[f"{r(t):g}" if t else "0.0"] = [r(x) for x in f(t)]
            bo[ch] = keys
        out[b] = bo
    return {"animation_length": r(length), "bones": out}

def rad(x): return x * D
# ---- curvas de Cinematic (Anim) ----
def clamp01(x): return max(0.0, min(1.0, x))
def window(t, a, b): return clamp01((t - a) / (b - a)) if b > a else (1.0 if t >= b else 0.0)
def smoother(x): return x * x * x * (x * (x * 6 - 15) + 10)
def out_cubic(x): return 1 - (1 - x) ** 3
def in_cubic(x): return x ** 3
def in_out_cubic(x): return 4 * x ** 3 if x < .5 else 1 - (-2 * x + 2) ** 3 / 2
def in_out_sine(x): return -(C(PI * x) - 1) / 2
def lerp(w, a, b): return a + (b - a) * w

A = {}
# ===== mesa de crafteo y estaciones sin pose propia (cartografia, herreria, crafter...) =====
A["default"] = loop({
    "body": {"rotation": lambda p: [4 + .8 * S(p), 3 * S(.5 * p), 0]},
    "head": {"rotation": lambda p: [7, 0, 0]},
    "right_arm": {"rotation": lambda p: [rad(-.98) + 4 * S(p) + 2 * S(2 * p + 1), rad(-.16) + 3 * S(.5 * p), rad(.06)],
                  "position": [0, 0, -.6]},
    "left_arm": {"rotation": lambda p: [rad(-.98) + 3 * S(p + 2.1) + 1.5 * S(2 * p + 2.5), rad(.16) - 2 * S(.5 * p), rad(-.06)],
                 "position": [0, 0, -.6]},
})
def place(t):  # alcanzar el tablero y dejar un objeto
    k = S(PI * window(t, 0, .6)) ** 2
    return k
A["default_accion"] = once({
    "right_arm": {"rotation": lambda t: [rad(-.98) - 18 * place(t), rad(-.16) - 6 * place(t), rad(.06)]},
    "body": {"rotation": lambda t: [4 + 5 * place(t), -3 * place(t), 0]},
}, .6)
# ===== atril: leer el libro apoyado =====
def flip(p):   # pasa una hoja una vez por bucle
    ph = (p / (2 * PI)) % 2.0      # 0..2 a lo largo del bucle
    return S(PI * clamp01((ph - 1.3) / .45)) ** 2
A["lectern"] = loop({
    "body": {"rotation": lambda p: [7 + .7 * S(p), 0, 0]},
    "head": {"rotation": lambda p: [13 + 1.5 * S(.5 * p), 4 * flip(p), 0]},
    "right_arm": {"rotation": lambda p: [-44 + 1.2 * S(p) - 8 * flip(p), -18 + 26 * flip(p), 4]},
    "left_arm": {"rotation": lambda p: [-44 + 1.2 * S(p + 1.4), 18, -4]},
})
# ===== yunque =====
A["anvil"] = loop({
    "body": {"rotation": lambda p: [5 + .8 * S(p), -4, 0]},
    "head": {"rotation": lambda p: [9, 0, 0]},
    "left_arm": {"rotation": lambda p: [rad(-1.05) + rad(.04) * S(p + 2.1), rad(.28), rad(-.06)]},
    "right_arm": {"rotation": lambda p: [rad(-.98) + rad(.05) * S(p), rad(-.2), rad(.08)]},
})
BLOWS = [.7, 1.02, 1.3]; PUT = BLOWS[-1] + .32
def hammer(s):  # AnvilStation.armPitch, en radianes
    if s < .3: return lerp(smoother(s / .3), -.98, .2)
    if s < .55: return lerp(out_cubic(window(s, .3, .55)), .2, -2.75)
    if s >= PUT: return lerp(in_out_cubic(window(s, PUT, 2.05)), .2, -.98)
    for i, b in enumerate(BLOWS):
        prev = .55 if i == 0 else BLOWS[i - 1] + .12
        if s < b - .09: return lerp(in_out_sine(window(s, prev, b - .09)), -1.6 if i else -2.75, -2.75)
        if s < b: return lerp(in_cubic(window(s, b - .09, b)), -2.75, -1.26)
        if s < b + .12: return lerp(out_cubic(window(s, b, b + .12)), -1.26, -1.6)
    return lerp(in_cubic(window(s, BLOWS[-1] + .12, PUT)), -1.6, .2)
def impact(s):  # 1 justo en cada golpe
    return max([max(0.0, 1 - abs(s - b) / .1) for b in BLOWS])
def raised(s):  # cuanto esta el martillo arriba
    return clamp01((-hammer(s) * D - 60) / 97)
A["anvil_accion"] = once({
    "right_arm": {"rotation": lambda s: [hammer(s) * D, -20, 4.6]},
    "left_arm": {"rotation": lambda s: [rad(-1.05) + 6 * impact(s), 16, -3.4]},
    "body": {"rotation": lambda s: [5 - 5 * raised(s) + 9 * impact(s), -4 - 6 * raised(s), 0]},
    "head": {"rotation": lambda s: [9 - 5 * raised(s) + 5 * impact(s), 0, 0]},
}, 2.05)
# ===== fogata: agachado, o sentado y echado hacia atras =====
legs = {"right_leg": {"rotation": [rad(-2.0), rad(.12), rad(.05)]},
        "left_leg": {"rotation": [rad(-1.45), rad(-.22), 0]}}
A["campfire"] = loop(dict(legs, **{
    "body": {"rotation": lambda p: [6 + .8 * S(p), 0, 0]},
    "head": {"rotation": lambda p: [11, 0, 0]},
    "right_arm": {"rotation": lambda p: [rad(-1.05) + rad(.03) * S(.5 * p) * 2, rad(-.1), rad(.05)]},
    "left_arm": {"rotation": lambda p: [rad(.35) + 2 * S(p), 0, rad(-.32)]},
}))
cl, sl = C(.45), S(.45)
A["campfire_sentado"] = loop({
    "right_leg": {"rotation": [rad(-2.1), rad(.12), rad(.05)]},
    "left_leg": {"rotation": [rad(-1.45), rad(-.22), 0]},
    "body": {"rotation": lambda p: [rad(-.45) + .8 * S(p), 0, 0], "position": [0, -(12 - 12 * cl), 12 * sl]},
    "head": {"rotation": lambda p: [14 + 1.5 * S(.5 * p), 0, 0], "position": [0, -(12 - 12 * cl), 12 * sl]},
    "right_arm": {"rotation": lambda p: [rad(-1.5) + 3 * S(.5 * p), rad(-.3), rad(.05)], "position": [0, -(10 - 10 * cl), 10 * sl]},
    "left_arm": {"rotation": lambda p: [rad(.4), 0, rad(-.25)], "position": [0, -(10 - 10 * cl), 10 * sl]},
})
# ===== telar: la lanzadera va y viene =====
A["loom"] = loop({
    "body": {"rotation": lambda p: [4, 3 * S(2 * p), 0]},
    "head": {"rotation": lambda p: [8, 4 * S(2 * p - .4), 0]},
    "right_arm": {"rotation": lambda p: [rad(-1.0) + rad(.04) * S(p), rad(-.35) + 11.5 * S(2 * p), rad(.05)]},
    "left_arm": {"rotation": lambda p: [rad(-1.0) + rad(.04) * S(p + 1.3), rad(.35) + 11.5 * S(2 * p), rad(-.05)]},
})
# ===== afiladora: las dos manos en la manivela =====
A["grindstone"] = loop({
    "body": {"rotation": lambda p: [6 + .8 * S(p), 0, 0]},
    "head": {"rotation": lambda p: [10, 0, 0]},
    "right_arm": {"rotation": lambda p: [rad(-1.15) + rad(.03) * S(p), rad(-.32), rad(.05)]},
    "left_arm": {"rotation": lambda p: [rad(-1.2) + rad(.03) * S(p + 1.3), rad(.3), rad(-.05)]},
})
def crank(s):
    th = 2 * PI * 2 * in_out_sine(clamp01(s / 2.4))
    env = S(PI * clamp01(s / 2.4)) ** .5
    return th, env
A["grindstone_accion"] = once({
    "right_arm": {"rotation": lambda s: [rad(-1.15) + 13 * S(crank(s)[0]) * crank(s)[1], rad(-.32) + 9 * C(crank(s)[0]) * crank(s)[1] - 9 * crank(s)[1], rad(.05)]},
    "left_arm": {"rotation": lambda s: [rad(-1.2) + 13 * S(crank(s)[0]) * crank(s)[1], rad(.3) + 9 * C(crank(s)[0]) * crank(s)[1] - 9 * crank(s)[1], rad(-.05)]},
    "body": {"rotation": lambda s: [6 + 4 * S(crank(s)[0]) * crank(s)[1], 3 * C(crank(s)[0]) * crank(s)[1], 0]},
}, 2.4)
# ===== cortapiedras: empujar la piedra por la sierra =====
A["stonecutter"] = loop({
    "body": {"rotation": lambda p: [5 + .8 * S(p), 0, 0]},
    "head": {"rotation": lambda p: [10, 0, 0]},
    "left_arm": {"rotation": lambda p: [rad(-1.0) + rad(.04) * S(p + 2.1), rad(-.2), rad(-.06)]},
    "right_arm": {"rotation": lambda p: [rad(-.98) + rad(.05) * S(p), rad(-.12), rad(.06)]},
})
def push(s): return S(PI * smoother(clamp01(s / 1.05)))
A["stonecutter_accion"] = once({
    "left_arm": {"rotation": lambda s: [rad(-1.0) - 6 * push(s), rad(-.2) + rad(.8) * push(s), rad(-.06)]},
    "right_arm": {"rotation": lambda s: [rad(-.98) - 6 * push(s), rad(-.12) + rad(.48) * push(s), rad(.06)]},
    "body": {"rotation": lambda s: [5 + 3 * push(s), 7 * push(s), 0]},
}, 1.05)
# ===== horno, ahumador, alto horno y soporte de pociones =====
A["furnace"] = loop({
    "body": {"rotation": lambda p: [8 + .8 * S(p), 0, 0]},
    "head": {"rotation": lambda p: [10, 0, 0]},
    "right_arm": {"rotation": lambda p: [rad(-1.32) + rad(.12) * S(1.5 * p), rad(-.12) + rad(.08) * S(.5 * p), rad(.04)]},
    "left_arm": {"rotation": lambda p: [rad(-1.6) + rad(.03) * S(p + 2.1), rad(.32), rad(-.05)]},
})
# ===== biblioteca cincelada: buscar un libro / leerlo =====
A["chiseled_bookshelf"] = loop({
    "body": {"rotation": lambda p: [2, 4 * S(.5 * p), 0]},
    "head": {"rotation": lambda p: [-2, 9 * S(.5 * p - .3), 0]},
    "right_arm": {"rotation": lambda p: [rad(-1.4) + rad(.1) * S(p), rad(-.1) + rad(.18) * S(.5 * p), rad(.04)]},
    "left_arm": {"rotation": lambda p: [rad(-.55) + 2 * S(p + 1), rad(.1), rad(-.1)]},
})
A["chiseled_bookshelf_leyendo"] = loop({
    "body": {"rotation": lambda p: [3 + .6 * S(p), 0, 0]},
    "head": {"rotation": lambda p: [15 + 1.5 * S(.5 * p), 4 * flip(p), 0]},
    "right_arm": {"rotation": lambda p: [rad(-1.05) + 1.2 * S(.5 * p) - 6 * flip(p), rad(-.42) + 24 * flip(p), rad(.05)]},
    "left_arm": {"rotation": lambda p: [rad(-1.05) + 1.2 * S(.5 * p + 1.4), rad(.42), rad(-.05)]},
})
# ===== mesa de encantamiento y faro: brazos alzados canalizando =====
A["enchanting"] = loop({
    "body": {"rotation": lambda p: [-2 + 1 * S(p), 0, 0]},
    "head": {"rotation": lambda p: [4 + 2 * S(.5 * p), 0, 0]},
    "right_arm": {"rotation": lambda p: [rad(-1.75) + rad(.06) * S(.5 * p), rad(-.32) + rad(.05) * S(.5 * p), rad(.18) + 4 * S(p)]},
    "left_arm": {"rotation": lambda p: [rad(-1.75) + rad(.06) * S(.5 * p + 1.4), rad(.32), rad(-.18) - 4 * S(p)]},
})

anims = {"annis_cine_" + k: v for k, v in A.items()}
json.dump({"format_version": "1.8.0", "animations": anims}, open(sys.argv[1], "w"), indent=1)
print(len(anims), "animaciones")
