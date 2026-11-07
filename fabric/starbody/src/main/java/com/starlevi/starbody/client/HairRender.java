package com.starlevi.starbody.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * Dibuja las tiras de pelo pegadas a la cabeza y al torso, dobladas segun las fisicas. Sirve para
 * cualquier modelo humanoide (jugadores y, despues, aldeanos): solo necesita sus partes cabeza y torso.
 */
public final class HairRender {
    /** Lo que se guarda en el estado de dibujo de cada personaje. */
    public record Frame(Identifier skin, HairMesh mesh, HairSim sim, boolean showHat, boolean showJacket, boolean helmet) {
    }

    public static final RenderStateDataKey<Frame> KEY = RenderStateDataKey.create(() -> "starbody:pelo");

    private HairRender() {
    }

    public static void submit(Frame f, PoseStack ps, SubmitNodeCollector collector, int light, ModelPart head, ModelPart body) {
        if (f == null || f.mesh() == null || f.mesh().strips.isEmpty()) {
            return;
        }
        if (!f.helmet()) {
            // con casco no se dibuja el pelo de la cabeza (si no, atraviesa el casco)
            draw(f, ps, collector, light, head, SkinLayout.Part.HEAD);
        }
        draw(f, ps, collector, light, body, SkinLayout.Part.BODY);
    }

    /**
     * Para otros mods (los aldeanos de StarVillagers): las tiras de la cabeza, con ps ya puesto en el
     * espacio de la cabeza de un jugador, en pixeles.
     */
    public static void submitHead(Identifier skin, HairMesh mesh, HairSim sim, PoseStack ps, SubmitNodeCollector collector, int light) {
        if (mesh == null || mesh.strips.isEmpty() || skin == null) {
            return;
        }
        collector.submitCustomGeometry(ps, RenderTypes.entityCutoutNoCull(skin), (pose, vc) -> {
            for (HairMesh.Strip s : mesh.strips) {
                if (s.face().part() == SkinLayout.Part.HEAD) {
                    strip(pose, vc, s, sim, 0, light);
                }
            }
        });
    }

    /** Igual que submit, pero la cabeza se toma de donde de verdad se dibuja (sigue a EMF y paquetes). */
    public static void submitFollowing(Frame f, PoseStack ps, SubmitNodeCollector collector, int light, ModelPart root, ModelPart head, ModelPart body) {
        if (f == null || f.mesh() == null || f.mesh().strips.isEmpty()) {
            return;
        }
        if (!f.helmet()) {
            HeadFinder.Head h = HeadFinder.find(root);
            if (h == null) {
                draw(f, ps, collector, light, head, SkinLayout.Part.HEAD);
            } else {
                boolean any = false;
                for (HairMesh.Strip s : f.mesh().strips) {
                    if (s.face().part() == SkinLayout.Part.HEAD && visible(f, s)) {
                        any = true;
                        break;
                    }
                }
                if (any) {
                    ps.pushPose();
                    h.apply(ps);
                    collector.submitCustomGeometry(ps, RenderTypes.entityCutoutNoCull(f.skin()), (pose, vc) -> {
                        for (HairMesh.Strip s : f.mesh().strips) {
                            if (s.face().part() == SkinLayout.Part.HEAD && visible(f, s)) {
                                strip(pose, vc, s, f.sim(), 0, light);
                            }
                        }
                    });
                    ps.popPose();
                }
            }
        }
        draw(f, ps, collector, light, body, SkinLayout.Part.BODY);
    }

    private static void draw(Frame f, PoseStack ps, SubmitNodeCollector collector, int light, ModelPart part, SkinLayout.Part which) {
        if (part == null || !part.visible) {
            return;
        }
        boolean any = false;
        for (HairMesh.Strip s : f.mesh().strips) {
            if (s.face().part() == which && visible(f, s)) {
                any = true;
                break;
            }
        }
        if (!any) {
            return;
        }
        ps.pushPose();
        part.translateAndRotate(ps);
        ps.scale(1 / 16f, 1 / 16f, 1 / 16f);
        int p = which == SkinLayout.Part.HEAD ? 0 : 1;
        collector.submitCustomGeometry(ps, RenderTypes.entityCutoutNoCull(f.skin()), (pose, vc) -> {
            for (HairMesh.Strip s : f.mesh().strips) {
                if (s.face().part() == which && visible(f, s)) {
                    strip(pose, vc, s, f.sim(), p, light);
                }
            }
        });
        ps.popPose();
    }

    private static boolean visible(Frame f, HairMesh.Strip s) {
        if (!s.face().outer()) {
            return true;
        }
        return s.face().part() == SkinLayout.Part.HEAD ? f.showHat() : f.showJacket();
    }

    private static void strip(PoseStack.Pose pose, VertexConsumer vc, HairMesh.Strip s, HairSim sim, int part, int light) {
        SkinLayout.Face f = s.face();
        float x0 = f.x(s.column()), z0 = f.z(s.column()), x1 = f.x(s.column() + 1), z1 = f.z(s.column() + 1);
        float nx = f.nx(), nz = f.nz();
        float len = f.y(1) - f.y(0);
        float y = f.y(s.top()), out = 0;
        // las capas de afuera quedan apenas por fuera para no tocar la de adentro
        float lift = f.outer() ? 0.02f : 0.04f;
        for (int k = 0; k < s.length(); k++) {
            float a = sim.segment(part, f.side(), k, s.length(), s.phase());
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
            float y2 = y + len * cos, out2 = out + len * sin;
            float u0 = (f.u0() + s.column()) / 64f, u1 = u0 + 1 / 64f;
            float v0 = (f.v0() + s.top() + k) / 64f, v1 = v0 + 1 / 64f;
            float o1 = out + lift, o2 = out2 + lift;
            float nnx = nx * cos, nny = -sin, nnz = nz * cos;
            vertex(pose, vc, x0 + nx * o1, y, z0 + nz * o1, u0, v0, light, nnx, nny, nnz);
            vertex(pose, vc, x1 + nx * o1, y, z1 + nz * o1, u1, v0, light, nnx, nny, nnz);
            vertex(pose, vc, x1 + nx * o2, y2, z1 + nz * o2, u1, v1, light, nnx, nny, nnz);
            vertex(pose, vc, x0 + nx * o2, y2, z0 + nz * o2, u0, v1, light, nnx, nny, nnz);
            y = y2;
            out = out2;
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z, float u, float v, int light, float nx, float ny, float nz) {
        vc.addVertex(pose, x, y, z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }
}
