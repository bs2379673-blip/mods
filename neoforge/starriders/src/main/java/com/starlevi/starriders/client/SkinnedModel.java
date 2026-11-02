package com.starlevi.starriders.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Modelo con esqueleto (piel deformada por huesos), leido de un .bin propio que se arma con
 * tools/guardian_bin.py a partir de un .glb. Trae una animacion en bucle (caminar); el resto de los
 * movimientos se suman encima por codigo (rotaciones extra por hueso).
 */
public final class SkinnedModel {
    public final int bones, frames;
    public final float fps;
    final String[] names;
    final int[] parent;
    final Vector3f[] baseT, baseS;
    final Quaternionf[] baseR;
    final float[][] animT, animR;
    final int[] jointBone;
    final Matrix4f[] ibm;
    final float[] pos, nrm, uv, we;
    final byte[] jo;
    final int[] idx;
    private final Map<String, Integer> byName = new HashMap<>();

    /** Pose actual (local por hueso): se llena con sample() y se le suman rotaciones. */
    public final Quaternionf[] rot;
    public final Vector3f[] tr;
    private final Quaternionf[] tmpR;
    private final Vector3f[] tmpT;
    private final Matrix4f[] world, skin;
    private final float[] sp, sn;
    private final Matrix4f m4 = new Matrix4f();

    private SkinnedModel(DataInputStream in) throws IOException {
        this.bones = in.readInt();
        this.frames = in.readInt();
        this.fps = in.readFloat();
        this.names = new String[this.bones];
        this.parent = new int[this.bones];
        this.baseT = new Vector3f[this.bones];
        this.baseR = new Quaternionf[this.bones];
        this.baseS = new Vector3f[this.bones];
        this.animT = new float[this.bones][];
        this.animR = new float[this.bones][];
        for (int b = 0; b < this.bones; b++) {
            byte[] nm = new byte[in.readShort()];
            in.readFully(nm);
            this.names[b] = new String(nm, java.nio.charset.StandardCharsets.UTF_8);
            this.byName.put(this.names[b], b);
            this.parent[b] = in.readInt();
            this.baseT[b] = new Vector3f(in.readFloat(), in.readFloat(), in.readFloat());
            this.baseR[b] = new Quaternionf(in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat());
            this.baseS[b] = new Vector3f(in.readFloat(), in.readFloat(), in.readFloat());
            if (in.readByte() != 0) {
                float[] t = new float[this.frames * 3], r = new float[this.frames * 4];
                for (int f = 0; f < this.frames; f++) {
                    for (int k = 0; k < 3; k++) {
                        t[f * 3 + k] = in.readFloat();
                    }
                    for (int k = 0; k < 4; k++) {
                        r[f * 4 + k] = in.readFloat();
                    }
                }
                this.animT[b] = t;
                this.animR[b] = r;
            }
        }
        int nj = in.readInt();
        this.jointBone = new int[nj];
        this.ibm = new Matrix4f[nj];
        for (int j = 0; j < nj; j++) {
            this.jointBone[j] = in.readInt();
            float[] m = new float[16];
            for (int k = 0; k < 16; k++) {
                m[k] = in.readFloat();
            }
            // el .bin guarda la matriz por filas
            this.ibm[j] = new Matrix4f().set(m).transpose();
        }
        int nv = in.readInt();
        this.pos = new float[nv * 3];
        this.nrm = new float[nv * 3];
        this.uv = new float[nv * 2];
        this.jo = new byte[nv * 4];
        this.we = new float[nv * 4];
        for (int v = 0; v < nv; v++) {
            for (int k = 0; k < 3; k++) {
                this.pos[v * 3 + k] = in.readFloat();
            }
            for (int k = 0; k < 3; k++) {
                this.nrm[v * 3 + k] = in.readFloat();
            }
            this.uv[v * 2] = in.readFloat();
            this.uv[v * 2 + 1] = in.readFloat();
            in.readFully(this.jo, v * 4, 4);
            for (int k = 0; k < 4; k++) {
                this.we[v * 4 + k] = in.readFloat();
            }
        }
        int ni = in.readInt();
        this.idx = new int[ni];
        for (int i = 0; i < ni; i++) {
            this.idx[i] = in.readUnsignedShort();
        }
        this.rot = new Quaternionf[this.bones];
        this.tr = new Vector3f[this.bones];
        this.tmpR = new Quaternionf[this.bones];
        this.tmpT = new Vector3f[this.bones];
        this.world = new Matrix4f[this.bones];
        for (int b = 0; b < this.bones; b++) {
            this.rot[b] = new Quaternionf();
            this.tr[b] = new Vector3f();
            this.tmpR[b] = new Quaternionf();
            this.tmpT[b] = new Vector3f();
            this.world[b] = new Matrix4f();
        }
        this.skin = new Matrix4f[nj];
        for (int j = 0; j < nj; j++) {
            this.skin[j] = new Matrix4f();
        }
        this.sp = new float[nv * 3];
        this.sn = new float[nv * 3];
    }

    public static SkinnedModel read(InputStream in) throws IOException {
        try (DataInputStream d = new DataInputStream(new java.io.BufferedInputStream(in))) {
            return new SkinnedModel(d);
        }
    }

    public int bone(String name) {
        return this.byName.getOrDefault(name, -1);
    }

    /** Llena la pose con el cuadro (con decimales, en bucle) de la animacion. */
    public void sample(float frame) {
        sampleInto(frame, this.rot, this.tr);
    }

