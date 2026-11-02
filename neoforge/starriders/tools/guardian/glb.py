import json, struct, numpy as np
CT={5126:np.float32,5123:np.uint16,5125:np.uint32,5121:np.uint8,5122:np.int16}
NC={"SCALAR":1,"VEC2":2,"VEC3":3,"VEC4":4,"MAT4":16}
def load(p):
    b=open(p,'rb').read()
    jl=struct.unpack('<I',b[12:16])[0]; J=json.loads(b[20:20+jl]); bin_=b[20+jl+8:]
    def acc(i):
        a=J['accessors'][i]; v=J['bufferViews'][a['bufferView']]
        off=v.get('byteOffset',0)+a.get('byteOffset',0); n=NC[a['type']]; dt=CT[a['componentType']]
        cnt=a['count']; st=v.get('byteStride',0); isz=np.dtype(dt).itemsize*n
        if st and st!=isz:
            raw=np.frombuffer(bin_,np.uint8,cnt*st,off).reshape(cnt,st)[:,:isz].copy()
            arr=raw.view(dt).reshape(cnt,n)
        else: arr=np.frombuffer(bin_,dt,cnt*n,off).reshape(cnt,n)
        return arr
    return J,acc
