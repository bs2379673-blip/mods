"""Vista previa FK de una animacion .dnc: dncprev.py <modelo.json> <anim.dnc> <fotograma> <pose_out.json>  (luego skinprev.py)"""
import sys, gzip, struct, json, numpy as np
sys.path.insert(0, __import__('os').path.dirname(__import__('os').path.abspath(__file__)))
def load_dnc(path):
    d = gzip.open(path).read(); assert d[:4] == b'DNC1'
    fps, n, nc, nv = struct.unpack_from('<HIHH', d, 4); o = 4 + 10
    const = {}; var = {}
    for _ in range(nc):
        b, = struct.unpack_from('<H', d, o); o += 2; const[b] = np.array(struct.unpack_from('<4f', d, o)); o += 16
    for _ in range(nv):
        b, = struct.unpack_from('<H', d, o); o += 2
        var[b] = np.frombuffer(d, '<i2', n * 4, o).reshape(n, 4) / 32767.0; o += n * 8
    rb, = struct.unpack_from('<H', d, o); o += 2
    root = np.frombuffer(d, '<f4', n * 3, o).reshape(n, 3); o += n * 12
    return fps, n, const, var, rb, root
def qm(q):
    x, y, z, w = q / np.linalg.norm(q)
    return np.array([[1-2*(y*y+z*z), 2*(x*y-w*z), 2*(x*z+w*y)], [2*(x*y+w*z), 1-2*(x*x+z*z), 2*(y*z-w*x)], [2*(x*z-w*y), 2*(y*z+w*x), 1-2*(x*x+y*y)]])
if __name__ == '__main__':
    m = json.load(open(sys.argv[1])); fps, n, const, var, rb, root = load_dnc(sys.argv[2]); f = int(sys.argv[3])
    B = m['bones']; nb = len(B); D = [None] * nb; P = [None] * nb; pv = [np.array(b['pv']) for b in B]
    for i, b in enumerate(B):
        if i in const: L = qm(const[i])
        elif i in var: L = qm(var[i][f])
        else: L = np.eye(3)
        p = b['p']
        if p < 0: D[i] = L; P[i] = pv[i].copy()
        else: D[i] = D[p] @ L; P[i] = P[p] + D[p] @ (pv[i] - pv[p])
        if i == rb: P[i] = P[i] + (D[p] @ root[f] if p >= 0 else root[f])
    out = {}
    for i, b in enumerate(B): out[b['n']] = list(D[i].reshape(9)) + list(P[i] - D[i] @ pv[i])
    json.dump(out, open(sys.argv[4], 'w'))
