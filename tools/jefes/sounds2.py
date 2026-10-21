"""Sonidos de los jefes: capas sintetizadas + los gruñidos/voces recortados del Skinwalker (growl_N / voice_N, no se tocan).
Pasos, golpes, rugidos y screamer se construyen en capas (golpe grave, carne humeda, grava, reverb de sala) para que no suenen a un solo oscilador."""
import numpy as np, subprocess, os, glob, json
from scipy import signal
from scipy.io import wavfile
SR = 44100
OUT = '/home/claude/mods/jefes/src/main/resources/assets/jefes/sounds'
rng = np.random.default_rng(77)

def bp(x, lo, hi, o=2):
    s = signal.butter(o, [lo / (SR / 2), min(hi, SR / 2 - 100) / (SR / 2)], 'band', output='sos'); return signal.sosfilt(s, x)
def lp(x, f, o=2):
    s = signal.butter(o, f / (SR / 2), 'low', output='sos'); return signal.sosfilt(s, x)
def hp(x, f, o=2):
    s = signal.butter(o, f / (SR / 2), 'high', output='sos'); return signal.sosfilt(s, x)
def ex(n, k): return np.exp(-np.arange(n) / SR * k)
def noise(n): return rng.standard_normal(n)
def pad(x, n):
    return np.pad(x, (0, max(0, n - len(x))))[:n]
def mix(n, *layers):
    o = np.zeros(n)
    for off, x, g in layers:
        i = int(off * SR)
        if i >= n: continue
        seg = x[:n - i]; o[i:i + len(seg)] += seg * g
    return o
def reverb(x, rt=1.2, wet=0.25, tail=1.0):
    n = int(SR * rt); ir = noise(n) * ex(n, 5.5 / rt); ir = lp(ir, 3500) * np.clip(np.arange(n) / (SR * 0.01), 0, 1)
    ir /= np.abs(ir).sum() / 8
    y = signal.fftconvolve(np.pad(x, (0, int(SR * tail))), ir)[:len(x) + int(SR * tail)]
    d = np.pad(x, (0, len(y) - len(x)))
    return d * (1 - wet) + y * wet
def sat(x, d): return np.tanh(x * d) / np.tanh(d)
def pitch(x, r):
    n = int(len(x) / r); return np.interp(np.arange(n) * r, np.arange(len(x)), x)
def norm(x, p=0.92): return x / (np.abs(x).max() + 1e-9) * p
def fade(x, a=0.004, b=0.03):
    x = x.copy(); na, nb = int(a * SR), int(b * SR); x[:na] *= np.linspace(0, 1, na); x[-nb:] *= np.linspace(1, 0, nb); return x
def sweep(n, f0, f1, k):
    t = np.arange(n) / SR; f = f1 + (f0 - f1) * np.exp(-t * k); return np.sin(2 * np.pi * np.cumsum(f) / SR)
def save(name, x):
    x = fade(norm(x)); w = f'/tmp/jefes_{name}.wav'
    wavfile.write(w, SR, (x * 32767).astype(np.int16))
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', w, '-ac', '1', '-c:a', 'libvorbis', '-q:a', '5', f'{OUT}/{name}.ogg'], check=True)
def load(name):
    r = subprocess.run(['ffmpeg', '-loglevel', 'error', '-i', f'{OUT}/{name}.ogg', '-f', 'f32le', '-ac', '1', '-ar', str(SR), '-'], capture_output=True, check=True)
    return np.frombuffer(r.stdout, dtype=np.float32).astype(np.float64)

growls = [load(f'growl_{i}') for i in range(1, 13)]
voices = [load(f'voice_{i}') for i in range(1, 10)]
cent = lambda x: (lambda f, P: (f * P).sum() / P.sum())(*signal.welch(x, SR, nperseg=2048))
low = sorted(range(12), key=lambda i: cent(growls[i]))[:6]

# viejos
for f in glob.glob(f'{OUT}/*.ogg'):
    b = os.path.basename(f)[:-4]
    if not (b.startswith('growl_') or b.startswith('voice_')): os.remove(f)

