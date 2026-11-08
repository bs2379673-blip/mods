package com.starlevi.starmodels.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.starlevi.starmodels.model.AvatarBuilder;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dibuja el personaje en lugar del jugador. Cada parte sigue a la parte del jugador tal como quedo
 * despues de todas las animaciones (Minecraft, EMF / Fresh Animations, Punchy, Not Enough Animations,
 * gestos de StarScenes...), con el mismo giro y corrimiento pero desde la articulacion del personaje.
 * Las texturas se mandan de a una (un dibujo por textura) y de lejos se usa la version liviana.
 */
public class AvatarLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    /** Lo que se guarda en el estado de dibujo del jugador. */
    public record Frame(ClientAvatars.Look look, ClientAvatars.Ready ready, boolean far) {
    }

    public static final RenderStateDataKey<Frame> KEY = RenderStateDataKey.create(() -> "starmodels:personaje");
    /** A partir de cuantos bloques se usa la version liviana. */
    public static final double FAR = 24;

    public AvatarLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    public static Frame frame(AvatarRenderState s) {
        return ((FabricRenderState) (Object) s).getData(KEY);
    }

    static ModelPart part(PlayerModel m, String name, ModelPart def) {
        try {
            ModelPart p = m.root().getChild(name);
            return p != null ? p : def;
        } catch (Exception e) {
            return def;
        }
    }

    static ModelPart[] parts(PlayerModel m) {
        return new ModelPart[]{part(m, "head", m.head), part(m, "body", m.body), part(m, "right_arm", m.rightArm), part(m, "left_arm", m.leftArm),
                part(m, "right_leg", m.rightLeg), part(m, "left_leg", m.leftLeg)};
    }

    /** Cuanto se dobla la rodilla segun como esta la pierna (positivo = la pantorrilla va para atras). */
    static float knee(float legX, boolean crouch) {
        float b = 0.06f + Math.max(0, legX) * 0.45f + Math.max(0, -legX) * 0.12f + Math.max(0, -legX - 0.9f) * 2f;
        if (crouch) {
            b += 0.45f;
        }
        return Math.min(b, 2.2f);
    }

    /** El codo: un poco siempre (como en Roblox), mas al ir el brazo adelante; estirado al apuntar. */
    static float elbow(float armX) {
        float f = Math.max(0, -armX);
        f = f < 0.8f ? f : Math.max(0, 1.6f - f);
        return -(0.12f + 0.5f * f);
    }

    @Override
    public void submit(PoseStack ps, SubmitNodeCollector collector, int light, AvatarRenderState s, float yRot, float xRot) {
        Frame f = frame(s);
        if (f == null || f.ready() == null || s.isInvisible) {
            return;
        }
        draw(ps, collector, light, LivingEntityRenderer.getOverlayCoords(s, 0), f, parts(getParentModel()), s.isCrouching);
    }

    /** Una cosa a dibujar: un lote, de que parte, y un ajuste extra (accesorios de otro personaje). */
    private record Item(AvatarBuilder.Batch batch, Matrix4f extra) {
    }

    private static Matrix4f[] pose(AvatarBuilder.Result m, ModelPart[] vanilla, boolean crouch) {
        Matrix4f[] mats = new Matrix4f[AvatarBuilder.PARTS];
        Quaternionf q = new Quaternionf();
        for (int i = 0; i < 6; i++) {
            ModelPart p = vanilla[i];
            PartPose d = p.getInitialPose();
            float[] j = m.joints[i];
            Matrix4f mt = mats[i] = new Matrix4f().translate(j[0] + p.x - d.x(), j[1] + p.y - d.y(), j[2] + p.z - d.z());
            if (p.xRot != 0 || p.yRot != 0 || p.zRot != 0) {
                mt.rotate(q.rotationZYX(p.zRot, p.yRot, p.xRot));
            }
            if (p.xScale != 1 || p.yScale != 1 || p.zScale != 1) {
                mt.scale(p.xScale, p.yScale, p.zScale);
            }
            mt.translate(-j[0], -j[1], -j[2]);
        }
        for (int c = AvatarBuilder.RIGHT_FOREARM; c < AvatarBuilder.PARTS; c++) {
            int parent = c - 4;
            float x = vanilla[parent].xRot;
            float bend = c <= AvatarBuilder.LEFT_FOREARM ? elbow(x) : knee(x, crouch);
            float[] k = m.joints[c];
            mats[c] = new Matrix4f(mats[parent]).translate(k[0], k[1], k[2]).rotateX(bend).translate(-k[0], -k[1], -k[2]);
        }
        return mats;
    }

    /** Tambien lo usa el workshop. */
    static void draw(PoseStack ps, SubmitNodeCollector collector, int light, int overlay, Frame f, ModelPart[] vanilla, boolean crouch) {
        ClientAvatars.Ready r = f.ready();
        AvatarBuilder.Result m = r.pick(f.far());
        Matrix4f[] mats = pose(m, vanilla, crouch);
        // agrupado por textura: cada textura es un solo dibujo para la placa de video
        Map<RenderType, List<Item>> groups = new LinkedHashMap<>();
        for (int mi = 0; mi < m.byMaterial.size(); mi++) {
            List<AvatarBuilder.Batch> list = m.byMaterial.get(mi);
            if (!list.isEmpty()) {
                List<Item> g = groups.computeIfAbsent(type(r, m, mi), t -> new ArrayList<>());
                for (AvatarBuilder.Batch b : list) {
                    g.add(new Item(b, null));
                }
            }
        }
        ClientAvatars.Look look = f.look();
        for (AvatarBuilder.Accessory a : m.accessories) {
            if (look != null && look.without().contains(a.id)) {
                continue;
            }
            for (AvatarBuilder.Batch b : a.batches) {
                groups.computeIfAbsent(type(r, m, b.material), t -> new ArrayList<>()).add(new Item(b, null));
            }
        }
        if (look != null) {
            for (ClientAvatars.Added ad : look.with()) {
                ClientAvatars.Ready src = ClientAvatars.get(ad.from());
                if (src == null) {
                    continue;
                }
                AvatarBuilder.Result sm = src.pick(f.far());
                AvatarBuilder.Accessory a = null;
                for (AvatarBuilder.Accessory x : sm.accessories) {
                    if (x.id.equals(ad.acc())) {
                        a = x;
                        break;
                    }
                }
                if (a == null) {
                    continue;
                }
                // de la articulacion de ese personaje a la de este, mas el ajuste, y el tamano alrededor del accesorio
                float[] sj = sm.joints[a.part], dj = m.joints[a.part], c = a.center;
                Matrix4f extra = new Matrix4f().translate(c[0] + dj[0] - sj[0] + ad.x(), c[1] + dj[1] - sj[1] + ad.y(), c[2] + dj[2] - sj[2] + ad.z())
                        .scale(ad.s()).translate(-c[0], -c[1], -c[2]);
                for (AvatarBuilder.Batch b : a.batches) {
                    groups.computeIfAbsent(type(src, sm, b.material), t -> new ArrayList<>()).add(new Item(b, extra));
                }
            }
        }
        for (Map.Entry<RenderType, List<Item>> e : groups.entrySet()) {
            List<Item> items = e.getValue();
            collector.submitCustomGeometry(ps, e.getKey(), (pose, vc) -> {
                Matrix4f[] full = new Matrix4f[AvatarBuilder.PARTS];
                Matrix3f[] fullN = new Matrix3f[AvatarBuilder.PARTS];
                Matrix3f rot = new Matrix3f();
                for (int i = 0; i < AvatarBuilder.PARTS; i++) {
                    full[i] = new Matrix4f(pose.pose()).scale(1 / 16f).mul(mats[i]);
                    fullN[i] = new Matrix3f(pose.normal()).mul(rot.set(mats[i]));
                }
                Matrix4f tmp = new Matrix4f();
                for (Item it : items) {
                    AvatarBuilder.Batch b = it.batch();
                    if (b.part < 0) {
                        seams(vc, full, fullN, b.data, light, overlay);
                    } else if (it.extra() != null) {
                        emit(vc, tmp.set(full[b.part]).mul(it.extra()), fullN[b.part], b.data, light, overlay);
                    } else {
                        emit(vc, full[b.part], fullN[b.part], b.data, light, overlay);
                    }
                }
            });
        }
    }

    private static RenderType type(ClientAvatars.Ready r, AvatarBuilder.Result m, int material) {
        Identifier tex = r.textures[Math.min(material, r.textures.length - 1)];
        return m.materials.get(material).doubleSided ? RenderTypes.entityCutoutNoCull(tex) : RenderTypes.entityCutout(tex);
    }

    private static void vertex(VertexConsumer vc, Matrix4f mat, Matrix3f nm, float x, float y, float z, float nx, float ny, float nz, float u, float v,
                               int light, int overlay) {
        float qx = nm.m00() * nx + nm.m10() * ny + nm.m20() * nz;
        float qy = nm.m01() * nx + nm.m11() * ny + nm.m21() * nz;
        float qz = nm.m02() * nx + nm.m12() * ny + nm.m22() * nz;
        float l = (float) Math.sqrt(qx * qx + qy * qy + qz * qz) + 1e-6f;
        vc.addVertex(mat.m00() * x + mat.m10() * y + mat.m20() * z + mat.m30(), mat.m01() * x + mat.m11() * y + mat.m21() * z + mat.m31(),
                mat.m02() * x + mat.m12() * y + mat.m22() * z + mat.m32(), -1, u, v, overlay, light, qx / l, qy / l, qz / l);
    }

    /**
     * Los dibujos de entidades de Minecraft son de cuadrados: cada triangulo va como un cuadrado con el
     * ultimo vertice repetido (asi funciona igual con shaders, que esperan esos dibujos).
     */
    private static void emit(VertexConsumer vc, Matrix4f mat, Matrix3f nm, float[] d, int light, int overlay) {
        for (int o = 0; o + 24 <= d.length; o += 24) {
            for (int k = 0; k < 4; k++) {
                int p = o + Math.min(k, 2) * 8;
                vertex(vc, mat, nm, d[p], d[p + 1], d[p + 2], d[p + 3], d[p + 4], d[p + 5], d[p + 6], d[p + 7], light, overlay);
            }
        }
    }

    /** Uniones de la ropa 3D: cada vertice va con su parte; el triangulo se estira entre las dos. */
    private static void seams(VertexConsumer vc, Matrix4f[] full, Matrix3f[] fullN, float[] d, int light, int overlay) {
        int st = AvatarBuilder.Batch.SEAM_STRIDE;
        for (int o = 0; o + st * 3 <= d.length; o += st * 3) {
            for (int k = 0; k < 4; k++) {
                int p = o + Math.min(k, 2) * st;
                int part = (int) d[p + 8];
                vertex(vc, full[part], fullN[part], d[p], d[p + 1], d[p + 2], d[p + 3], d[p + 4], d[p + 5], d[p + 6], d[p + 7], light, overlay);
            }
        }
    }
}
