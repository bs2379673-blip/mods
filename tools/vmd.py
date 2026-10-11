"""Lector de VMD (Vocaloid Motion Data). parse(path) -> dict(model, bones{name:[(frame,pos,quat)]}, morphs{name:[(frame,w)]})
Cuaternion en orden (x,y,z,w). Coordenadas MMD tal cual (izquierda, Y arriba)."""
import struct, collections

def sj(b):
    b = b.split(b'\x00')[0]
    try: return b.decode('shift_jis')
    except Exception: return b.decode('shift_jis', 'replace')

def parse(path):
    d = open(path, 'rb').read()
    hdr = d[:30]
    off = 30
    model = sj(d[off:off + 20]); off += 20
    n, = struct.unpack_from('<I', d, off); off += 4
    bones = collections.defaultdict(list)
    rec = struct.Struct('<15sI3f4f64s')
    for _ in range(n):
        nm, fr, px, py, pz, qx, qy, qz, qw, _i = rec.unpack_from(d, off); off += rec.size
        bones[sj(nm)].append((fr, (px, py, pz), (qx, qy, qz, qw)))
    morphs = collections.defaultdict(list)
    if off + 4 <= len(d):
        m, = struct.unpack_from('<I', d, off); off += 4
        r2 = struct.Struct('<15sIf')
        for _ in range(m):
            nm, fr, w = r2.unpack_from(d, off); off += r2.size
            morphs[sj(nm)].append((fr, w))
    for k in bones: bones[k].sort(key=lambda t: t[0])
    for k in morphs: morphs[k].sort(key=lambda t: t[0])
    return {'header': hdr, 'model': model, 'bones': dict(bones), 'morphs': dict(morphs), 'count': n}

if __name__ == '__main__':
    import sys
    v = parse(sys.argv[1])
    print(v['header'][:25], v['model'], v['count'], 'bones', len(v['bones']), 'morphs', len(v['morphs']))
    mx = max([f for b in v['bones'].values() for f, _, _ in b] + [f for b in v['morphs'].values() for f, _ in b] + [0])
    print('frames', mx, 'secs', mx / 30)
    for k, b in sorted(v['bones'].items(), key=lambda t: -len(t[1])): print(' ', k, len(b))
    for k, b in sorted(v['morphs'].items(), key=lambda t: -len(t[1])): print(' M', k, len(b))
