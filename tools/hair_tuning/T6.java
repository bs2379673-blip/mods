import com.tunombre.dancers.rig.*;
import com.google.gson.*;
import java.io.*; import java.nio.file.*; import java.util.*;
/** Serie temporal 30 fps en vista frontal ortografica (X derecha, Z arriba): lat, hang, ext, del pelo 髪6.L/R */
public class T6 { public static void main(String[] a) throws Exception {
 Rig3.Model3 m = Rig3.Model3.load(JsonParser.parseString(Files.readString(Path.of(a[0]))).getAsJsonObject());
 Rig3.Anim3 an = Rig3.Anim3.load(new FileInputStream(a[1]));
 double t0=Double.parseDouble(a[2]), t1=Double.parseDouble(a[3]);
 int nb=m.boneCount(); double[] R=new double[nb*9], T=new double[nb*3];
 Rig3.SpringState3 st=new Rig3.SpringState3(m);
 int sl=-1,sr=-1,head=-1; for(int s=0;s<m.sBone.length;s++){ String n=m.boneNames[m.sBone[s]]; if(n.equals("髪6.L")) sl=s; if(n.equals("髪6.R")) sr=s;} for(int i=0;i<nb;i++) if(m.boneNames[i].equals("頭")) head=i;
 PrintWriter w=new PrintWriter(a[4]); double dt=1.0/60;
 for(double t=t0;t<=t1;t+=1.0/30){ for(int k=0;k<2;k++) Rig3.pose(m,an,t+k*dt-dt,0,st,dt,R,T);   // 2 pasos de 1/60 por muestra
   double[][] tip=new double[2][3]; int[] ss={sl,sr};
   for(int q=0;q<2;q++){ int b=m.sBone[ss[q]]; double x=m.sTip[ss[q]*3],y=m.sTip[ss[q]*3+1],z=m.sTip[ss[q]*3+2];
     for(int c=0;c<3;c++) tip[q][c]=R[b*9+c*3]*x+R[b*9+c*3+1]*y+R[b*9+c*3+2]*z+T[b*3+c]; }
   double[] ht=new double[3]; double hx=m.pivot[head*3],hy=m.pivot[head*3+1],hz=m.pivot[head*3+2]+0.17;
   for(int c=0;c<3;c++) ht[c]=R[head*9+c*3]*hx+R[head*9+c*3+1]*hy+R[head*9+c*3+2]*hz+T[head*3+c];
   int low=tip[0][2]<tip[1][2]?0:1;
   double lat=tip[low][0]-ht[0], hang=ht[2]-tip[low][2];
   double xmin=Math.min(tip[0][0],tip[1][0]), xmax=Math.max(tip[0][0],tip[1][0]);
   w.printf("%.4f,%.5f,%.5f,%.5f,%.5f%n",t,lat/1.6,hang/1.6,(xmax-xmin+0.2)/1.6,ht[0]/1.6); }
 w.close(); }}
