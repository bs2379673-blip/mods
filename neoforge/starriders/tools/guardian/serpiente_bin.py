# Snake.glb ("Snake" de Vasian-Digital3D, CC BY 4.0) -> serpiente.bin para StarRiders.
# Mismo formato que guardian.bin (sin animacion: la mueve el codigo) + grupos por textura al final.
import struct, numpy as np, sys
from skin import Model
m=Model(sys.argv[1] if len(sys.argv)>1 else 'Snake.glb')
N=m.N
arm=[i for i,n in enumerate(N) if n['name']=='Armature'][0]
need=set([arm])
for j in m.joints:
    k=j
    while k is not None and k not in need:
        need.add(k); k=m.parent.get(k)
order=[]
def visit(i):
    if i in order: return
    if i in m.parent and m.parent[i] in need: visit(m.parent[i])
    order.append(i)
for i in sorted(need): visit(i)
idx={n:k for k,n in enumerate(order)}
out=bytearray()
def w(fmt,*a): out.extend(struct.pack('>'+fmt,*a))
w('i',len(order)); w('i',1); w('f',20.0)
for i in order:
    nm=N[i]['name'].encode(); w('h',len(nm)); out.extend(nm)
    par=m.parent.get(i); w('i', idx[par] if par in idx else -1)
    t,r,s=m.base[i]; w('3f',*t); w('4f',*r); w('3f',*s); w('b',0)
w('i',len(m.joints))
for k,j in enumerate(m.joints):
    w('i',idx[j]); w('16f',*m.ibm[k].ravel())
# triangulos ordenados por textura
order_t=np.argsort(m.mat,kind='stable'); tri=m.idx[order_t]; mats=m.mat[order_t]
V=len(m.pos); w('i',V)
for v in range(V):
    w('3f',*m.pos[v]); w('3f',*m.nrm[v]); w('2f',*m.uv[v])
    ww=m.we[v]; ww=ww/max(1e-6,ww.sum())
    w('4b',*[int(x) for x in m.jo[v]]); w('4f',*ww)
I=tri.ravel(); w('i',len(I)); out.extend(np.asarray(I,'>u2').tobytes())
G=int(mats.max())+1; w('i',G)
for g in range(G):
    sel=np.where(mats==g)[0]; w('i',int(sel.min())*3 if len(sel) else 0); w('i',len(sel)*3)
open('serpiente.bin','wb').write(bytes(out))
print(len(order),'huesos',V,'verts',len(I)//3,'tris',G,'grupos',len(out)//1024,'KB', 'max vert', V)
