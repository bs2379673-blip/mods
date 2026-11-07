package com.starlevi.starscenes.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.starlevi.starscenes.api.SceneActor;
import com.starlevi.starscenes.model.BBModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Dibuja cualquier actor de StarScenes (sus NPCs o entidades de otros mods) con su modelo de
 * Blockbench, su skin, sus items y su nombre.
 */
public class NpcRenderer<T extends Entity & SceneActor> extends EntityRenderer<T, NpcRenderer.State> {
    public static class State extends EntityRenderState {
        ClientScenes.Ready ready;
        Identifier skin;
        Matrix4f[] mats;
        /** Si se dibuja con un modelo de EMF: que caras y con que matriz. */
        java.util.List<com.starlevi.starscenes.emf.EmfRig.Draw> emf;
        float yRot;
        float scale = 1;
        /** Globito de texto (o null). */
        String bubble;
        final ItemStackRenderState main = new ItemStackRenderState(), off = new ItemStackRenderState(), head = new ItemStackRenderState();
    }

    /** Donde van los items: hueso y punto (pixeles) de cada mano y de la cabeza. */
    private record Attach(int rightBone, float[] right, int leftBone, float[] left, int headBone, float[] head) {
    }

    private static final Map<BBModel, Attach> ATTACH = new WeakHashMap<>();
    /** El animador de cada entidad en este cliente. */
    private static final Map<Entity, NpcAnimator> ANIMATORS = new WeakHashMap<>();
    /** El esqueleto de EMF de cada entidad (y de que modelo salio). */
    private static final Map<Entity, Object[]> RIGS = new WeakHashMap<>();
    private final ItemModelResolver items;

    public NpcRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.items = ctx.getItemModelResolver();
        this.shadowRadius = 0.4f;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(T npc, State s, float partial) {
        super.extractRenderState(npc, s, partial);
        s.yRot = npc instanceof LivingEntity le ? Mth.rotLerp(partial, le.yBodyRotO, le.yBodyRot) : npc.getYRot(partial);
        s.scale = npc.sceneScale();
        s.ready = ClientScenes.model(npc.sceneModel(), npc.sceneAnimationSources());
        Identifier own = npc.sceneTexture();
        s.skin = own != null ? own : ClientScenes.skin(npc.sceneSkin());
        s.mats = null;
        s.emf = null;
        s.bubble = Bubbles.get(npc.getId());
        String emfName = npc.sceneEmfModel();
        com.starlevi.starscenes.emf.JemModel jem = emfName == null ? null : com.starlevi.starscenes.emf.EmfModels.get(emfName);
        if (jem != null) {
            Object[] r = RIGS.get(npc);
            if (r == null || r[0] != jem) {
                r = new Object[]{jem, new com.starlevi.starscenes.emf.EmfRig(jem)};
                RIGS.put(npc, r);
            }
            float headYaw = 0, headPitch = npc.getXRot(partial);
            if (npc instanceof LivingEntity le) {
                headYaw = Mth.wrapDegrees(Mth.rotLerp(partial, le.yHeadRotO, le.yHeadRot) - s.yRot);
            }
            s.emf = ((com.starlevi.starscenes.emf.EmfRig) r[1]).pose(npc, partial, headYaw, headPitch);
            return;
        }
        if (s.ready != null) {
            NpcAnimator an = ANIMATORS.computeIfAbsent(npc, k -> new NpcAnimator());
            an.update(npc, npc, s.ready.model, (npc.tickCount + partial) / 20f, Minecraft.getInstance().player, s.yRot);
            s.mats = new Matrix4f[an.mats.size()];
            for (int i = 0; i < s.mats.length; i++) {
                s.mats[i] = new Matrix4f(an.mats.get(i));
            }
        }
        update(s.main, npc.sceneItem(SceneActor.MAIN_HAND), ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, npc);
        update(s.off, npc.sceneItem(SceneActor.OFF_HAND), ItemDisplayContext.THIRD_PERSON_LEFT_HAND, npc);
        update(s.head, npc.sceneItem(SceneActor.HEAD), ItemDisplayContext.HEAD, npc);
    }

    private void update(ItemStackRenderState st, ItemStack stack, ItemDisplayContext ctx, T npc) {
        if (stack.isEmpty()) {
            st.clear();
        } else if (npc instanceof LivingEntity le) {
            this.items.updateForLiving(st, stack, ctx, le);
        } else {
            this.items.updateForNonLiving(st, stack, ctx, npc);
        }
    }

