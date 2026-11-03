package com.starlevi.starspirits.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.starlevi.starspirits.Rarity;
import com.starlevi.starspirits.SpiritDef;
import com.starlevi.starspirits.StarSpirits;
import com.starlevi.starspirits.block.PillarBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * El Pilar Runico: un menhir tallado, con la punta rota, piedras con musgo en la base, una runa grande
 * tallada adelante y atras, una franja de glifos abajo y tres esquirlas runicas que flotan alrededor.
 * Las runas laten suave; en el ritual se encienden del color de la rareza, las esquirlas se aceleran,
 * sube un haz de luz y el espiritu baja girando hasta el piso.
 */
public class PillarRenderer implements BlockEntityRenderer<PillarBlockEntity> {
    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(StarSpirits.MOD_ID, "textures/block/pilar_runico.png");
    /** Surcos tallados (oscuros) y trazos de luz, de la runa grande y de la franja. */
    private static final float[] GROOVE = PillarShape.strokes(0.2f, 0.012f, false), GLOW = PillarShape.strokes(0.085f, 0.03f, false),
            HALO = PillarShape.strokes(0.32f, 0.04f, false), BAND_GROOVE = PillarShape.strokes(0.11f, 0.012f, true),
            BAND_GLOW = PillarShape.strokes(0.045f, 0.03f, true);
    private static final float TOP = PillarShape.HEIGHT + 0.3f;
    private final SpiritRig.Pose pose = new SpiritRig.Pose();

