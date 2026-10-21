package com.tunombre.infectados.client;

import com.tunombre.infectados.Infectados;
import com.tunombre.infectados.mob.Ganado;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.client.forgeevent.PatchedRenderersEvent;
import yesman.epicfight.api.client.model.Meshes;
import yesman.epicfight.client.mesh.HumanoidMesh;

import java.util.function.Function;

/** Solo cliente: mallas 3D propias, renderers, capas (plaga, pelo de la Witch), lengua, motor,  */
public final class ClientSetup {
    public static final ModelLayerLocation PLAGA_LAYER = new ModelLayerLocation(new ResourceLocation(Infectados.MODID, "plaga"), "main");
    private static AssetAccessor<HumanoidMesh>[] GANADO;
    private static AssetAccessor<HumanoidMesh> MOTOSIERRA, SMOKER, BOOMER, WITCH;

    private ClientSetup() { }

    private static AssetAccessor<HumanoidMesh> mesh(String name) {
        return Meshes.MeshAccessor.create(Infectados.MODID, "entity/" + name, l -> l.loadSkinnedMesh(HumanoidMesh::new));
    }

    @SuppressWarnings("unchecked")
    public static void init(IEventBus bus) {
        GANADO = new AssetAccessor[]{mesh("ganado1"), mesh("ganado2"), mesh("ganado3"), mesh("ganado4")};
        MOTOSIERRA = mesh("motosierra");
        SMOKER = mesh("smoker");
        BOOMER = mesh("boomer");
        WITCH = mesh("witch");
        bus.addListener((EntityRenderersEvent.RegisterLayerDefinitions e) -> e.registerLayerDefinition(PLAGA_LAYER, PlagaModel::create));
        bus.addListener(ClientSetup::onRenderers);
        bus.addListener(ClientSetup::onPatched);
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.ClientPlayerNetworkEvent.Clone e) -> { Infectados.clientHeld = false; });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingIn e) -> { Infectados.clientHeld = false; });
        MinecraftForge.EVENT_BUS.addListener(HoldFx::onCamera);
        MinecraftForge.EVENT_BUS.addListener(ClientFx::renderStage);
        MinecraftForge.EVENT_BUS.addListener(ClientFx::clientTick);
    }

    private static void onRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(Infectados.GANADO.get(), c -> new InfVanillaRenderer(c, "ganado1"));
        e.registerEntityRenderer(Infectados.MOTOSIERRA.get(), c -> new InfVanillaRenderer(c, "motosierra"));
        e.registerEntityRenderer(Infectados.SMOKER.get(), c -> new InfVanillaRenderer(c, "smoker"));
        e.registerEntityRenderer(Infectados.BOOMER.get(), c -> new InfVanillaRenderer(c, "boomer"));
        e.registerEntityRenderer(Infectados.WITCH.get(), c -> new InfVanillaRenderer(c, "witch"));
    }

    private static void onPatched(PatchedRenderersEvent.Add e) {
        EntityRendererProvider.Context ctx = e.getContext();
        add(e, ctx, Infectados.GANADO.get(), GANADO[0], x -> x instanceof Ganado g ? GANADO[g.variant() - 1] : GANADO[0], 1F, 1F, 1F, true, false);
        add(e, ctx, Infectados.MOTOSIERRA.get(), MOTOSIERRA, x -> MOTOSIERRA, 1.32F, 1.32F, 1.32F, false, false);
        add(e, ctx, Infectados.SMOKER.get(), SMOKER, x -> SMOKER, 1F, 1.06F, 1F, false, false);
        add(e, ctx, Infectados.BOOMER.get(), BOOMER, x -> BOOMER, 1.12F, 1.05F, 1.12F, false, false);
        add(e, ctx, Infectados.WITCH.get(), WITCH, x -> WITCH, 0.96F, 0.96F, 0.96F, false, true);
    }

    private static void add(PatchedRenderersEvent.Add e, EntityRendererProvider.Context ctx, EntityType<?> type, AssetAccessor<HumanoidMesh> def,
                            Function<LivingEntity, AssetAccessor<HumanoidMesh>> pick, float sx, float sy, float sz, boolean plaga, boolean hair) {
        e.addPatchedEntityRenderer(type, t -> {
            InfRenderer r = new InfRenderer(def, pick, ctx, t, sx, sy, sz);
            r.initLayerLast(ctx, t);
            if (plaga) r.addCustomLayer(new PlagaLayer());
            if (hair) r.addCustomLayer(new WitchHairLayer());
            return r;
        });
    }
}