    /** Lo que el animador del cliente tiene que hacer por un mensaje del servidor. */
    public static void trigger(Entity npc, String kind) {
        if (npc instanceof SceneActor) {
            ANIMATORS.computeIfAbsent(npc, k -> new NpcAnimator()).trigger(kind);
        }
    }

    @Override
    public void submit(State s, PoseStack ps, SubmitNodeCollector collector, CameraRenderState camera) {
        if (s.emf != null && s.skin != null && !s.isInvisible) {
            // como dibuja Minecraft sus modelos: girado, dado vuelta (y para abajo) y a la altura de los pies
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(180 - s.yRot));
            ps.scale(s.scale, s.scale, s.scale);
            ps.scale(-1, -1, 1);
            ps.translate(0, -1.501f, 0);
            ps.scale(1 / 16f, 1 / 16f, 1 / 16f);
            int light = s.lightCoords;
            final var draws = s.emf;
            collector.submitCustomGeometry(ps, RenderTypes.entityTranslucent(s.skin), (pose, vc) -> emitEmf(pose, vc, draws, light));
            ps.popPose();
            bubble(s, ps, collector, camera);
            super.submit(s, ps, collector, camera);
            return;
        }
        if (s.ready != null && s.mats != null && !s.isInvisible) {
            BBModel m = s.ready.model;
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(180 - s.yRot));
            float k = s.scale / 16f;
            ps.scale(k, k, k);
            int light = s.lightCoords;
            for (int t = 0; t < s.ready.textures.length; t++) {
                Identifier tex = s.ready.isSkin[t] && s.skin != null ? s.skin : s.ready.textures[t];
                if (tex == null) {
                    continue;
                }
                final int tt = t;
                final Matrix4f[] mats = s.mats;
                collector.submitCustomGeometry(ps, RenderTypes.entityCutoutNoCull(tex), (pose, vc) -> emit(pose, vc, m, mats, tt, light));
            }
            Attach at = ATTACH.computeIfAbsent(m, NpcRenderer::attach);
            item(ps, collector, s, s.main, at.rightBone, at.right, false, light);
            item(ps, collector, s, s.off, at.leftBone, at.left, false, light);
            item(ps, collector, s, s.head, at.headBone, at.head, true, light);
            ps.popPose();
        }
        bubble(s, ps, collector, camera);
        super.submit(s, ps, collector, camera);
    }

    /** El globito de texto, arriba de la cabeza (y del nombre, si se ve). */
    private static void bubble(State s, PoseStack ps, SubmitNodeCollector collector, CameraRenderState camera) {
        if (s.bubble == null || s.isInvisible) {
            return;
        }
        double y = s.boundingBoxHeight + 0.5 + (s.nameTag != null ? 0.28 : 0);
        collector.submitNameTag(ps, new net.minecraft.world.phys.Vec3(0, y, 0), 0, net.minecraft.network.chat.Component.literal(s.bubble), true,
                s.lightCoords, s.distanceToCameraSq, camera);
    }

    private static void emitEmf(PoseStack.Pose pose, VertexConsumer vc, java.util.List<com.starlevi.starscenes.emf.EmfRig.Draw> draws, int light) {
        Matrix4f full = new Matrix4f();
        Matrix3f nrm = new Matrix3f();
        for (com.starlevi.starscenes.emf.EmfRig.Draw d : draws) {
            full.set(pose.pose()).mul(d.matrix());
            nrm.set(pose.normal()).mul(new Matrix3f(d.matrix()));
            for (com.starlevi.starscenes.emf.JemModel.Face q : d.faces()) {
                float nx = nrm.m00() * q.nx + nrm.m10() * q.ny + nrm.m20() * q.nz;
                float ny = nrm.m01() * q.nx + nrm.m11() * q.ny + nrm.m21() * q.nz;
                float nz = nrm.m02() * q.nx + nrm.m12() * q.ny + nrm.m22() * q.nz;
                float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz) + 1e-6f;
                for (int v = 0; v < 4; v++) {
                    float x = q.xyz[v * 3], y = q.xyz[v * 3 + 1], z = q.xyz[v * 3 + 2];
                    vc.addVertex(full.m00() * x + full.m10() * y + full.m20() * z + full.m30(), full.m01() * x + full.m11() * y + full.m21() * z + full.m31(),
                            full.m02() * x + full.m12() * y + full.m22() * z + full.m32(), -1, q.uv[v * 2], q.uv[v * 2 + 1], OverlayTexture.NO_OVERLAY, light,
                            nx / l, ny / l, nz / l);
                }
            }
        }
    }

    private static void emit(PoseStack.Pose pose, VertexConsumer vc, BBModel m, Matrix4f[] mats, int tex, int light) {
        Matrix4f full = new Matrix4f();
        Matrix3f nrm = new Matrix3f();
        for (int b = 0; b < m.bones.size(); b++) {
            BBModel.Bone bone = m.bones.get(b);
            if (bone.hidden || bone.quads.isEmpty()) {
                continue;
            }
            full.set(pose.pose()).mul(mats[b]);
            nrm.set(pose.normal()).mul(new Matrix3f(mats[b]));
            for (BBModel.Quad q : bone.quads) {
                if (q.texture != tex) {
                    continue;
                }
                float nx = nrm.m00() * q.nx + nrm.m10() * q.ny + nrm.m20() * q.nz;
                float ny = nrm.m01() * q.nx + nrm.m11() * q.ny + nrm.m21() * q.nz;
                float nz = nrm.m02() * q.nx + nrm.m12() * q.ny + nrm.m22() * q.nz;
                float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz) + 1e-6f;
                for (int v = 0; v < 4; v++) {
                    float x = q.xyz[v * 3], y = q.xyz[v * 3 + 1], z = q.xyz[v * 3 + 2];
                    vc.addVertex(full.m00() * x + full.m10() * y + full.m20() * z + full.m30(), full.m01() * x + full.m11() * y + full.m21() * z + full.m31(),
                            full.m02() * x + full.m12() * y + full.m22() * z + full.m32(), -1, q.uv[v * 2], q.uv[v * 2 + 1], OverlayTexture.NO_OVERLAY, light,
                            nx / l, ny / l, nz / l);
                }
            }
        }
    }

    private void item(PoseStack ps, SubmitNodeCollector collector, State s, ItemStackRenderState st, int bone, float[] at, boolean head, int light) {
        if (st.isEmpty() || bone < 0) {
            return;
        }
        ps.pushPose();
        ps.mulPose(s.mats[bone]);
        ps.translate(at[0], at[1], at[2]);
        ps.scale(16, 16, 16);
        // al espacio de los modelos de Minecraft (y para abajo) y como los acomoda Minecraft
        ps.scale(-1, -1, 1);
        if (head) {
            ps.translate(0, -0.25f, 0);
            ps.mulPose(Axis.YP.rotationDegrees(180));
            ps.scale(0.625f, -0.625f, -0.625f);
        } else {
            ps.mulPose(Axis.XP.rotationDegrees(-90));
            ps.mulPose(Axis.YP.rotationDegrees(180));
            ps.translate(0, 0.0625f, -0.0625f);
        }
        st.submit(ps, collector, light, OverlayTexture.NO_OVERLAY, s.outlineColor);
        ps.popPose();
    }

    /** Busca las manos y la cabeza del modelo (por los nombres de huesos de ModelEngine o de jugador). */
    private static Attach attach(BBModel m) {
        int right = -1, left = -1, head = m.headBone;
        for (int i = 0; i < m.bones.size(); i++) {
            String n = m.bones.get(i).name.toLowerCase(Locale.ROOT);
            if (n.contains("item")) {
                continue;
            }
            if (right < 0 && (n.equals("right_arm") || n.equals("rightarm") || n.equals("arm_right") || n.equals("brazo_derecho"))) {
                right = i;
            }
            if (left < 0 && (n.equals("left_arm") || n.equals("leftarm") || n.equals("arm_left") || n.equals("brazo_izquierdo"))) {
                left = i;
            }
        }
        return new Attach(right, hand(m, right), left, hand(m, left), head, head < 0 ? null : m.bones.get(head).origin.clone());
    }

    /** La mano: abajo de todo del brazo, en el medio. */
    private static float[] hand(BBModel m, int bone) {
        if (bone < 0) {
            return null;
        }
        float minY = Float.MAX_VALUE, sx = 0, sz = 0;
        int n = 0;
        for (BBModel.Quad q : m.bones.get(bone).quads) {
            for (int v = 0; v < 4; v++) {
                minY = Math.min(minY, q.xyz[v * 3 + 1]);
                sx += q.xyz[v * 3];
                sz += q.xyz[v * 3 + 2];
                n++;
            }
        }
        if (n == 0) {
            float[] o = m.bones.get(bone).origin;
            return new float[]{o[0], o[1] - 10, o[2]};
        }
        return new float[]{sx / n, minY + 1, sz / n};
    }
}
