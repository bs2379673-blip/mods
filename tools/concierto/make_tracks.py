"""Prepara el analisis de cada cancion para el mod concierto.
Entrada : audio/<nombre>.wav (22050 Hz mono) + audio/<nombre>.v2.json (golpes y estructura)
Salida  : <salida>/<nombre>.bin  =  'CTK2' | int32 frames | uint8 stride | frames*stride bytes (0..255)
Canales : 0-31 bandas (40 Hz..10 kHz, log) | 32 volumen | 33 graves | 34 medios | 35 agudos | 36 bombo | 37 caja | 38 acento
60 cuadros por segundo. El tiempo 0 es el inicio del archivo de audio."""
import sys, json, struct, os
import numpy as np
from scipy.io import wavfile
from scipy.signal import stft

FPS = 60
STRIDE = 39
NAMES = ["intro", "static", "kontonboogie", "melt", "magicalcure", "loopingtherooms", "anamanaguchi",
         "minnamikumiku", "mesmerizer", "triplebaka", "mikubeam"]


def analyze(src_dir, name):
    sr, x = wavfile.read(f"{src_dir}/{name}.wav")
    x = x.astype(np.float32)
    if x.ndim > 1: x = x.mean(1)
    x /= (np.abs(x).max() + 1e-9)
    N = 1024
    nf = int(len(x) / sr * FPS)
    centers = (np.arange(nf) + 0.5) / FPS * sr
    win = np.hanning(N).astype(np.float32)
    freqs = np.fft.rfftfreq(N, 1 / sr)
    edges = np.geomspace(40, 10000, 33)
    idx = []
    for b in range(32):
        k = np.where((freqs >= edges[b]) & (freqs < edges[b + 1]))[0]
        if len(k) == 0: k = np.array([np.argmin(abs(freqs - (edges[b] + edges[b + 1]) / 2))])
        idx.append(k)
    raw = np.zeros((nf, 32), np.float32); rms = np.zeros(nf, np.float32)
    pad = np.concatenate([np.zeros(N, np.float32), x, np.zeros(N, np.float32)])
    for f in range(nf):
        s0 = int(centers[f]) - N // 2 + N
        seg = pad[s0:s0 + N]
        m = np.abs(np.fft.rfft(seg * win)) / N
        for b in range(32): raw[f, b] = m[idx[b]].mean()
        rms[f] = np.sqrt((seg ** 2).mean())
    v = np.log10(1 + raw * 400.0)
    hi = np.percentile(v, 97, axis=0) + 1e-6; lo = np.percentile(v, 5, axis=0)
    s = np.clip((v - lo) / np.maximum(1e-6, hi - lo), 0, 1) ** 1.3
    lv = np.clip(rms / (np.percentile(rms, 97) + 1e-6), 0, 1)
    out = np.zeros((nf, STRIDE), np.float32)
    out[:, :32] = s
    out[:, 32] = lv
    out[:, 33] = s[:, :6].mean(1); out[:, 34] = s[:, 6:18].mean(1); out[:, 35] = s[:, 18:].mean(1)
    j = json.load(open(f"{src_dir}/{name}.v2.json"))
    for ch, key in ((36, "kicks"), (37, "snares"), (38, "accents")):
        arr = np.array([h[1] for h in j[key]]) if j[key] else np.array([1.0])
        ref = np.percentile(arr, 85) + 1e-6
        for t, st in j[key]:
            f = int(round(t * FPS))
            val = float(min(1.0, 0.35 + 0.65 * st / ref))
            for d, w in ((0, 1.0), (1, 0.55), (2, 0.25)):
                if 0 <= f + d < nf: out[f + d, ch] = max(out[f + d, ch], val * w)
    return out


def main(src_dir, dst_dir):
    os.makedirs(dst_dir, exist_ok=True)
    for n in NAMES:
        if not os.path.exists(f"{src_dir}/{n}.wav") or not os.path.exists(f"{src_dir}/{n}.v2.json"):
            print("falta", n); continue
        a = analyze(src_dir, n)
        q = np.clip(np.round(a * 255), 0, 255).astype(np.uint8)
        with open(f"{dst_dir}/{n}.bin", "wb") as f:
            f.write(b"CTK2"); f.write(struct.pack("<i", len(q))); f.write(bytes([STRIDE])); f.write(q.tobytes())
        print(n, len(q), "cuadros", round(len(q) / FPS, 1), "s", os.path.getsize(f"{dst_dir}/{n}.bin") // 1024, "KB")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
