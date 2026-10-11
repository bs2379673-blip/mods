package com.tunombre.dancers;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Dancers: entidad "Dancer" que muestra un modelo propio (assets/dancers/models/<nombre>.json) y reproduce animaciones
 * en formato Epic Fight (cualquier recurso animmodels/animations/<ruta>.json de cualquier mod, p. ej. mikuevent).
 * Todo el calculo es propio: no necesita Epic Fight. Debe estar en servidor y clientes.
 */
@Mod(Dancers.MODID)
public class Dancers {
    public static final String MODID = "dancers";
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
    public static final RegistryObject<EntityType<DancerEntity>> DANCER = ENTITIES.register("dancer",
            () -> EntityType.Builder.of(DancerEntity::new, MobCategory.MISC).sized(0.6f, 1.8f).clientTrackingRange(16).updateInterval(3).fireImmune().build("dancer"));

    public Dancers() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ENTITIES.register(bus);
        bus.addListener(Dancers::onAttributes);
        MinecraftForge.EVENT_BUS.addListener(DancerCommand::onRegisterCommands);
    }

    private static void onAttributes(EntityAttributeCreationEvent e) {
        e.put(DANCER.get(), Mob.createMobAttributes().build());
    }
}
