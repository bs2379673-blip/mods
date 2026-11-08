package com.leon.saintsdragons.client.compat.geo;

import com.leon.saintsdragons.client.compat.render.DeferredBufferSource;
import com.leon.saintsdragons.util.compat.SDTickets;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.model.BakedGeoModel;
import software.bernie.geckolib.cache.model.GeoBone;
import com.mojang.blaze3d.vertex.VertexConsumer;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.BoneSnapshots;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.RenderPassInfo;

/**
 * Port 1.21.11: renderer de GeckoLib 5 con los puntos de entrada de GeckoLib 4 que usa el mod
 * (getTextureLocation, getRenderType, applyRotations, getDeathMaxRotation, y "renderExtras" para lo que
 * se dibujaba en render() despues del modelo, con un MultiBufferSource que se manda despues al collector).
 * Tambien corre los setCustomAnimations de los modelos y deja tomar la posicion de huesos al dibujar.
 */
public class LegacyGeoEntityRenderer<T extends Entity & GeoAnimatable> extends GeoEntityRenderer<T, SDRenderState> {
    private record LayerCtx(RenderPassInfo<SDRenderState> info, SubmitNodeCollector collector) {
    }

    private static final ThreadLocal<java.util.ArrayDeque<LayerCtx>> LAYERS = ThreadLocal.withInitial(java.util.ArrayDeque::new);

    static void pushLayer(RenderPassInfo<SDRenderState> info, SubmitNodeCollector collector) {
        LAYERS.get().push(new LayerCtx(info, collector));
    }

    static void popLayer() {
        LAYERS.get().pop();
    }

    private record SubmitCtx(SDRenderState state, SubmitNodeCollector collector, CameraRenderState camera) {
    }

    private SubmitCtx submitCtx;
    private RenderPassInfo<SDRenderState> currentInfo;
    private float currentNativeScale = 1;

    public LegacyGeoEntityRenderer(EntityRendererProvider.Context context, GeoModel<T> model) {
        super(context, model);
    }

    @Override
    public SDRenderState createRenderState(T animatable, @Nullable Void relatedObject) {
        return new SDRenderState();
    }

    @SuppressWarnings("unchecked")
    protected T entityOf(GeoRenderState state) {
        return state == null ? null : (T) state.getGeckolibData(SDTickets.ENTITY);
    }

    protected static float partialOf(GeoRenderState state) {
        Float f = state == null ? null : state.getGeckolibData(DataTickets.PARTIAL_TICK);
        return f == null ? 1 : f;
    }

    @Override
    public void addRenderData(T animatable, @Nullable Void relatedObject, SDRenderState renderState, float partialTick) {
        super.addRenderData(animatable, relatedObject, renderState, partialTick);
        renderState.addGeckolibData(SDTickets.ENTITY, animatable);
        renderState.addGeckolibData(LegacyGeoModel.ANIMATABLE, animatable);
    }

    // ------------------------------------------------------------------ texturas y tipo de dibujo

    public Identifier getTextureLocation(T animatable) {
        GeoModel<T> m = getGeoModel();
        if (m instanceof LegacyGeoModel<T> legacy) {
            return legacy.getLegacyTextureResource(animatable);
        }
        return null;
    }

    @Override
    public Identifier getTextureLocation(SDRenderState renderState) {
        T e = entityOf(renderState);
        Identifier id = e == null ? null : getTextureLocation(e);
        return id != null ? id : super.getTextureLocation(renderState);
    }

    public RenderType getRenderType(T animatable, Identifier texture, @Nullable MultiBufferSource bufferSource, float partialTick) {
        if (getGeoModel() instanceof LegacyGeoModel<T> legacy) {
            RenderType t = legacy.getRenderType(animatable, texture);
            if (t != null) {
                return t;
            }
        }
        return net.minecraft.client.renderer.rendertype.RenderTypes.entityCutoutNoCull(texture);
    }

    // ------------------------------------------------------------ cambiar los vertices (lo que hacia actuallyRender)

    /** Envolver lo que se dibuja (por ejemplo, para correr la textura); null = sin cambios. */
    protected com.mojang.blaze3d.vertex.VertexConsumer wrapBuffer(T entity, com.mojang.blaze3d.vertex.VertexConsumer buffer, float partialTick) {
        return null;
    }

    /** La luz con que se dibuja el modelo (por ejemplo, siempre al maximo). */
    protected int modelLight(T entity, int packedLight) {
        return packedLight;
    }

    @Override
    public void submitRenderTasks(RenderPassInfo<SDRenderState> info, net.minecraft.client.renderer.OrderedSubmitNodeCollector collector, RenderType type) {
        T e = entityOf(info.renderState());
        float partial = partialOf(info.renderState());
        if (e == null || (wrapBuffer(e, null, partial) == null && modelLight(e, info.packedLight()) == info.packedLight())) {
            super.submitRenderTasks(info, collector, type);
            return;
        }
        int light = modelLight(e, info.packedLight());
        int overlay = info.packedOverlay();
        int color = info.renderColor();
        collector.submitCustomGeometry(info.poseStack(), type, (pose, vc) -> {
            com.mojang.blaze3d.vertex.VertexConsumer w = wrapBuffer(e, vc, partial);
            com.mojang.blaze3d.vertex.VertexConsumer out = w == null ? vc : w;
            PoseStack ps = info.poseStack();
            ps.pushPose();
            ps.last().set(pose);
            info.renderPosed(() -> info.model().render(info, out, light, overlay, color));
            ps.popPose();
        });
    }

