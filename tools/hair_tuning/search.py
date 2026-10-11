import json, subprocess, numpy as np, random, sys, os, concurrent.futures as cf
B='/home/claude/mods/dancers/src/main/resources/assets/dancers'
m0=json.load(open(B+'/dancer_models/mikupd.json')); names=[b['n'] for b in m0['bones']]
J='/opt/gradle-8.14.3/lib/gson-2.10.jar'
def stats(csv):
    s=np.loadtxt(csv,delimiter=','); lat,hang,ext=s[:,1],s[:,2],s[:,3]
    L=np.percentile(hang,50); x=lat/L; x=x-x.mean()
    X=np.abs(np.fft.rfft(x))**2; f=np.fft.rfftfreq(len(x),1/30); tot=X[1:].sum()
    ac=np.correlate(x,x,'full')[len(x)-1:]; ac/=ac[0]
    return dict(std=x.std(), p95=np.percentile(np.abs(x),95), low=X[(f>0.02)&(f<0.5)].sum()/tot, hi=X[f>=3].sum()/tot, ac1=ac[3], ac3=ac[9], hp5=np.percentile(hang/L,5), L=L)
TARGET=dict(std=0.148,p95=0.30,low=0.39,hi=0.19,ac1=0.63,ac3=0.28,hp5=0.707)
W=dict(std=3,p95=2,low=2,hi=3,ac1=3,ac3=2,hp5=3)
def score(st): return sum(W[k]*((st[k]-TARGET[k])/TARGET[k])**2 for k in TARGET)
def run(p,anim='melt',t1=200):
    k,c,g,ma,mt=p; tag='_'.join(map(str,p))+anim
    mm=json.loads(json.dumps(m0))
    for s in mm['springs']:
        if names[s['bone']].startswith('髪'): s.update(stiffness=k,damping=c,gravity=g,max_angle=ma,max_total=mt)
    mp=f'/tmp/claude-0/jt/s_{tag}.json'; json.dump(mm,open(mp,'w'),ensure_ascii=False)
    out=f'/tmp/claude-0/jt/s_{tag}.csv'
    subprocess.run(['java','-Dsun.jnu.encoding=UTF-8','-cp',f'{J}:cls4','T6',mp,f'{B}/dancer_anims/mikupd/{anim}.dnc','10',str(t1),out],capture_output=True,cwd='/tmp/claude-0/jt')
    st=stats(out); os.remove(mp); os.remove(out); return p,st,score(st)
if __name__=='__main__':
    random.seed(1)
    cands=[(random.choice([2,4,8,15,30,60]),random.choice([1,2,3,5,8]),random.choice([6,9.8]),random.choice([8,12,18,25]),random.choice([35,50,70,90])) for _ in range(60)]
    with cf.ThreadPoolExecutor(4) as ex: res=list(ex.map(run,cands))
    res.sort(key=lambda r:r[2])
    for p,st,sc in res[:8]: print(p,'score %.2f'%sc,{k:round(float(v),2) for k,v in st.items()})
    json.dump([(p,sc) for p,_,sc in res],open('search1.json','w'))
