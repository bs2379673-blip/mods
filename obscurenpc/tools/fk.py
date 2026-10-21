import json, numpy as np
import os
BASE=os.environ.get('EF_ANIM','/tmp/efj/assets/epicfight/animmodels/')
arm=json.load(open(BASE+'entity/biped.json'))['armature']['hierarchy']
parent={}
def walk(j,p=None):
    parent[j['name']]=p
    for c in j.get('children',[]): walk(c,j['name'])
for h in arm: walk(h)
order=list(parent.keys())
def load(path):
    d=json.load(open(path)); return {j['name']:(j['time'],[np.array(t).reshape(4,4) for t in j['transform']]) for j in d['animation']}
def pose_at(anim,t):
    out={}
    for n,(ts,ms) in anim.items():
        i=max(k for k in range(len(ts)) if ts[k]<=t) if t>=ts[0] else 0
        out[n]=ms[i]
    return out
def fk(pose):
    w={}
    for n in order:
        p=parent[n]
        w[n]=(w[p] if p else np.eye(4))@pose[n]
    return w
def pos(w,n): return w[n][:3,3]
