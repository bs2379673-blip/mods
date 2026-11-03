package com.starlevi.starriders;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** Entidad del fenix, el silbato, los datos del jugador y la pestana de creativo. */
public final class ModRegistry {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StarRiders.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, StarRiders.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, StarRiders.MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, StarRiders.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<PhoenixEntity>> PHOENIX = ENTITIES.register("fenix",
            () -> EntityType.Builder.<PhoenixEntity>of(PhoenixEntity::new, MobCategory.MISC).sized(1.7f, 2.0f)
                    .clientTrackingRange(16).updateInterval(1).fireImmune().build("fenix"));

    public static final DeferredHolder<EntityType<?>, EntityType<GuardianEntity>> GUARDIAN = ENTITIES.register("guardian",
            () -> EntityType.Builder.<GuardianEntity>of(GuardianEntity::new, MobCategory.MISC).sized(3.0f, 4.6f)
                    .clientTrackingRange(16).updateInterval(1).build("guardian"));

    public static final DeferredHolder<EntityType<?>, EntityType<SerpentEntity>> SERPENT = ENTITIES.register("serpiente",
            () -> EntityType.Builder.<SerpentEntity>of(SerpentEntity::new, MobCategory.MISC).sized(2.4f, 1.6f)
                    .clientTrackingRange(16).updateInterval(1).build("serpiente"));

    public static final DeferredItem<WhistleItem> WHISTLE = ITEMS.register("silbato",
            () -> new WhistleItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    public static final Supplier<AttachmentType<RiderData>> DATA = ATTACHMENTS.register("monturas",
            () -> AttachmentType.builder(RiderData::new).serialize(RiderData.CODEC).copyOnDeath().build());

    static {
        TABS.register("starriders", () -> CreativeModeTab.builder()
                .title(Component.literal("StarRiders"))
                .icon(() -> new ItemStack(WHISTLE.get()))
                .displayItems((params, out) -> out.accept(WHISTLE.get()))
                .build());
    }

    private ModRegistry() {
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        ENTITIES.register(bus);
        TABS.register(bus);
        ATTACHMENTS.register(bus);
    }
}