def step(i):
    n = int(SR * 1.1)
    f0 = rng.uniform(70, 105)
    thud = sweep(n, f0, 32, 16) * ex(n, 8.5)
    body = lp(noise(n), rng.uniform(260, 380)) * ex(n, 11) * 1.4
    crunch = bp(noise(n), 350, 2600) * ex(n, 28) * 0.55
    grit = np.zeros(n)
    for _ in range(rng.integers(5, 11)):
        o = rng.integers(int(SR * 0.01), int(SR * 0.22)); L = int(SR * rng.uniform(0.004, 0.012)); g = hp(noise(L), 900) * np.hanning(L); grit[o:o + L] += g * rng.uniform(0.2, 0.5)
    toe = np.roll(lp(noise(n), 900) * ex(n, 30), int(SR * rng.uniform(0.07, 0.12))) * 0.4
    x = thud + body + crunch + grit + toe
    return reverb(x, 1.4, 0.2, 0.6)
for i in range(1, 5): save(f'step{i}', step(i))

def whoosh(n_s, f0, f1, g=1.0):
    n = int(SR * n_s); out = np.zeros(n); seg = 24
    for k in range(seg):
        a, b = int(n * k / seg), int(n * (k + 1) / seg); f = f0 + (f1 - f0) * k / seg
        out[a:b] = bp(noise(b - a + 600), f * 0.6, f * 1.6)[300:300 + b - a] * 1
    env = np.sin(np.pi * np.clip(np.arange(n) / n, 0, 1)) ** 1.6
    return out * env * g
for i in range(1, 4):
    w = whoosh(0.55, rng.uniform(280, 380), rng.uniform(1100, 1700), 1.2)
    lay = [(0, w, 1)]
    if i == 3:
        g = pitch(growls[low[i]], 0.85); lay.append((0.04, g[:int(SR * 0.5)] * 0.9, 1))
    save(f'attack{i}', reverb(mix(int(SR * 0.9), *lay), 0.8, 0.12, 0.3))

for i in range(1, 4):
    n = int(SR * 0.8)
    thud = sweep(n, rng.uniform(130, 170), 44, 20) * ex(n, 13)
    slap = bp(noise(n), 500, 3800) * ex(n, 40) * 0.8
    crack = hp(noise(n), 2500) * ex(n, 120) * 0.35
    wet = lp(noise(n), 500) * ex(n, 10) * 0.7
    save(f'hit{i}', reverb(thud + slap + crack + wet, 1.0, 0.15, 0.4))

n = int(SR * 2.6)
boom = sweep(n, 70, 26, 9) * ex(n, 3.6); rum = lp(noise(n), 160) * ex(n, 3.0) * 2.0; deb = bp(noise(n), 400, 3000) * ex(n, 14) * 0.5
save('land', reverb(boom + rum + deb, 2.2, 0.35, 1.0))

for i in range(1, 3):
    n = int(SR * 1.5); out = np.zeros(n)
    for k, (a, b) in enumerate([(0.0, 0.5), (0.65, 1.2)]):
        L = int(SR * (b - a)); env = np.sin(np.pi * np.linspace(0, 1, L)) ** 1.3
        s = bp(noise(L), 500, 4200) * env * (1 + 0.5 * np.sin(2 * np.pi * rng.uniform(25, 40) * np.arange(L) / SR))
        s += lp(noise(L), 300) * env * 0.5
        out[int(a * SR):int(a * SR) + L] += s * (0.7 if k == 0 else 1)
    save(f'sniff{i}', reverb(out, 0.9, 0.12, 0.3))

n = int(SR * 1.4)
creak = np.sin(2 * np.pi * np.cumsum(120 + 30 * np.sin(2 * np.pi * 5 * np.arange(n) / SR) + 80 * np.arange(n) / n) / SR) * np.clip(np.arange(n) / (SR * 0.3), 0, 1) * ex(n, 1.6)
squeeze = lp(noise(n), 700) * np.sin(np.pi * np.arange(n) / n) ** 1.5
tug = sweep(n, 110, 40, 20) * ex(n, 9)
g = pitch(growls[low[0]], 0.6)
save('grab', reverb(mix(n, (0, creak * 0.25 + squeeze * 0.8 + tug, 1), (0.1, g[:int(SR * 1.2)], 0.7)), 1.2, 0.2, 0.5))

