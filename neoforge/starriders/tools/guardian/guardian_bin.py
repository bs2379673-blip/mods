# glb (Tree Heretic Keeper) -> guardian.bin para StarRiders (big endian, lo lee DataInputStream)
import struct, numpy as np, sys
from skin import Model
m=Model('../tk/Treehereti-ckeeper.glb')
N=m.N
for p in ('translation','rotation','scale'): m.ch.pop((61,p),None)
arm=[i for i,n in enumerate(N) if n['name']=='Armature'][0]
need=set()
for j in m.joints:
    k=j
    while k is not None and k!=arm and k not in need:
        need.add(k); k=m.parent.get(k)
need.add(arm)
order=[]
def visit(i):
    if i in order: return
    if i in m.parent and m.parent[i] in need: visit(m.parent[i])
    order.append(i)
for i in sorted(need): visit(i)
idx={n:k for k,n in enumerate(order)}
STEP=2; FR=list(range(0,183,STEP))  # ciclo de caminata, 30 fps
out=bytearray()
def w(fmt,*a): out.extend(struct.pack('>'+fmt,*a))
w('i',len(order)); w('i',len(FR)); w('f',60/STEP)
for i in order:
    nm=N[i]['name'].encode(); w('h',len(nm)); out.extend(nm)
    par=m.parent.get(i); w('i', idx[par] if par in idx else -1)
    t,r,s=m.base[i]; w('3f',*t); w('4f',*r); w('3f',*s)
    anim = any((i,p) in m.ch for p in ('translation','rotation'))
    if anim:
        T=[];R=[]
        for f in FR:
            tt,rr,ss=m.local(i,f); T.append(tt); R.append(np.array(rr)/np.linalg.norm(rr))
        R=np.array(R)
        for k in range(1,len(R)):
            if np.dot(R[k],R[k-1])<0: R[k]=-R[k]
        T=np.array(T)
        if np.ptp(T,0).max()<1e-4 and np.ptp(R,0).max()<1e-5: anim=False
    w('b',1 if anim else 0)
    if anim:
        for k in range(len(FR)): w('3f',*T[k]); w('4f',*R[k])
w('i',len(m.joints))
for k,j in enumerate(m.joints):
    w('i',idx[j]); w('16f',*m.ibm[k].ravel())
V=len(m.pos); w('i',V)
for v in range(V):
    w('3f',*m.pos[v]); w('3f',*m.nrm[v]); w('2f',*m.uv[v])
    ww=m.we[v]; ww=ww/max(1e-6,ww.sum())
    w('4b',*[int(x) for x in m.jo[v]]); w('4f',*ww)
I=m.idx.ravel(); w('i',len(I)); out.extend(np.asarray(I,'>u2').tobytes())
open('guardian.bin','wb').write(bytes(out))
print(len(order),'huesos',len(m.joints),'joints',V,'verts',len(I)//3,'tris',len(out)//1024,'KB', max(m.jo.ravel()))
print([N[i]['name'] for i in order][:12])
