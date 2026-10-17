package com.tunombre.crafteos;

import com.mojang.serialization.Codec;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

@Mod(Crafteos.MODID)
public class Crafteos {
    public static final String MODID = "crafteos";
    private static final DeferredRegister<Codec<? extends IGlobalLootModifier>> LOOT = DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, MODID);

    public Crafteos() {
        LOOT.register("sin_prohibidos", BanLoot.CODEC);
        LOOT.register(FMLJavaModLoadingContext.get().getModEventBus());
        Bans.load();
        Net.init();
        MinecraftForge.EVENT_BUS.register(Recipes.class);
        MinecraftForge.EVENT_BUS.register(Cmds.class);
        MinecraftForge.EVENT_BUS.register(Bans.class);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MinecraftForge.EVENT_BUS.register(com.tunombre.crafteos.client.ClientBans.class));
    }
}
