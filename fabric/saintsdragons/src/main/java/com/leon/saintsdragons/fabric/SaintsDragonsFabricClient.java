package com.leon.saintsdragons.fabric;

import com.leon.saintsdragons.client.init.CommonClientModEvents;
import com.leon.saintsdragons.client.compat.RealCameraCompatibility;
import com.leon.saintsdragons.common.registry.ModBlocks;
import com.leon.saintsdragons.common.registry.ModBlockEntities;
import com.leon.saintsdragons.client.model.block.DraconianNucleusModel;
import com.leon.saintsdragons.client.renderer.block.DraconianNucleusRenderer;
import com.leon.saintsdragons.client.model.block.DraconicCrucibleEntity;
import com.leon.saintsdragons.client.renderer.block.DraconicCrucibleRenderer;
import com.leon.saintsdragons.fabric.client.FabricDragonRideKeybinds;
import com.leon.saintsdragons.fabric.client.FabricDragonUI;
import com.leon.saintsdragons.fabric.client.event.FabricClientEventHandler;
import com.leon.saintsdragons.fabric.client.particle.FabricParticleRegistry;
import com.leon.saintsdragons.fabric.client.renderer.FabricDragonPartRenderer;
import com.leon.saintsdragons.fabric.entity.part.FabricPartEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.resources.Identifier;
import com.leon.saintsdragons.client.renderer.DragonAttachmentReloadListener;
import com.leon.saintsdragons.common.SaintsDragonsCommon;

public final class SaintsDragonsFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() {
                        return SaintsDragonsCommon.rl("rider_attachments");
                    }

                    @Override
                    public void onResourceManagerReload(ResourceManager resourceManager) {
                        new DragonAttachmentReloadListener().onResourceManagerReload(resourceManager);
                    }
                });
        com.leon.saintsdragons.client.particle.compat.LegacyParticleGroup.register();
        RealCameraCompatibility.register();
        CommonClientModEvents.registerEntityRenderers(EntityRendererRegistry::register);
        CommonClientModEvents.registerMenuScreens();
        net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap.putBlock(ModBlocks.DRACONIAN_NUCLEUS.get(), net.minecraft.client.renderer.chunk.ChunkSectionLayer.TRANSLUCENT);
        EntityModelLayerRegistry.registerModelLayer(
                DraconianNucleusModel.LAYER_LOCATION,
                DraconianNucleusModel::createBodyLayer);
        EntityModelLayerRegistry.registerModelLayer(
                DraconicCrucibleEntity.LAYER_LOCATION,
                DraconicCrucibleEntity::createBodyLayer);
        net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(
                ModBlockEntities.DRACONIAN_NUCLEUS.get(),
                DraconianNucleusRenderer::new);
        net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(
                ModBlockEntities.DRACONIC_CRUCIBLE.get(),
                DraconicCrucibleRenderer::new);
        EntityRendererRegistry.register(FabricPartEntities.DRAGON_PART, FabricDragonPartRenderer::new);
        FabricParticleRegistry.registerParticleFactories();
        FabricDragonRideKeybinds.init();
        FabricDragonUI.init();
        FabricClientEventHandler.init();
    }
}
