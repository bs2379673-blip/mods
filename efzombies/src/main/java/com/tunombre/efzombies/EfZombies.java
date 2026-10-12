package com.tunombre.efzombies;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.types.MovementAnimation;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.forgeevent.EntityPatchRegistryEvent;
import yesman.epicfight.gameasset.Armatures;
import yesman.epicfight.world.entity.ai.attribute.EpicFightAttributes;

/**
 * EF Zombies 2.0
 * - Animaciones propias para los zombis de Epic Fight (andar, perseguir, reposo; variantes por zombi). Ver ZombieClient.
 * - Nuevo mob: el TREPADOR (efzombies:trepador). Trepa paredes, se cuelga del techo y cae sobre el jugador. Ver Trepador.
 * Desde 2.0 debe estar en el SERVIDOR y en los CLIENTES (agrega un mob).
 */
@Mod(EfZombies.MODID)
public class EfZombies {
    public static final String MODID = "efzombies";

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    public static final RegistryObject<EntityType<Trepador>> TREPADOR = ENTITIES.register("trepador",
            () -> EntityType.Builder.<Trepador>of(Trepador::new, MobCategory.MONSTER)
                    .sized(Trepador.WIDTH, Trepador.HEIGHT)
                    .clientTrackingRange(10)
                    .build(MODID + ":trepador"));

    public static final RegistryObject<Item> TREPADOR_EGG = ITEMS.register("trepador_spawn_egg",
            () -> new ForgeSpawnEggItem(TREPADOR, 0x4A0C10, 0xD88A96, new Item.Properties()));

    // ---- animaciones de zombi normal ----
    public static final AnimationManager.AnimationAccessor<MovementAnimation>[] ZW = newMove(7), ZWH = newMove(7), ZC = newMove(7), ZCH = newMove(7);
    public static AnimationManager.AnimationAccessor<MovementAnimation> ZQW, ZQC;
    public static AnimationManager.AnimationAccessor<StaticAnimation> ZQI;
    public static final AnimationManager.AnimationAccessor<StaticAnimation>[] ZI = newStatic(6), ZIH = newStatic(6);

    // ---- animaciones del trepador ----
    /** pared: trepando / quieto. techo: gateando / quieto. suelo: gateo, carrera, reposo. caida. */
    public static AnimationManager.AnimationAccessor<StaticAnimation> T_WALL, T_WALL_IDLE, T_CEIL, T_CEIL_IDLE, T_IDLE, T_FALL;
    public static AnimationManager.AnimationAccessor<MovementAnimation> T_WALK, T_RUN;

    @SuppressWarnings("unchecked")
    private static AnimationManager.AnimationAccessor<MovementAnimation>[] newMove(int n) { return new AnimationManager.AnimationAccessor[n]; }

    @SuppressWarnings("unchecked")
    private static AnimationManager.AnimationAccessor<StaticAnimation>[] newStatic(int n) { return new AnimationManager.AnimationAccessor[n]; }

    public EfZombies() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ENTITIES.register(bus);
        ITEMS.register(bus);
        bus.addListener(EfZombies::onAnimations);
        bus.addListener(EfZombies::onAttributes);
        bus.addListener(EfZombies::onAttributesModify);
        bus.addListener(EfZombies::onPatches);
        bus.addListener(EfZombies::onSpawnPlacement);
        bus.addListener(EfZombies::onCreativeTab);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            MinecraftForge.EVENT_BUS.register(new ZombieClient());
            ClientSetup.init(bus);
        });
    }

    private static void onAnimations(AnimationManager.AnimationRegistryEvent event) {
        event.newBuilder(MODID, EfZombies::build);
    }

    private static void build(AnimationManager.AnimationBuilder b) {
        for (int i = 0; i < 7; i++) {
            ZW[i] = move(b, "zombie/zw" + (i + 1));
            ZWH[i] = move(b, "zombie/zw" + (i + 1) + "h");
            ZC[i] = move(b, "zombie/zc" + (i + 1));
            ZCH[i] = move(b, "zombie/zc" + (i + 1) + "h");
        }
        for (int i = 0; i < 6; i++) {
            ZI[i] = loop(b, "zombie/zi" + (i + 1), 0.25F);
            ZIH[i] = loop(b, "zombie/zi" + (i + 1) + "h", 0.25F);
        }
        ZQW = move(b, "zombie/zwq");
        ZQC = move(b, "zombie/zcq");
        ZQI = loop(b, "zombie/ziq", 0.25F);

        T_WALL = loop(b, "trepador/tcw", 0.15F);
        T_WALL_IDLE = loop(b, "trepador/tcwi", 0.2F);
        T_CEIL = loop(b, "trepador/tcc", 0.15F);
        T_CEIL_IDLE = loop(b, "trepador/tcci", 0.2F);
        T_IDLE = loop(b, "trepador/tgi", 0.2F);
        T_FALL = loop(b, "trepador/tfall", 0.12F);
        T_WALK = move(b, "trepador/tgw");
        T_RUN = move(b, "trepador/tgc");
    }

    private static AnimationManager.AnimationAccessor<MovementAnimation> move(AnimationManager.AnimationBuilder b, String name) {
        return b.nextAccessor("biped/" + name, acc -> new MovementAnimation(0.2F, true, acc, Armatures.BIPED));
    }

    private static AnimationManager.AnimationAccessor<StaticAnimation> loop(AnimationManager.AnimationBuilder b, String name, float blend) {
        return b.nextAccessor("biped/" + name, acc -> new StaticAnimation(blend, true, acc, Armatures.BIPED));
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(TREPADOR.get(), Trepador.createAttributes().build());
    }

    /** los atributos que Epic Fight da a los humanoides (sin ellos su parche falla al leerlos) */
    private static void onAttributesModify(EntityAttributeModificationEvent event) {
        EntityType<Trepador> t = TREPADOR.get();
        event.add(t, EpicFightAttributes.WEIGHT.get());
        event.add(t, EpicFightAttributes.ARMOR_NEGATION.get());
        event.add(t, EpicFightAttributes.IMPACT.get(), 1.0D);
        event.add(t, EpicFightAttributes.MAX_STRIKES.get());
        event.add(t, EpicFightAttributes.STUN_ARMOR.get());
        event.add(t, EpicFightAttributes.OFFHAND_ATTACK_SPEED.get());
        event.add(t, EpicFightAttributes.OFFHAND_MAX_STRIKES.get());
        event.add(t, EpicFightAttributes.OFFHAND_ARMOR_NEGATION.get());
        event.add(t, EpicFightAttributes.OFFHAND_IMPACT.get());
    }

    private static void onPatches(EntityPatchRegistryEvent event) {
        event.getTypeEntry().put(TREPADOR.get(), e -> TrepadorPatch::new);
    }

    private static void onSpawnPlacement(SpawnPlacementRegisterEvent event) {
        event.register(TREPADOR.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Monster::checkMonsterSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) event.accept(TREPADOR_EGG);
    }
}