    @Override
    public RenderType getRenderType(SDRenderState renderState, Identifier texture) {
        T e = entityOf(renderState);
        return e == null ? super.getRenderType(renderState, texture) : getRenderType(e, texture, null, partialOf(renderState));
    }

    // ------------------------------------------------------------------ giros

    @Override
    protected void applyRotations(RenderPassInfo<SDRenderState> renderPassInfo, PoseStack poseStack, float nativeScale) {
        this.currentInfo = renderPassInfo;
        this.currentNativeScale = nativeScale;
        T e = entityOf(renderPassInfo.renderState());
        float partial = partialOf(renderPassInfo.renderState());
        if (e == null) {
            super.applyRotations(renderPassInfo, poseStack, nativeScale);
            return;
        }
        float yaw = renderPassInfo.renderState().getOrDefaultGeckolibData(DataTickets.ENTITY_BODY_YAW, e.getYRot());
        applyRotations(e, poseStack, e.tickCount + partial, yaw, partial);
    }

    /** Como en GeckoLib 4; por defecto, lo de GeckoLib 5. */
    protected void applyRotations(T animatable, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick) {
        if (this.currentInfo != null) {
            super.applyRotations(this.currentInfo, poseStack, this.currentNativeScale);
        }
    }

    protected float getDeathMaxRotation(T animatable) {
        return 90;
    }

    @Override
    protected float getDeathMaxRotation(GeoRenderState renderState) {
        T e = entityOf(renderState);
        return e == null ? 90 : getDeathMaxRotation(e);
    }

