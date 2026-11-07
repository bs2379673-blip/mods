package com.starlevi.starfest;

import com.starlevi.starfest.block.ShowBlock;
import com.starlevi.starfest.block.ShowBlockEntity;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/** Bloques, objetos y la pestaña de creativo de StarFest. */
public final class Registry {
    public static final Map<ShowBlock.Kind, ShowBlock> BLOCK = new EnumMap<>(ShowBlock.Kind.class);
    public static final Map<ShowBlock.Kind, BlockEntityType<ShowBlockEntity>> TYPE = new EnumMap<>(ShowBlock.Kind.class);

    private Registry() {
    }

    public static Identifier id(String p) {
        return Identifier.fromNamespaceAndPath(StarFest.MOD_ID, p);
    }

    public static BlockEntityType<ShowBlockEntity> typeFor(ShowBlock.Kind k) {
        return TYPE.get(k);
    }

    public static void register() {
        for (ShowBlock.Kind k : ShowBlock.Kind.values()) {
            String name = k.name().toLowerCase(Locale.ROOT);
            ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id(name));
            BlockBehaviour.Properties p = BlockBehaviour.Properties.of().setId(key).mapColor(MapColor.COLOR_BLACK).strength(1.5f, 6.0f)
                    .sound(k == ShowBlock.Kind.PISO ? SoundType.GLASS : SoundType.METAL).noOcclusion();
            if (k == ShowBlock.Kind.PISO) {
                p = p.lightLevel(s -> 4).isViewBlocking((s, l, pos) -> false);
            }
            ShowBlock b = net.minecraft.core.Registry.register(BuiltInRegistries.BLOCK, key, new ShowBlock(k, p));
            BLOCK.put(k, b);
            net.minecraft.core.Registry.register(BuiltInRegistries.ITEM, id(name),
                    new BlockItem(b, new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id(name))).useBlockDescriptionPrefix()));
            final ShowBlock.Kind kind = k;
            TYPE.put(k, net.minecraft.core.Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id(name),
                    FabricBlockEntityTypeBuilder.<ShowBlockEntity>create((pos, state) -> new ShowBlockEntity(TYPE.get(kind), pos, state), b).build()));
        }
        net.minecraft.core.Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id("starfest"), FabricItemGroup.builder()
                .title(Component.literal("StarFest"))
                .icon(() -> new ItemStack(BLOCK.get(ShowBlock.Kind.FOCO)))
                .displayItems((params, out) -> BLOCK.values().forEach(out::accept))
                .build());
    }
}