    /** Mezcla la pose actual hacia otro cuadro (w = 0 nada, 1 todo). */
    public void blend(float frame, float w) {
        if (w <= 0) {
            return;
        }
        sampleInto(frame, this.tmpR, this.tmpT);
        for (int b = 0; b < this.bones; b++) {
            this.rot[b].slerp(this.tmpR[b], Math.min(1, w));
            this.tr[b].lerp(this.tmpT[b], Math.min(1, w));
        }
    }

    private void sampleInto(float frame, Quaternionf[] r, Vector3f[] t) {
        float f = ((frame % this.frames) + this.frames) % this.frames;
        int a = (int) f, b = (a + 1) % this.frames;
        float k = f - a;
        Quaternionf q2 = new Quaternionf();
        for (int i = 0; i < this.bones; i++) {
            float[] at = this.animT[i], ar = this.animR[i];
            if (at == null) {
                r[i].set(this.baseR[i]);
                t[i].set(this.baseT[i]);
                continue;
            }
            t[i].set(at[a * 3], at[a * 3 + 1], at[a * 3 + 2]).lerp(new Vector3f(at[b * 3], at[b * 3 + 1], at[b * 3 + 2]), k);
            r[i].set(ar[a * 4], ar[a * 4 + 1], ar[a * 4 + 2], ar[a * 4 + 3]);
            q2.set(ar[b * 4], ar[b * 4 + 1], ar[b * 4 + 2], ar[b * 4 + 3]);
            r[i].slerp(q2, k);
        }
    }

    /** Suma una rotacion (en los ejes del hueso) a la pose actual. */
    public void turn(int b, float x, float y, float z) {
        if (b >= 0) {
            this.rot[b].rotateXYZ(x, y, z);
        }
    }

    /** Suma una rotacion en los ejes del padre (util cuando los ejes del hueso son raros). */
    public void turnParent(int b, float x, float y, float z) {
        if (b >= 0) {
            Quaternionf q = new Quaternionf().rotateXYZ(x, y, z);
            this.rot[b].premul(q);
        }
    }

    /** Matriz del hueso en el espacio del modelo (despues de compute()). */
    public Matrix4f world(int b) {
        return this.world[b];
    }

    public void compute() {
        for (int b = 0; b < this.bones; b++) {
            this.m4.translationRotateScale(this.tr[b], this.rot[b], this.baseS[b]);
            if (this.parent[b] >= 0) {
                this.world[b].set(this.world[this.parent[b]]).mul(this.m4);
            } else {
                this.world[b].set(this.m4);
            }
        }
        for (int j = 0; j < this.skin.length; j++) {
            this.skin[j].set(this.world[this.jointBone[j]]).mul(this.ibm[j]);
        }
        int nv = this.pos.length / 3;
        for (int v = 0; v < nv; v++) {
            float x = this.pos[v * 3], y = this.pos[v * 3 + 1], z = this.pos[v * 3 + 2];
            float nx = this.nrm[v * 3], ny = this.nrm[v * 3 + 1], nz = this.nrm[v * 3 + 2];
            float ox = 0, oy = 0, oz = 0, px = 0, py = 0, pz = 0;
            for (int k = 0; k < 4; k++) {
                float w = this.we[v * 4 + k];
                if (w <= 0) {
                    continue;
                }
                Matrix4f m = this.skin[this.jo[v * 4 + k]];
                ox += w * (m.m00() * x + m.m10() * y + m.m20() * z + m.m30());
                oy += w * (m.m01() * x + m.m11() * y + m.m21() * z + m.m31());
                oz += w * (m.m02() * x + m.m12() * y + m.m22() * z + m.m32());
                px += w * (m.m00() * nx + m.m10() * ny + m.m20() * nz);
                py += w * (m.m01() * nx + m.m11() * ny + m.m21() * nz);
                pz += w * (m.m02() * nx + m.m12() * ny + m.m22() * nz);
            }
            this.sp[v * 3] = ox;
            this.sp[v * 3 + 1] = oy;
            this.sp[v * 3 + 2] = oz;
            float l = (float) Math.sqrt(px * px + py * py + pz * pz);
            if (l < 1e-6f) {
                l = 1;
            }
            this.sn[v * 3] = px / l;
            this.sn[v * 3 + 1] = py / l;
            this.sn[v * 3 + 2] = pz / l;
        }
    }

    /** Y mas bajo de la piel calculada. */
    public float minSkinnedY() {
        float min = Float.MAX_VALUE;
        for (int v = 1; v < this.sp.length; v += 3) {
            min = Math.min(min, this.sp[v]);
        }
        return min;
    }

    /** Dibuja la piel ya calculada (triangulos como cuadros con el ultimo vertice repetido). */
    public void render(PoseStack ps, VertexConsumer vc, int light, int overlay, int color) {
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        Vector3f p = new Vector3f(), q = new Vector3f();
        for (int i = 0; i < this.idx.length; i += 3) {
            for (int c = 0; c < 4; c++) {
                int v = this.idx[i + Math.min(c, 2)];
                m.transformPosition(this.sp[v * 3], this.sp[v * 3 + 1], this.sp[v * 3 + 2], p);
                n.transform(this.sn[v * 3], this.sn[v * 3 + 1], this.sn[v * 3 + 2], q);
                vc.addVertex(p.x, p.y, p.z).setColor(color).setUv(this.uv[v * 2], this.uv[v * 2 + 1]).setOverlay(overlay)
                        .setLight(light).setNormal(q.x, q.y, q.z);
            }
        }
    }
}