    public PillarRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(PillarBlockEntity be, float partial, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) {
            return;
        }
        float now = be.getLevel().getGameTime() + partial;
        ps.pushPose();
        ps.translate(0.5, 0, 0.5);
        VertexConsumer stone = buffers.getBuffer(RenderType.entityCutoutNoCull(TEX));
        stone(ps.last(), stone, light);
        grooves(ps.last(), stone, light, GROOVE);
        grooves(ps.last(), stone, light, BAND_GROOVE);
        // runas: laten suave; en el ritual se prenden del color de la rareza
        float f = be.ritualActive() ? Mth.clamp((now - be.start) / Math.max(20, be.duration), 0, 1) : -1;
        SpiritDef d = f >= 0 ? ClientDefs.def(be.ritual) : null;
        Rarity rar = d == null ? Rarity.RARO : d.rarity;
        float pulse = 0.6f + 0.25f * Mth.sin(now * 0.06f);
        float cr = 0.42f, cg = 0.92f, cb = 1f, ca = pulse;
        if (f >= 0) {
            float k = Mth.clamp(f * 3, 0, 1);
            cr = Mth.lerp(k, cr, rar.r());
            cg = Mth.lerp(k, cg, rar.g());
            cb = Mth.lerp(k, cb, rar.b());
            ca = 0.9f + 0.1f * Mth.sin(now * 0.8f);
        }
        VertexConsumer glow = buffers.getBuffer(Glow.LIGHT);
        lines(ps.last(), glow, GLOW, cr, cg, cb, ca);
        lines(ps.last(), glow, HALO, cr, cg, cb, ca * 0.16f);
        // la franja de abajo se enciende en una ola que da la vuelta
        lines(ps.last(), glow, BAND_GLOW, cr, cg, cb, ca * (0.55f + 0.3f * Mth.sin(now * 0.11f)));
        // la gema del rombo de arriba
        for (int side = 0; side < 2; side++) {
            float[] gem = PillarShape.onFace(side, 0, 4.75f, 0.06f);
            ps.pushPose();
            ps.translate(gem[0], gem[1], gem[2]);
            Glow.orb(ps, glow, 0.07f + 0.02f * Mth.sin(now * 0.09f), now * 0.04f, cr, cg, cb, ca);
            ps.popPose();
        }
        shards(ps, buffers, light, now, f, cr, cg, cb, ca);
        if (f >= 0) {
            ritual(be, f, now, cr, cg, cb, ps, buffers);
        } else {
            // motitas de luz que suben despacio por la runa
            for (int i = 0; i < 4; i++) {
                float t = ((now * 0.008f + i / 4f) % 1f);
                float y = 1.8f + t * 3.6f;
                ps.pushPose();
                float[] pt = PillarShape.onFace(i % 2, Mth.sin(now * 0.03f + i * 2) * 0.3f, y, 0.12f);
                ps.translate(pt[0], pt[1], pt[2]);
                Glow.orb(ps, glow, 0.045f, now * 0.05f, cr, cg, cb, 0.7f * Mth.sin(t * Mth.PI));
                ps.popPose();
            }
        }
        ps.popPose();
    }

    private static void stone(PoseStack.Pose p, VertexConsumer vc, int light) {
        Matrix4f m = p.pose();
        float[] q = PillarShape.STONE;
        org.joml.Vector3f n = new org.joml.Vector3f();
        for (int i = 0; i < q.length; i += 8) {
            p.normal().transform(q[i + 5], q[i + 6], q[i + 7], n);
            vc.addVertex(m, q[i], q[i + 1], q[i + 2]).setColor(-1).setUv(q[i + 3], q[i + 4]).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light).setNormal(n.x, n.y, n.z);
        }
    }

    /** Surcos tallados: un pedacito oscuro de la textura, apenas por delante de la piedra. */
    private static void grooves(PoseStack.Pose p, VertexConsumer vc, int light, float[] q) {
        Matrix4f m = p.pose();
        for (int i = 0; i < q.length; i += 12) {
            float rx = q[i] + q[i + 6], rz = q[i + 2] + q[i + 8];
            float l = Mth.sqrt(rx * rx + rz * rz) + 1e-5f;
            org.joml.Vector3f n = p.normal().transform(rx / l, 0, rz / l, new org.joml.Vector3f());
            for (int k = 0; k < 4; k++) {
                int o = i + k * 3;
                vc.addVertex(m, q[o], q[o + 1], q[o + 2]).setColor(0.32f, 0.3f, 0.36f, 1f).setUv(0.12f + k % 2 * 0.01f, 0.5f + k / 2 * 0.01f)
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(n.x, n.y, n.z);
            }
        }
    }

    private static void lines(PoseStack.Pose p, VertexConsumer vc, float[] q, float r, float g, float b, float a) {
        Matrix4f m = p.pose();
        for (int i = 0; i < q.length; i += 3) {
            vc.addVertex(m, q[i], q[i + 1], q[i + 2]).setColor(r, g, b, a);
        }
    }

    /** Tres esquirlas de piedra con un glifo que flotan alrededor; en el ritual giran rapido y se acercan. */
    private static void shards(PoseStack ps, MultiBufferSource buffers, int light, float now, float f, float cr, float cg, float cb, float ca) {
        float speed = f >= 0 ? 0.008f + 0.05f * Mth.clamp(f * 2, 0, 1) : 0.008f;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEX));
        VertexConsumer glow = buffers.getBuffer(Glow.LIGHT);
        for (int i = 0; i < 3; i++) {
            float a = now * speed + i * Mth.TWO_PI / 3;
            float dist = f >= 0 ? Mth.lerp(Mth.clamp(f * 2, 0, 1), 2.1f, 1.55f) : 2.1f;
            float y = 4.6f + i * 0.55f + Mth.sin(now * 0.05f + i * 2.1f) * 0.25f;
            ps.pushPose();
            ps.translate(Mth.sin(a) * dist * 1.1f + 0.1f, y, Mth.cos(a) * dist * 0.85f);
            ps.mulPose(com.mojang.math.Axis.YP.rotation(a));
            ps.mulPose(com.mojang.math.Axis.ZP.rotation(Mth.sin(now * 0.03f + i) * 0.25f));
            float s = 0.75f + i * 0.12f;
            ps.scale(s, s, s);
            shard(ps.last(), vc, light, i);
            // glifo de la esquirla (mira hacia afuera)
            Matrix4f m = ps.last().pose();
            float z = 0.135f;
            float[][] g = PillarShape.GLYPHS[(i * 3 + 1) % PillarShape.GLYPHS.length];
            for (float[] st : g) {
                float x0 = (st[0] - 0.3f) * 0.22f, y0 = (st[1] - 0.5f) * 0.3f, x1 = (st[2] - 0.3f) * 0.22f, y1 = (st[3] - 0.5f) * 0.3f;
                float dx = x1 - x0, dy = y1 - y0, l = Mth.sqrt(dx * dx + dy * dy) + 1e-4f, w = 0.022f;
                float nx = -dy / l * w, ny = dx / l * w;
                glow.addVertex(m, x0 - nx, y0 - ny, z).setColor(cr, cg, cb, ca);
                glow.addVertex(m, x1 - nx, y1 - ny, z).setColor(cr, cg, cb, ca);
                glow.addVertex(m, x1 + nx, y1 + ny, z).setColor(cr, cg, cb, ca);
                glow.addVertex(m, x0 + nx, y0 + ny, z).setColor(cr, cg, cb, ca);
            }
            Glow.orb(ps, glow, 0.22f, now * 0.03f, cr, cg, cb, ca * 0.12f);
            ps.popPose();
        }
    }

    /** Una esquirla: un cristal de piedra irregular de 8 caras (con el frente plano para el glifo). */
    private static void shard(PoseStack.Pose p, VertexConsumer vc, int light, int seed) {
        float[][] ring = {{-0.17f, 0.04f, 0.12f}, {0.16f, -0.02f, 0.12f}, {0.2f, 0.02f, -0.1f}, {-0.15f, -0.03f, -0.12f}};
        float[] top = {0.02f, 0.38f + seed * 0.04f, 0}, bot = {-0.02f, -0.3f, 0.01f};
        float u = 0.05f + seed * 0.12f;
        for (int j = 0; j < 4; j++) {
            float[] a = ring[j], b = ring[(j + 1) % 4];
            tri(p, vc, light, a, b, top, u);
            tri(p, vc, light, b, a, bot, u);
        }
    }

    private static void tri(PoseStack.Pose p, VertexConsumer vc, int light, float[] a, float[] b, float[] c, float u) {
        float[] n = PillarShape.normal(a, b, c, c);
        org.joml.Vector3f nn = p.normal().transform(-n[0], -n[1], -n[2], new org.joml.Vector3f());
        Matrix4f m = p.pose();
        float[][] pts = {a, b, c, c};
        float[][] uv = {{u, 0.3f}, {u + 0.08f, 0.3f}, {u + 0.04f, 0.15f}, {u + 0.04f, 0.15f}};
        for (int k = 0; k < 4; k++) {
            vc.addVertex(m, pts[k][0], pts[k][1], pts[k][2]).setColor(-1).setUv(uv[k][0], uv[k][1]).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light).setNormal(nn.x, nn.y, nn.z);
        }
    }

    /** Ritual: anillos en el piso, haz de luz por la runa y el espiritu que baja girando. */
    private void ritual(PillarBlockEntity be, float f, float now, float cr, float cg, float cb, PoseStack ps, MultiBufferSource buffers) {
        for (int k = 0; k < 3; k++) {
            float rf = (f * 2.2f + k / 3f) % 1f;
            Glow.ring(ps.last(), buffers.getBuffer(Glow.LIGHT), 0.04f, 1.4f + rf * 2.6f, 0.14f, cr, cg, cb, (1 - rf) * 0.7f);
        }
        float bf = Mth.clamp(f / 0.3f, 0, 1), fade = f > 0.85f ? (1 - f) / 0.15f : 1;
        ps.pushPose();
        ps.translate(0.2f, TOP, 0);
        Glow.beam(ps.last(), buffers.getBuffer(Glow.LIGHT), 6 + 20 * bf, 0.2f, 0.5f, cr, cg, cb, 0.8f * bf * fade);
        ps.popPose();
        // el espiritu baja del lado de quien hizo el ritual
        net.minecraft.world.entity.player.Player owner = be.owner == null ? null : be.getLevel().getPlayerByUUID(be.owner);
        if (owner != null) {
            double dx = owner.getX() - (be.getBlockPos().getX() + 0.5), dz = owner.getZ() - (be.getBlockPos().getZ() + 0.5);
            ps.mulPose(com.mojang.math.Axis.YP.rotation((float) Math.atan2(dx, dz)));
        }
        // el espiritu: aparece arriba de la punta como una luz, baja girando por delante de la runa y toca el piso
        float y = Mth.lerp(smooth(f), TOP + 0.4f, 0.05f);
        float zf = PillarShape.frontZ(Math.min(PillarShape.HEIGHT - 0.5f, Math.max(0.5f, y))) + 0.9f;
        ps.pushPose();
        ps.translate(0, y, zf * smooth(Mth.clamp(f * 2, 0, 1)));
        if (f < 0.35f) {
            Glow.orb(ps, buffers.getBuffer(Glow.LIGHT), 0.35f, now * 0.1f, cr, cg, cb, 0.9f);
        } else {
            float g = Mth.clamp((f - 0.35f) / 0.65f, 0, 1);
            this.pose.walk = 0;
            this.pose.walkAmount = 0;
            this.pose.age = now;
            this.pose.headYaw = 0;
            this.pose.headPitch = -10;
            this.pose.happy = f > 0.9f ? (f - 0.9f) / 0.1f : -1;
            this.pose.sleeping = false;
            this.pose.seed = (int) be.getBlockPos().asLong();
            SpiritDraw.draw(be.ritual, ps, buffers, LightTexture.FULL_BRIGHT, this.pose, (1 - g) * 900, 0.3f + 0.7f * g);
            Glow.orb(ps, buffers.getBuffer(Glow.LIGHT), 0.5f * (1 - g), now * 0.1f, cr, cg, cb, 0.6f * (1 - g));
        }
        ps.popPose();
    }

    private static float smooth(float x) {
        return x * x * (3 - 2 * x);
    }

    @Override
    public AABB getRenderBoundingBox(PillarBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(3, 0, 3).expandTowards(0, 30, 0);
    }

    @Override
    public boolean shouldRenderOffScreen(PillarBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
