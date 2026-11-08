package com.leon.saintsdragons.client.compat.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Port 1.21.11: el juego ya no dibuja al momento (junta todo y dibuja despues). El codigo de efectos del
 * mod (rayos, estelas, brillos) dibuja con MultiBufferSource como en 1.20.1: esto guarda esos vertices
 * (ya transformados) y despues los manda al SubmitNodeCollector, cada tipo de dibujo de una vez.
 */
public final class DeferredBufferSource implements MultiBufferSource {
    private final Map<RenderType, Recorder> buffers = new LinkedHashMap<>();

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        return this.buffers.computeIfAbsent(type, t -> new Recorder());
    }

    public boolean isEmpty() {
        for (Recorder r : this.buffers.values()) {
            if (r.count() > 0) {
                return false;
            }
        }
        return true;
    }

    /** Manda todo al collector (las posiciones ya vienen transformadas, relativas a la camara). */
    public void submit(SubmitNodeCollector collector) {
        PoseStack identity = new PoseStack();
        for (Map.Entry<RenderType, Recorder> e : this.buffers.entrySet()) {
            Recorder r = e.getValue();
            if (r.count() == 0) {
                continue;
            }
            collector.submitCustomGeometry(identity, e.getKey(), (pose, vc) -> r.replay(vc));
        }
        this.buffers.clear();
    }

    /** Guarda posicion, color, uv, overlay, luz y normal de cada vertice. */
    public static final class Recorder implements VertexConsumer {
        private static final int F = 8; // x y z u v nx ny nz
        private static final int I = 3; // color overlay light
        private float[] f = new float[F * 64];
        private int[] i = new int[I * 64];
        private int n = -1;

        int count() {
            return this.n + 1;
        }

        void replay(VertexConsumer vc) {
            for (int k = 0; k <= this.n; k++) {
                int a = k * F, b = k * I;
                vc.addVertex(this.f[a], this.f[a + 1], this.f[a + 2]).setColor(this.i[b]).setUv(this.f[a + 3], this.f[a + 4])
                        .setOverlay(this.i[b + 1]).setLight(this.i[b + 2]).setNormal(this.f[a + 5], this.f[a + 6], this.f[a + 7]);
            }
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.n++;
            if ((this.n + 1) * F > this.f.length) {
                this.f = Arrays.copyOf(this.f, this.f.length * 2);
                this.i = Arrays.copyOf(this.i, this.i.length * 2);
            }
            int a = this.n * F, b = this.n * I;
            this.f[a] = x;
            this.f[a + 1] = y;
            this.f[a + 2] = z;
            this.f[a + 3] = 0;
            this.f[a + 4] = 0;
            this.f[a + 5] = 0;
            this.f[a + 6] = 1;
            this.f[a + 7] = 0;
            this.i[b] = -1;
            this.i[b + 1] = 0xA0000; // OverlayTexture.NO_OVERLAY
            this.i[b + 2] = 0xF000F0;
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return setColor((a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | (b & 255));
        }

        @Override
        public VertexConsumer setColor(int argb) {
            if (this.n >= 0) {
                this.i[this.n * I] = argb;
            }
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            if (this.n >= 0) {
                this.f[this.n * F + 3] = u;
                this.f[this.n * F + 4] = v;
            }
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            if (this.n >= 0) {
                this.i[this.n * I + 1] = (u & 0xFFFF) | (v & 0xFFFF) << 16;
            }
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            if (this.n >= 0) {
                this.i[this.n * I + 2] = (u & 0xFFFF) | (v & 0xFFFF) << 16;
            }
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            if (this.n >= 0) {
                this.f[this.n * F + 5] = x;
                this.f[this.n * F + 6] = y;
                this.f[this.n * F + 7] = z;
            }
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float w) {
            return this;
        }
    }
}
