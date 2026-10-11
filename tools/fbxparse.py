"""Lector minimo de FBX binario (v7100-7400): devuelve el arbol de nodos."""
import struct, zlib
import numpy as np

class Node:
    __slots__ = ('name', 'props', 'children')
    def __init__(self, name): self.name = name; self.props = []; self.children = []
    def get(self, name):
        for c in self.children:
            if c.name == name: return c
    def all(self, name): return [c for c in self.children if c.name == name]

def parse(path):
    d = open(path, 'rb').read()
    assert d[:20] == b'Kaydara FBX Binary  '
    ver = struct.unpack_from('<I', d, 23)[0]
    big = ver >= 7500
    pos = 27
    root = Node('root')
    def rec(p):
        if big: end, nprop, plen, nl = struct.unpack_from('<QQQB', d, p); p += 25
        else: end, nprop, plen, nl = struct.unpack_from('<IIIB', d, p); p += 13
        if end == 0: return None, p
        name = d[p:p + nl].decode('latin1'); p += nl
        n = Node(name)
        pe = p + plen
        for _ in range(nprop):
            t = chr(d[p]); p += 1
            if t == 'Y': n.props.append(struct.unpack_from('<h', d, p)[0]); p += 2
            elif t == 'C': n.props.append(d[p]); p += 1
            elif t == 'I': n.props.append(struct.unpack_from('<i', d, p)[0]); p += 4
            elif t == 'F': n.props.append(struct.unpack_from('<f', d, p)[0]); p += 4
            elif t == 'D': n.props.append(struct.unpack_from('<d', d, p)[0]); p += 8
            elif t == 'L': n.props.append(struct.unpack_from('<q', d, p)[0]); p += 8
            elif t in 'fdlib':
                ln, enc, cl = struct.unpack_from('<III', d, p); p += 12
                raw = d[p:p + cl]; p += cl
                if enc == 1: raw = zlib.decompress(raw)
                dt = {'f': '<f4', 'd': '<f8', 'l': '<i8', 'i': '<i4', 'b': 'u1'}[t]
                n.props.append(np.frombuffer(raw, dtype=dt, count=ln))
            elif t == 'S':
                ln = struct.unpack_from('<I', d, p)[0]; p += 4; n.props.append(d[p:p + ln].decode('latin1')); p += ln
            elif t == 'R':
                ln = struct.unpack_from('<I', d, p)[0]; p += 4; n.props.append(d[p:p + ln]); p += ln
            else: raise ValueError('prop ' + t)
        p = pe
        while p < end:
            c, p = rec(p)
            if c is None: break
            n.children.append(c)
        return n, end
    while pos < len(d) - 40:
        n, pos = rec(pos)
        if n is None: break
        root.children.append(n)
    return root

if __name__ == '__main__':
    import sys
    r = parse(sys.argv[1])
    def show(n, dep=0, maxd=3):
        pr = []
        for x in n.props[:3]:
            pr.append(repr(x)[:50] if not hasattr(x, 'shape') else f'arr{x.shape}')
        print('  ' * dep + n.name, pr)
        if dep < maxd:
            for c in n.children[:40]: show(c, dep + 1, maxd)
    for c in r.children: show(c, 0, 1)
