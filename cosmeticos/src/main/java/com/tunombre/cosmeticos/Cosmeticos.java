package com.tunombre.cosmeticos;

import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

@Mod(Cosmeticos.MODID)
public class Cosmeticos {
    public static final String MODID = "cosmeticos";
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, MODID);
    public static final List<RegistryObject<MobEffect>> OBJS = new ArrayList<>();

    static {
        for (Cosmetics.Def d : Cosmetics.ALL) OBJS.add(EFFECTS.register(d.id(), () -> new CosmeticEffect(d.index(), d.color())));
    }

    public Cosmeticos() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        EFFECTS.register(bus);
        Net.init();
        MinecraftForge.EVENT_BUS.register(Server.class);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.tunombre.cosmeticos.client.CosClient.init());
    }

    public static MobEffect effect(int index) { return OBJS.get(index).get(); }
}
