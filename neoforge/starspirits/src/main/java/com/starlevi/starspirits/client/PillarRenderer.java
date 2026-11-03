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
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * El Pilar Runico: una piedra gigante, un poco torcida, con una runa que late. En el ritual la runa se
 * enciende del color de la rareza, sube un haz de luz y el espiritu baja girando hasta el piso.
 */
public class PillarRenderer implements BlockEntityRenderer<PillarBlockEntity> {
    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(StarSpirits.MOD_ID, "textures/block/pilar_runico.png");
    private static final int SIDES = 7;
    private static final float[] RING_Y = {-0.05f, 0.7f, 1.9f, 3.3f, 4.7f, 5.9f, 6.7f};
    private static final float[] RING_R = {1.25f, 1.12f, 1.02f, 0.95f, 0.88f, 0.78f, 0.6f};
    private static final float TOP = 7.25f;
    /** Vertices de la piedra (por anillo y lado): x, y, z. Se arma una sola vez (misma forma siempre). */
    private static final float[][][] V = new float[RING_Y.length][SIDES + 1][];
    /** Trazos de la runa (pares de puntos en el plano de la cara, x de -1 a 1, y en bloques). */
    private static final float[][] RUNE = {
            {0, 2.0f, 0, 5.0f}, {-0.45f, 2.6f, 0, 2.0f}, {0.45f, 2.6f, 0, 2.0f}, {-0.5f, 3.9f, 0, 3.3f}, {0.5f, 3.9f, 0, 3.3f},
            {-0.45f, 4.4f, 0, 5.0f}, {0.45f, 4.4f, 0, 5.0f}, {-0.3f, 3.0f, 0.3f, 3.0f}};
    private final SpiritRig.Pose pose = new SpiritRig.Pose();

