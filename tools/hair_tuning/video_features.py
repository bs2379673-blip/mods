import subprocess, numpy as np, sys, json
F=sys.argv[1]; W,H=640,360
p=subprocess.Popen(['ffmpeg','-v','error','-i',F,'-f','rawvideo','-pix_fmt','rgb24','-'],stdout=subprocess.PIPE,bufsize=10**8)
fs=W*H*3; rows=[]; n=0
while True:
    b=p.stdout.read(fs)
    if len(b)<fs: break
    im=np.frombuffer(b,np.uint8).reshape(H,W,3).astype(np.int16)
    r,g,bl=im[...,0],im[...,1],im[...,2]
    fig=(im.max(2)>28)
    teal=(g-r>45)&(bl-r>35)&(g>80)
    ys,xs=np.where(fig)
    if len(ys)<50: rows.append([n]+[np.nan]*12); n+=1; continue
    fy0,fy1,fx0,fx1=ys.min(),ys.max(),xs.min(),xs.max()
    ty,tx=np.where(teal)
    if len(ty)<30: rows.append([n,fx0,fx1,fy0,fy1]+[np.nan]*8); n+=1; continue
    ytop=ty.min(); ylow=ty.max()
    top=ty<ytop+0.25*(ylow-ytop)
    hx=tx[top].mean()
    # punta: media de x de los pixeles teal mas bajos (5%)
    low=ty>ylow-3
    lx=tx[low].mean()
    rows.append([n,fx0,fx1,fy0,fy1,ytop,ylow,hx,lx,tx.min(),tx.max(),len(ty)])
    n+=1
np.save('feat.npy',np.array(rows,float)); print(n)