    // ------------------------------------------------------------------ huesos

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<SDRenderState> renderPassInfo, BoneSnapshots snapshots) {
        super.adjustModelBonesForRender(renderPassInfo, snapshots);
        LegacyGeoModel.runCustomAnimations(getGeoModel(), renderPassInfo, snapshots);
        hideBlockBones(renderPassInfo, snapshots);
    }

    /** Huesos de los que se quiere saber donde quedaron al dibujar (ver {@link #onBonePose}). */
    protected String[] sampledBones(T animatable) {
        return new String[0];
    }

    /** poseAtBone: el PoseStack en el hueso (relativo a la camara, como en GeckoLib 4 al dibujar). */
    protected void onBonePose(T animatable, GeoBone bone, PoseStack poseAtBone, RenderPassInfo<SDRenderState> info) {
    }

    @Override
    public void preRenderPass(RenderPassInfo<SDRenderState> renderPassInfo, SubmitNodeCollector renderTasks) {
        super.preRenderPass(renderPassInfo, renderTasks);
        if (renderPassInfo.willRender()) {
            addBlockBones(renderPassInfo);
        }
        T e = entityOf(renderPassInfo.renderState());
        if (e == null || !renderPassInfo.willRender()) {
            return;
        }
        for (String name : sampledBones(e)) {
            renderPassInfo.model().getBone(name).ifPresent(bone ->
                    renderPassInfo.addPerBoneRender(bone, (info, b, collector) -> onBonePose(e, b, info.poseStack(), info)));
        }
    }

    // ------------------------------------------------------------------ lo que se dibujaba despues del modelo

    /** Lo que en GeckoLib 4 se dibujaba en render() despues de super.render(...). */
    protected void renderExtras(T entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
    }

    /**
     * Como en GeckoLib 4: dibuja el modelo con el PoseStack que llega (los renderers del mod lo giran o
     * escalan antes de llamar a super.render) y despues lo de renderExtras.
     */
    public void render(T entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        SubmitCtx c = this.submitCtx;
        if (c != null && shouldDrawModel(c.state())) {
            int prevLight = c.state().lightCoords;
            c.state().lightCoords = packedLight;
            try {
                super.submit(c.state(), poseStack, c.collector(), c.camera());
            } finally {
                c.state().lightCoords = prevLight;
            }
        }
        renderExtras(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public void submit(SDRenderState renderState, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraState) {
        T e = entityOf(renderState);
        if (e == null) {
            super.submit(renderState, poseStack, collector, cameraState);
            return;
        }
        DeferredBufferSource buffers = new DeferredBufferSource();
        SubmitCtx prev = this.submitCtx;
        this.submitCtx = new SubmitCtx(renderState, collector, cameraState);
        poseStack.pushPose();
        try {
            render(e, e instanceof LivingEntity le ? le.yBodyRot : e.getYRot(), partialOf(renderState), poseStack, buffers, renderState.lightCoords);
        } finally {
            poseStack.popPose();
            this.submitCtx = prev;
        }
        buffers.submit(collector);
    }

    /** Desde una capa: volver a dibujar el modelo con otra textura, luz o color (como reRender de GeckoLib 4). */
    public void reRender(BakedGeoModel model, PoseStack poseStack, MultiBufferSource bufferSource, T animatable, RenderType renderType,
                         VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        LayerCtx c = LAYERS.get().peek();
        if (c == null || renderType == null) {
            return;
        }
        RenderPassInfo<SDRenderState> info = c.info();
        int color = net.minecraft.util.ARGB.colorFromFloat(alpha, red, green, blue);
        c.collector().order(1).submitCustomGeometry(info.poseStack(), renderType, (pose, vc) -> {
            PoseStack ps = info.poseStack();
            ps.pushPose();
            ps.last().set(pose);
            info.renderPosed(() -> info.model().render(info, vc, packedLight, packedOverlay, color));
            ps.popPose();
        });
    }

    /** Si se dibuja el modelo de GeckoLib (algunos efectos solo dibujan lo suyo). */
    protected boolean shouldDrawModel(SDRenderState renderState) {
        return true;
    }

    // ------------------------------------------------------------ cubos dibujados como bloques

    /** Huesos cuyos cubos se dibujan como un bloque (en GeckoLib 4: renderCube con renderSingleBlock). */
    protected boolean isBlockBone(T entity, software.bernie.geckolib.cache.model.GeoBone bone) {
        return false;
    }

    /** El bloque que se dibuja en esos cubos (null = ninguno). */
    protected net.minecraft.world.level.block.state.BlockState blockFor(T entity) {
        return null;
    }

    /** Tamano de cada bloque (1 = entero), alrededor de su centro. */
    protected float blockScale(T entity, float partialTick) {
        return 1;
    }

    private boolean blockBoneOrParent(T e, software.bernie.geckolib.cache.model.GeoBone bone) {
        for (software.bernie.geckolib.cache.model.GeoBone b = bone; b != null; b = b.parent()) {
            if (isBlockBone(e, b)) {
                return true;
            }
        }
        return false;
    }

    private void forEachBone(software.bernie.geckolib.cache.model.GeoBone[] bones, java.util.function.Consumer<software.bernie.geckolib.cache.model.GeoBone> c) {
        for (software.bernie.geckolib.cache.model.GeoBone b : bones) {
            c.accept(b);
            forEachBone(b.children(), c);
        }
    }

    void addBlockBones(RenderPassInfo<SDRenderState> info) {
        T e = entityOf(info.renderState());
        if (e == null) {
            return;
        }
        net.minecraft.world.level.block.state.BlockState block = blockFor(e);
        if (block == null || block.isAir()) {
            return;
        }
        float scale = blockScale(e, partialOf(info.renderState()));
        int light = info.packedLight();
        forEachBone(info.model().topLevelBones(), bone -> {
            if (bone instanceof software.bernie.geckolib.cache.model.cuboid.CuboidGeoBone cb && cb.cubes.length > 0 && blockBoneOrParent(e, bone)) {
                info.addPerBoneRender(bone, (pass, b, tasks) -> {
                    PoseStack ps = pass.poseStack();
                    for (software.bernie.geckolib.cache.model.cuboid.GeoCube cube : cb.cubes) {
                        double[] c = cubeCenter(cube);
                        ps.pushPose();
                        ps.translate(c[0] - 0.5D, c[1] - 0.5D, c[2] - 0.5D);
                        if (scale < 0.999F) {
                            float sc = Math.max(scale, 0.001F);
                            ps.translate(0.5D, 0.5D, 0.5D);
                            ps.scale(sc, sc, sc);
                            ps.translate(-0.5D, -0.5D, -0.5D);
                        }
                        tasks.submitBlock(ps, block, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 0);
                        ps.popPose();
                    }
                });
            }
        });
    }

    void hideBlockBones(RenderPassInfo<SDRenderState> info, BoneSnapshots snapshots) {
        T e = entityOf(info.renderState());
        if (e == null || blockFor(e) == null) {
            return;
        }
        forEachBone(info.model().topLevelBones(), bone -> {
            if (blockBoneOrParent(e, bone)) {
                snapshots.get(bone.name()).ifPresent(sn -> sn.skipRender(true));
            }
        });
    }

    private static double[] cubeCenter(software.bernie.geckolib.cache.model.cuboid.GeoCube cube) {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (software.bernie.geckolib.cache.model.GeoQuad quad : cube.quads()) {
            if (quad == null) {
                continue;
            }
            for (software.bernie.geckolib.cache.model.GeoVertex v : quad.vertices()) {
                if (v == null) {
                    continue;
                }
                minX = Math.min(minX, v.posX());
                minY = Math.min(minY, v.posY());
                minZ = Math.min(minZ, v.posZ());
                maxX = Math.max(maxX, v.posX());
                maxY = Math.max(maxY, v.posY());
                maxZ = Math.max(maxZ, v.posZ());
            }
        }
        if (!Double.isFinite(minX)) {
            return new double[]{0, 0, 0};
        }
        return new double[]{(minX + maxX) * 0.5D, (minY + maxY) * 0.5D, (minZ + maxZ) * 0.5D};
    }
}
