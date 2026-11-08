package com.leon.saintsdragons.client.particle.compat;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Port 1.21.11: la particula de 1.20.1 de la que heredan todas las del mod. Cada una se sigue
 * dibujando con su propio render(VertexConsumer, Camera, partial) de antes; un grupo propio
 * ({@link LegacyParticleGroup}) junta esos vertices y los manda a dibujar.
 */
public abstract class TextureSheetParticle extends SingleQuadParticle {
    protected TextureSheetParticle(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z, null);
    }

    protected TextureSheetParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
        super(level, x, y, z, xd, yd, zd, null);
    }

    public LegacyRenderType getRenderType() {
        return LegacyRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public ParticleRenderType getGroup() {
        return LegacyParticleGroup.TYPE;
    }

    @Override
    protected Layer getLayer() {
        return getRenderType() == LegacyRenderType.PARTICLE_SHEET_OPAQUE ? Layer.OPAQUE : Layer.TRANSLUCENT;
    }

    public void pickSprite(SpriteSet set) {
        setSprite(set.get(this.random));
    }

    protected boolean hasSprite() {
        return this.sprite != null;
    }

    /** El dibujo de 1.20.1: un cuadrado mirando a la camara (con su giro). */
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        if (this.sprite == null) {
            return;
        }
        Quaternionf q = new Quaternionf(camera.rotation());
        if (this.roll != 0.0F) {
            q.rotateZ(Mth.lerp(partialTicks, this.oRoll, this.roll));
        }
        renderRotatedQuad(buffer, camera, q, partialTicks);
    }

    protected void renderRotatedQuad(VertexConsumer buffer, Camera camera, Quaternionf q, float partialTicks) {
        Vec3 cam = camera.position();
        float x = (float) (Mth.lerp(partialTicks, this.xo, this.x) - cam.x());
        float y = (float) (Mth.lerp(partialTicks, this.yo, this.y) - cam.y());
        float z = (float) (Mth.lerp(partialTicks, this.zo, this.z) - cam.z());
        renderRotatedQuad(buffer, q, x, y, z, partialTicks);
    }

    protected void renderRotatedQuad(VertexConsumer buffer, Quaternionf q, float x, float y, float z, float partialTicks) {
        float size = getQuadSize(partialTicks);
        float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
        int light = getLightColor(partialTicks);
        vertex(buffer, q, x, y, z, 1, -1, size, u1, v1, light);
        vertex(buffer, q, x, y, z, 1, 1, size, u1, v0, light);
        vertex(buffer, q, x, y, z, -1, 1, size, u0, v0, light);
        vertex(buffer, q, x, y, z, -1, -1, size, u0, v1, light);
    }

    private void vertex(VertexConsumer buffer, Quaternionf q, float x, float y, float z, float dx, float dy, float size, float u, float v, int light) {
        Vector3f p = new Vector3f(dx, dy, 0).rotate(q).mul(size).add(x, y, z);
        buffer.addVertex(p.x(), p.y(), p.z()).setUv(u, v).setColor(this.rCol, this.gCol, this.bCol, this.alpha).setLight(light);
    }
}
