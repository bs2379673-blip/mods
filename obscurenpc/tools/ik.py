import sys; sys.path.insert(0, __import__('os').path.dirname(__import__('os').path.abspath(__file__)))
from fk import *
from scipy.optimize import least_squares
idle=load(BASE+'animations/biped/living/idle.json'); BASE_POSE=pose_at(idle,0)
def rot(axis,deg):
    a=np.radians(deg); c,s=np.cos(a),np.sin(a); R=np.eye(4)
    if axis=='x': R[1,1]=c;R[1,2]=-s;R[2,1]=s;R[2,2]=c
    if axis=='y': R[0,0]=c;R[0,2]=s;R[2,0]=-s;R[2,2]=c
    if axis=='z': R[0,0]=c;R[0,1]=-s;R[1,0]=s;R[1,1]=c
    return R
def R3(x,y,z): return rot('x',x)@rot('y',y)@rot('z',z)
def apply(angles, side, pose=None):
    p=dict(BASE_POSE if pose is None else pose)
    ax,ay,az,hx=angles
    p['Arm_'+side]=BASE_POSE['Arm_'+side]@R3(ax,ay,az)
    p['Hand_'+side]=BASE_POSE['Hand_'+side]@rot('x',hx)
    return p
W0=fk(BASE_POSE)
def solve(target, side, guess=(30,0,0,30)):
    t=np.array(target,float)
    if side=='L': pass
    def res(a):
        w=fk(apply(a,side)); d=pos(w,'Tool_'+side)-t
        # elbow position comfort + regularize
        return np.concatenate([d*10, [0.01*a[1], 0.004*a[0], 0.004*a[2]]])
    r=least_squares(res, guess, bounds=([-170,-90,-170,0],[170,90,170,150]))
    return tuple(r.x), np.linalg.norm(pos(fk(apply(r.x,side)),'Tool_'+side)-t)
