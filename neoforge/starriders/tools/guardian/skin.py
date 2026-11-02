import numpy as np
from glb import load
def q2m(q):
    x,y,z,w=q
    return np.array([[1-2*(y*y+z*z),2*(x*y-z*w),2*(x*z+y*w)],[2*(x*y+z*w),1-2*(x*x+z*z),2*(y*z-x*w)],[2*(x*z-y*w),2*(y*z+x*w),1-2*(x*x+y*y)]])
def trs(t,r,s):
    M=np.eye(4); M[:3,:3]=q2m(r)*np.array(s); M[:3,3]=t; return M
class Model:
    def __init__(s,p):
        J,acc=load(p); s.J=J
        pr=J['meshes'][0]['primitives'][0]; A=pr['attributes']
        s.pos=acc(A['POSITION']).astype(float); s.nrm=acc(A['NORMAL']).astype(float); s.uv=acc(A['TEXCOORD_0']).astype(float)
        s.jo=acc(A['JOINTS_0']).astype(int); s.we=acc(A['WEIGHTS_0']).astype(float)
        s.idx=acc(pr['indices']).ravel().reshape(-1,3)
        sk=J['skins'][0]; s.joints=sk['joints']; s.ibm=acc(sk['inverseBindMatrices']).reshape(-1,4,4).transpose(0,2,1)
        N=J['nodes']; s.N=N; s.parent={}
        for i,n in enumerate(N):
            for c in n.get('children',[]): s.parent[c]=i
        s.base=[(np.array(n.get('translation',[0,0,0]),float),np.array(n.get('rotation',[0,0,0,1]),float),np.array(n.get('scale',[1,1,1]),float)) for n in N]
        an=J['animations'][0]; s.times=acc(an['samplers'][0]['input']).ravel(); s.ch={}
        for c in an['channels']:
            sm=an['samplers'][c['sampler']]; s.ch[(c['target']['node'],c['target']['path'])]=(acc(sm['input']).ravel(),acc(sm['output']).astype(float))
        s.nf=len(s.times)
    def local(s,i,f):
        t,r,sc=s.base[i]
        def g(p,d):
            c=s.ch.get((i,p))
            if c is None: return d
            ti,o=c
            if len(ti)==s.nf: return o[f]
            k=min(len(ti)-1,np.searchsorted(ti,s.times[f])); return o[k]
        t=g('translation',s.base[i][0]); r=g('rotation',s.base[i][1]); sc=g('scale',s.base[i][2])
        return t,r,sc
    def world(s,f):
        W={}
        def w(i):
            if i in W: return W[i]
            M=trs(*s.local(i,f)); 
            if i in s.parent: M=w(s.parent[i])@M
            W[i]=M; return M
        for i in range(len(s.N)): w(i)
        return W
    def skin(s,f):
        W=s.world(f)
        JM=np.array([W[j]@s.ibm[k] for k,j in enumerate(s.joints)])
        P=np.c_[s.pos,np.ones(len(s.pos))]
        out=np.zeros((len(P),3))
        for k in range(4):
            out+=s.we[:,k:k+1]*np.einsum('nij,nj->ni',JM[s.jo[:,k]],P)[:,:3]
        return out