    static {
        RandomSource r = RandomSource.create(1234);
        float lean = 0.22f;
        for (int i = 0; i < RING_Y.length; i++) {
            float y = RING_Y[i];
            float shift = lean * (y / TOP) * (y / TOP);
            for (int j = 0; j <= SIDES; j++) {
                int jj = j % SIDES;
                // la cara 0 mira al frente (+z): angulo centrado en medio de la cara
                float a = (jj + 0.5f) * Mth.TWO_PI / SIDES;
                float rad = RING_R[i] * (0.9f + (i == 0 ? 0.1f : 0.2f) * ((jj * 7 + i * 3) % 5) / 4f);
                if (j == SIDES) {
                    V[i][j] = V[i][0];
                    continue;
                }
                V[i][j] = new float[]{Mth.sin(a) * rad + shift, y + (i > 0 && i < RING_Y.length - 1 ? (r.nextFloat() - 0.5f) * 0.25f : 0),
                        Mth.cos(a) * rad};
            }
        }
    }

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
        stone(ps, buffers.getBuffer(RenderType.entityCutoutNoCull(TEX)), light);
        // runa: late suave; en el ritual se prende del color de la rareza
        float f = be.ritualActive() ? Mth.clamp((now - be.start) / Math.max(20, be.duration), 0, 1) : -1;
        SpiritDef d = f >= 0 ? ClientDefs.def(be.ritual) : null;
        Rarity rar = d == null ? Rarity.RARO : d.rarity;
        float pulse = 0.55f + 0.25f * Mth.sin(now * 0.07f);
        float cr = 0.45f, cg = 0.95f, cb = 1f, ca = pulse;
        if (f >= 0) {
            float k = Mth.clamp(f * 3, 0, 1);
            cr = Mth.lerp(k, cr, rar.r());
            cg = Mth.lerp(k, cg, rar.g());
            cb = Mth.lerp(k, cb, rar.b());
            ca = 0.9f + 0.1f * Mth.sin(now * 0.8f);
        }
        rune(ps.last(), buffers.getBuffer(Glow.LIGHT), cr, cg, cb, ca);
        if (f >= 0) {
            ritual(be, f, now, cr, cg, cb, ps, buffers);
        } else {
            // motitas de luz que suben despacio por la runa
            for (int i = 0; i < 3; i++) {
                float t = ((now * 0.01f + i / 3f) % 1f);
                ps.pushPose();
                ps.translate(Mth.sin(now * 0.03f + i * 2) * 0.25f, 2.0f + t * 3.2f, faceZ(2.0f + t * 3.2f) + 0.1f);
                Glow.orb(ps, buffers.getBuffer(Glow.LIGHT), 0.05f, now * 0.05f, cr, cg, cb, 0.7f * Mth.sin(t * Mth.PI));
                ps.popPose();
            }
        }
        ps.popPose();
    }

    /** Distancia del centro a la cara del frente a esa altura (para pegar la runa a la piedra). */
    private static float faceZ(float y) {
        for (int i = 0; i < RING_Y.length - 1; i++) {
            if (y <= RING_Y[i + 1]) {
                float k = (y - RING_Y[i]) / (RING_Y[i + 1] - RING_Y[i]);
                float z0 = (V[i][0][2] + V[i][SIDES - 1][2]) * 0.5f, z1 = (V[i + 1][0][2] + V[i + 1][SIDES - 1][2]) * 0.5f;
                return Mth.lerp(k, z0, z1);
            }
        }
        return 0.6f;
    }

    private static void stone(PoseStack ps, VertexConsumer vc, int light) {
        PoseStack.Pose p = ps.last();
        Matrix4f m = p.pose();
        Matrix3f n = p.normal();
        for (int i = 0; i < RING_Y.length - 1; i++) {
            for (int j = 0; j < SIDES; j++) {
                float[] a = V[i][j], b = V[i][j + 1], c = V[i + 1][j + 1], e = V[i + 1][j];
                float u0 = j / (float) SIDES * 3, u1 = (j + 1) / (float) SIDES * 3;
                vtx(vc, m, n, a, u0, a[1] / 2.2f, light);
                vtx(vc, m, n, e, u0, e[1] / 2.2f, light);
                vtx(vc, m, n, c, u1, c[1] / 2.2f, light);
                vtx(vc, m, n, b, u1, b[1] / 2.2f, light);
            }
        }
        // punta
        int last = RING_Y.length - 1;
        float[] tip = {0.22f, TOP, 0.05f};
        for (int j = 0; j < SIDES; j++) {
            float[] a = V[last][j], b = V[last][j + 1];
            vtx(vc, m, n, a, j / (float) SIDES * 3, a[1] / 2.2f, light);
            vtx(vc, m, n, tip, (j + 0.5f) / SIDES * 3, TOP / 2.2f, light);
            vtx(vc, m, n, tip, (j + 0.5f) / SIDES * 3, TOP / 2.2f, light);
            vtx(vc, m, n, b, (j + 1) / (float) SIDES * 3, b[1] / 2.2f, light);
        }
    }

    private static void vtx(VertexConsumer vc, Matrix4f m, Matrix3f n, float[] v, float u, float vv, int light) {
        org.joml.Vector3f p = m.transformPosition(v[0], v[1], v[2], new org.joml.Vector3f());
        float len = Mth.sqrt(v[0] * v[0] + v[2] * v[2]) + 1e-4f;
        org.joml.Vector3f q = n.transform(v[0] / len, 0.15f, v[2] / len, new org.joml.Vector3f());
        vc.addVertex(p.x, p.y, p.z).setColor(-1).setUv(u, vv).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(q.x, q.y, q.z);
    }

    /** La runa: trazos de luz sobre la cara del frente y la de atras. */
    private static void rune(PoseStack.Pose p, VertexConsumer vc, float r, float g, float b, float a) {
        for (int side = 0; side < 2; side++) {
            float sz = side == 0 ? 1 : -1;
            for (float[] s : RUNE) {
                float w = 0.07f;
                float x0 = s[0] * 0.55f, y0 = s[1], x1 = s[2] * 0.55f, y1 = s[3];
                float dx = x1 - x0, dy = y1 - y0, l = Mth.sqrt(dx * dx + dy * dy) + 1e-4f;
                float nx = -dy / l * w, ny = dx / l * w;
                float z0 = (Math.abs(faceZ(y0)) + 0.02f) * sz, z1 = (Math.abs(faceZ(y1)) + 0.02f) * sz;
                Matrix4f m = p.pose();
                vc.addVertex(m, x0 - nx, y0 - ny, z0).setColor(r, g, b, a);
                vc.addVertex(m, x1 - nx, y1 - ny, z1).setColor(r, g, b, a);
                vc.addVertex(m, x1 + nx, y1 + ny, z1).setColor(r, g, b, a);
                vc.addVertex(m, x0 + nx, y0 + ny, z0).setColor(r, g, b, a);
                // halo
                float hw = 3.2f;
                vc.addVertex(m, x0 - nx * hw, y0 - ny * hw, z0 + 0.01f * sz).setColor(r, g, b, a * 0.18f);
                vc.addVertex(m, x1 - nx * hw, y1 - ny * hw, z1 + 0.01f * sz).setColor(r, g, b, a * 0.18f);
                vc.addVertex(m, x1 + nx * hw, y1 + ny * hw, z1 + 0.01f * sz).setColor(r, g, b, a * 0.18f);
                vc.addVertex(m, x0 + nx * hw, y0 + ny * hw, z0 + 0.01f * sz).setColor(r, g, b, a * 0.18f);
            }
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
        ps.translate(0, 7.2f, 0);
        Glow.beam(ps.last(), buffers.getBuffer(Glow.LIGHT), 6 + 20 * bf, 0.2f, 0.5f, cr, cg, cb, 0.8f * bf * fade);
        ps.popPose();
        // el espiritu: aparece arriba de la punta como una luz, baja girando por delante de la runa y toca el piso
        float y = Mth.lerp(smooth(f), 7.6f, 0.05f);
        float zf = faceZ(Math.max(0.5f, y)) + 0.9f;
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
