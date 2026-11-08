package com.leon.saintsdragons.client.particle.compat;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.particle.v1.ParticleRendererRegistry;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.state.ParticleGroupRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

import java.util.EnumMap;
import java.util.Map;

/** Port 1.21.11: dibuja las particulas del mod con sus vertices de 1.20.1 (posiciones relativas a la camara). */
public final class LegacyParticleGroup extends ParticleGroup<TextureSheetParticle> {
    public static final ParticleRenderType TYPE = new ParticleRenderType("saintsdragons:legacy");

    private static final RenderPipeline NO_DEPTH_PIPELINE = RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("saintsdragons", "pipeline/particle_no_depth"))
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthWrite(false)
            .withCull(false)
            .build();
    private static final RenderType TRANSLUCENT = RenderType.create("saintsdragons_particle_translucent",
            RenderSetup.builder(RenderPipelines.TRANSLUCENT_PARTICLE).withTexture("Sampler0", TextureAtlas.LOCATION_PARTICLES).useLightmap()
                    .sortOnUpload().bufferSize(1 << 18).createRenderSetup());
    private static final RenderType OPAQUE = RenderType.create("saintsdragons_particle_opaque",
            RenderSetup.builder(RenderPipelines.OPAQUE_PARTICLE).withTexture("Sampler0", TextureAtlas.LOCATION_PARTICLES).useLightmap()
                    .bufferSize(1 << 16).createRenderSetup());
    private static final RenderType NO_DEPTH = RenderType.create("saintsdragons_particle_no_depth",
            RenderSetup.builder(NO_DEPTH_PIPELINE).withTexture("Sampler0", TextureAtlas.LOCATION_PARTICLES).useLightmap()
                    .bufferSize(1 << 18).createRenderSetup());

    public static void register() {
        ParticleRendererRegistry.register(TYPE, LegacyParticleGroup::new);
        ParticleRendererRegistry.registerOrdering(ParticleRenderType.SINGLE_QUADS, TYPE);
    }

    public LegacyParticleGroup(ParticleEngine engine) {
        super(engine);
    }

    private static RenderType typeOf(LegacyRenderType t) {
        return switch (t) {
            case PARTICLE_SHEET_OPAQUE -> OPAQUE;
            case TRANSLUCENT_NO_DEPTH_WRITE -> NO_DEPTH;
            default -> TRANSLUCENT;
        };
    }

    @Override
    public ParticleGroupRenderState extractRenderState(Frustum frustum, Camera camera, float partial) {
        Map<LegacyRenderType, VertexRecorder> buckets = new EnumMap<>(LegacyRenderType.class);
        for (TextureSheetParticle p : this.particles) {
            if (!frustum.isVisible(p.getBoundingBox().inflate(4))) {
                continue;
            }
            LegacyRenderType t = p.getRenderType();
            if (t == LegacyRenderType.CUSTOM) {
                t = LegacyRenderType.PARTICLE_SHEET_TRANSLUCENT;
            }
            try {
                p.render(buckets.computeIfAbsent(t, k -> new VertexRecorder()), camera, partial);
            } catch (RuntimeException ignored) {
                // una particula rota no corta el resto
            }
        }
        return new State(buckets);
    }

    private record State(Map<LegacyRenderType, VertexRecorder> buckets) implements ParticleGroupRenderState {
        @Override
        public void submit(SubmitNodeCollector collector, CameraRenderState camera) {
            PoseStack ps = new PoseStack();
            for (Map.Entry<LegacyRenderType, VertexRecorder> e : this.buckets.entrySet()) {
                VertexRecorder r = e.getValue();
                if (r.size() == 0) {
                    continue;
                }
                collector.submitCustomGeometry(ps, typeOf(e.getKey()), (pose, vc) -> r.replay(pose.pose(), vc));
            }
        }
    }

    /** Guarda los vertices (posicion, uv, color, luz) para dibujarlos despues. */
    static final class VertexRecorder implements VertexConsumer {
        private float[] f = new float[5 * 256];
        private int[] c = new int[2 * 256];
        private int n = -1;

        int size() {
            return this.n + 1;
        }

        void replay(Matrix4f m, VertexConsumer vc) {
            int verts = size() - size() % 4;
            for (int i = 0; i < verts; i++) {
                vc.addVertex(m, this.f[i * 5], this.f[i * 5 + 1], this.f[i * 5 + 2]).setUv(this.f[i * 5 + 3], this.f[i * 5 + 4])
                        .setColor(this.c[i * 2]).setLight(this.c[i * 2 + 1]);
            }
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.n++;
            if ((this.n + 1) * 5 > this.f.length) {
                this.f = java.util.Arrays.copyOf(this.f, this.f.length * 2);
                this.c = java.util.Arrays.copyOf(this.c, this.c.length * 2);
            }
            this.f[this.n * 5] = x;
            this.f[this.n * 5 + 1] = y;
            this.f[this.n * 5 + 2] = z;
            this.f[this.n * 5 + 3] = 0;
            this.f[this.n * 5 + 4] = 0;
            this.c[this.n * 2] = -1;
            this.c[this.n * 2 + 1] = 0xF000F0;
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return setColor((a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | (b & 255));
        }

        @Override
        public VertexConsumer setColor(int argb) {
            if (this.n >= 0) {
                this.c[this.n * 2] = argb;
            }
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            if (this.n >= 0) {
                this.f[this.n * 5 + 3] = u;
                this.f[this.n * 5 + 4] = v;
            }
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            if (this.n >= 0) {
                this.c[this.n * 2 + 1] = (u & 0xFFFF) | (v & 0xFFFF) << 16;
            }
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float w) {
            return this;
        }
    }
}
