"""Sonidos propios de los jefes (sintetizados). Mono 44.1 kHz -> .ogg. Los rugidos y gruñidos base se copian de bestialago."""
import numpy as np, subprocess, os, shutil
from scipy import signal
from scipy.io import wavfile
SR = 44100
OUT = '/home/claude/mods/jefes/src/main/resources/assets/jefes/sounds'
rng = np.random.default_rng(23)
os.makedirs(OUT, exist_ok=True)


def bp(x, lo, hi, o=2):
    b, a = signal.butter(o, [lo / (SR / 2), hi / (SR / 2)], 'band'); return signal.lfilter(b, a, x)
def lp(x, f, o=2):
    b, a = signal.butter(o, f / (SR / 2), 'low'); return signal.lfilter(b, a, x)
def hp(x, f, o=2):
    b, a = signal.butter(o, f / (SR / 2), 'high'); return signal.lfilter(b, a, x)
def norm(x, p=0.92): return x / (np.abs(x).max() + 1e-9) * p
def save(name, x):
    x = norm(x); w = f'/tmp/jefes_{name}.wav'
    wavfile.write(w, SR, (x * 32767).astype(np.int16))
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', w, '-ac', '1', '-c:a', 'libvorbis', '-q:a', '5', f'{OUT}/{name}.ogg'], check=True)
    print(name, round(len(x) / SR, 2), 's')
def ex(n, k): return np.exp(-np.arange(n) / SR * k)


def step(seed):
    n = int(SR * 0.9); t = np.arange(n) / SR
    thud = np.sin(2 * np.pi * (48 + 40 * np.exp(-t * 14)) * t) * ex(n, 7)
    crunch = bp(rng.standard_normal(n), 200, 1800) * ex(n, 14) * 0.9
    wet = lp(rng.standard_normal(n), 700) * ex(n, 5) * np.clip(t * 20, 0, 1) * 0.7   # carne humeda
    claw = hp(rng.standard_normal(n), 2500) * ex(n, 40) * 0.25
    return thud * 1.2 + crunch + wet + claw


def whoosh():
    n = int(SR * 0.7); t = np.arange(n) / SR
    e = np.sin(np.pi * np.clip(t / 0.5, 0, 1)) ** 2 * (t < 0.5)
    x = rng.standard_normal(n)
    sweep = bp(x, 300, 1500) * e
    f = 400 + 1800 * np.clip(t / 0.45, 0, 1)
    y = np.zeros(n); z = np.zeros(2)
    for i in range(0, n, 512):
        seg = slice(i, min(n, i + 512)); fc = f[i] / (SR / 2)
        b, a = signal.butter(2, [max(0.005, fc * 0.6), min(0.95, fc * 1.4)], 'band')
        y[seg] = signal.lfilter(b, a, x[seg])
    return (y * 0.8 + sweep * 0.4) * e + 0.15 * lp(x, 200) * e


def hit():
    n = int(SR * 0.8); t = np.arange(n) / SR
    thud = np.sin(2 * np.pi * (60 + 90 * np.exp(-t * 25)) * t) * ex(n, 9)
    snap = bp(rng.standard_normal(n), 400, 3500) * ex(n, 28)
    return thud * 1.3 + snap * 0.7


def land():
    n = int(SR * 1.6); t = np.arange(n) / SR
    boom = np.sin(2 * np.pi * (36 + 50 * np.exp(-t * 6)) * t) * ex(n, 3.2)
    rub = lp(rng.standard_normal(n), 500) * ex(n, 4) * 0.8
    deb = hp(bp(rng.standard_normal(n), 800, 4000), 800) * ex(n, 8) * 0.3
    return boom * 1.4 + rub + deb


def sniff():
    n = int(SR * 1.8); t = np.arange(n) / SR
    e = np.sin(np.pi * np.clip(t / 1.4, 0, 1)) ** 1.5 * (t < 1.4)
    x = bp(rng.standard_normal(n), 400, 5000) * e * (1 + 0.5 * np.sin(2 * np.pi * 7 * t))
    return x + 0.3 * bp(rng.standard_normal(n), 100, 300) * e


def grab():
    n = int(SR * 0.9); t = np.arange(n) / SR
    crunch = bp(rng.standard_normal(n), 150, 2500) * ex(n, 8)
    for k in range(5):
        i = int(SR * (0.05 + k * 0.07)); crunch[i:i + 600] += rng.standard_normal(600) * 0.6 * np.hanning(600)
    thud = np.sin(2 * np.pi * (70 + 40 * np.exp(-t * 20)) * t) * ex(n, 11)
    return crunch + thud


def scream():
    n = int(SR * 2.4); t = np.arange(n) / SR
    f = 700 + 900 * np.clip(t / 0.15, 0, 1) - 300 * np.clip((t - 1.2) / 1.2, 0, 1)
    f = f * (1 + 0.03 * np.sin(2 * np.pi * 9 * t))
    ph = 2 * np.pi * np.cumsum(f) / SR
    y = sum(np.sin(k * ph) / k ** 0.8 for k in range(1, 20))
    y = np.tanh(y * 3.2) + 0.6 * bp(rng.standard_normal(n), 1500, 7000)
    y *= np.clip(t / 0.02, 0, 1) * np.clip((2.4 - t) / 0.5, 0, 1)
    low = np.sin(2 * np.pi * 55 * t) * ex(n, 1.5)
    return y * 0.9 + low * 0.6


def snarl():
    n = int(SR * 2.2); t = np.arange(n) / SR
    f = 74 * (1 + 0.1 * np.sin(2 * np.pi * 3.1 * t)) * (1 + 0.04 * lp(rng.standard_normal(n), 10) * 6)
    ph = 2 * np.pi * np.cumsum(f) / SR
    y = sum(np.sin(k * ph + 0.5 * k) / k ** 1.05 for k in range(1, 30))
    y *= 1 + 0.7 * np.sin(2 * np.pi * (f * 0.5) * t * 0 + np.cumsum(f * 0.5) * 2 * np.pi / SR)
    y = np.tanh(y * 2.2)
    y = bp(y, 70, 2000) + 0.5 * bp(rng.standard_normal(n), 200, 1800) * (0.3 + 0.7 * np.abs(np.sin(2 * np.pi * 6 * t)))
    return y * np.sin(np.pi * np.clip(t / 2.2, 0, 1)) ** 0.5


if __name__ == '__main__':
    src = '/home/claude/mods/bestialago/src/main/resources/assets/bestialago/sounds'
    for a, b in (('roar', 'roar'), ('roar2', 'roar2'), ('growl', 'growl'), ('bite', 'chomp')):
        shutil.copy(f'{src}/{a}.ogg', f'{OUT}/{b}.ogg')
    for i in range(3): save(f'step{i + 1}', step(i))
    save('attack', whoosh()); save('hit', hit()); save('land', land()); save('sniff', sniff())
    save('grab', grab()); save('scream', scream()); save('snarl', snarl())
