package com.starlevi.starfest;

import com.starlevi.starfest.block.ShowBlock;
import com.starlevi.starfest.block.ShowBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;

/** Bloques, objetos y la pestana de creativo de StarFest. */
public final class Registry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(StarFest.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StarFest.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, StarFest.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, StarFest.MOD_ID);

    public static final Map<ShowBlock.Kind, DeferredBlock<ShowBlock>> BLOCK = new EnumMap<>(ShowBlock.Kind.class);
    public static final Map<ShowBlock.Kind, DeferredHolder<BlockEntityType<?>, BlockEntityType<ShowBlockEntity>>> TYPE = new EnumMap<>(ShowBlock.Kind.class);

    static {
        for (ShowBlock.Kind k : ShowBlock.Kind.values()) {
            String id = k.name().toLowerCase(java.util.Locale.ROOT);
            BlockBehaviour.Properties p = BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(1.5f, 6.0f)
                    .sound(k == ShowBlock.Kind.PISO ? SoundType.GLASS : SoundType.METAL).noOcclusion();
            if (k == ShowBlock.Kind.PISO) {
                p = p.lightLevel(s -> 4).isViewBlocking((s, l, pos) -> false);
            }
            final BlockBehaviour.Properties props = p;
            DeferredBlock<ShowBlock> b = BLOCKS.register(id, () -> new ShowBlock(k, props));
            BLOCK.put(k, b);
            ITEMS.register(id, () -> new BlockItem(b.get(), new Item.Properties()));
            TYPE.put(k, BLOCK_ENTITIES.register(id, () -> BlockEntityType.Builder.<ShowBlockEntity>of(
                    (pos, state) -> new ShowBlockEntity(TYPE.get(k).get(), pos, state), b.get()).build(null)));
        }
        TABS.register("starfest", () -> CreativeModeTab.builder()
                .title(Component.literal("StarFest"))
                .icon(() -> new ItemStack(BLOCK.get(ShowBlock.Kind.FOCO).get()))
                .displayItems((params, out) -> BLOCK.values().forEach(b -> out.accept(b.get())))
                .build());
    }

    private Registry() {
    }

    public static BlockEntityType<ShowBlockEntity> typeFor(ShowBlock.Kind k) {
        return TYPE.get(k).get();
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        TABS.register(bus);
    }
}