n = int(SR * 2.0); out = np.zeros(n)
for off in [0.0, 0.06, 0.13, 0.2, 0.29, 0.4]:
    L = int(SR * 0.09); out[int(off * SR):int(off * SR) + L] += bp(noise(L), 700, 4500) * ex(L, 40) * rng.uniform(0.6, 1.0)
for off in [0.02, 0.17]:
    L = int(SR * 0.05); out[int(off * SR):int(off * SR) + L] += hp(noise(L), 3000) * ex(L, 90) * 0.7
thud = sweep(n, 90, 34, 14) * ex(n, 6) * 1.4
g = pitch(growls[low[1]], 0.55)
save('chomp', reverb(mix(n, (0, out + thud, 1), (0.05, g[:int(SR * 1.6)], 0.6)), 1.8, 0.3, 0.8))

def roar(k):
    n = int(SR * (3.1 + 0.3 * k)); src = [growls[low[(k + j) % 6]] for j in range(3)]
    lay = []
    for j, s in enumerate(src):
        r = [0.72, 0.62, 0.95][j] * rng.uniform(0.95, 1.05); y = pitch(s, r)
        lay.append((j * 0.12, sat(y, 2.5) * [1.0, 0.9, 0.5][j], 1))
    t = np.arange(n) / SR
    sub = np.sin(2 * np.pi * (52 + 8 * np.sin(2 * np.pi * 6.5 * t)) * t) * (1 + 0.4 * np.sin(2 * np.pi * 13 * t)) * np.clip(t / 0.25, 0, 1) * ex(n, 0.9) * 0.7
    hiss = hp(noise(n), 2500) * np.clip(t / 0.2, 0, 1) * ex(n, 1.5) * 0.12
    x = mix(n, *lay) + sub + hiss
    return reverb(sat(x, 1.8), 2.4, 0.3, 1.0)
for k in range(3): save(f'roar{k + 1}', roar(k))

best = sorted(range(9), key=lambda i: -cent(voices[i]))[:2]
for k in range(2):
    v = voices[best[k]]; n = int(SR * 2.8)
    a = sat(pitch(v, 1.2), 5)[:n]; b = sat(pitch(v, 1.55), 6)[:n]; c = pitch(growls[low[k]], 0.7)
    t = np.arange(n) / SR
    air = bp(noise(n), 2200, 7000) * ex(n, 1.3) * np.clip(t / 0.03, 0, 1) * 0.35
    save(f'scream{k + 1}', reverb(mix(n, (0, a, 1), (0.02, b, 0.8), (0, c, 0.7), (0, air, 1)), 1.6, 0.22, 0.8))

def ev(name, files, dist):
    return {"subtitle": f"jefes.subtitle.{name}", "sounds": [{"name": f"jefes:{f}", "attenuation_distance": dist} for f in files]}
sj = {
    "step": ev("step", [f"step{i}" for i in range(1, 5)], 96),
    "roar": ev("roar", [f"roar{i}" for i in range(1, 4)], 160),
    "growl": ev("growl", [f"growl_{i}" for i in range(1, 13)], 72),
    "voice": ev("voice", [f"voice_{i}" for i in range(1, 10)], 56),
    "attack": ev("attack", [f"attack{i}" for i in range(1, 4)], 64),
    "hit": ev("hit", [f"hit{i}" for i in range(1, 4)], 64),
    "land": ev("land", ["land"], 128),
    "sniff": ev("sniff", ["sniff1", "sniff2"], 64),
    "grab": ev("grab", ["grab"], 64),
    "chomp": ev("chomp", ["chomp"], 64),
    "scream": ev("scream", ["scream1", "scream2"], 48),
}
json.dump(sj, open('/home/claude/mods/jefes/src/main/resources/assets/jefes/sounds.json', 'w'), indent=2)
print(sorted(os.listdir(OUT)))
