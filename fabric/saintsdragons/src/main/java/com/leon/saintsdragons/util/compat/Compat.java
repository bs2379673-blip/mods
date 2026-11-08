package com.leon.saintsdragons.util.compat;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;

import java.util.UUID;

/** Port 1.21.11: ayudas para lo que cambio de forma en el juego (UUIDs en NBT, dueños, etc.). */
public final class Compat {
    private Compat() {
    }

    /** com.leon.saintsdragons.util.compat.Compat.OPTIONAL_UUID ya no existe: uno propio (se registra al iniciar). */
    public static final net.minecraft.network.syncher.EntityDataSerializer<java.util.Optional<UUID>> OPTIONAL_UUID =
            net.minecraft.network.syncher.EntityDataSerializer.forValueType(UUIDUtil.STREAM_CODEC.apply(net.minecraft.network.codec.ByteBufCodecs::optional));

    public static void registerSerializers() {
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricTrackedDataRegistry.register(
                net.minecraft.resources.Identifier.fromNamespaceAndPath("saintsdragons", "optional_uuid"), OPTIONAL_UUID);
    }

    private static volatile net.minecraft.server.MinecraftServer server;

    public static void setServer(net.minecraft.server.MinecraftServer s) {
        server = s;
    }

    /** Los registros del juego (para guardar items, encantamientos, etc.). */
    public static net.minecraft.core.HolderLookup.Provider registries() {
        net.minecraft.server.MinecraftServer s = server;
        if (s != null) {
            return s.registryAccess();
        }
        return ClientRegistries.get();
    }

    public static CompoundTag saveStack(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) {
            return new CompoundTag();
        }
        net.minecraft.nbt.Tag t = net.minecraft.world.item.ItemStack.CODEC.encodeStart(registries().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), stack)
                .result().orElse(new CompoundTag());
        return t instanceof CompoundTag c ? c : new CompoundTag();
    }

    public static CompoundTag saveStack(net.minecraft.world.item.ItemStack stack, CompoundTag into) {
        into.merge(saveStack(stack));
        return into;
    }

    public static net.minecraft.world.item.ItemStack loadStack(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }
        return net.minecraft.world.item.ItemStack.CODEC.parse(registries().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), tag)
                .result().orElse(net.minecraft.world.item.ItemStack.EMPTY);
    }

    public static net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> enchantment(
            net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> key) {
        return registries().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(key);
    }

    public static int enchantLevel(net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> key,
                                   net.minecraft.world.item.ItemStack stack) {
        try {
            return net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(enchantment(key), stack);
        } catch (Exception e) {
            return 0;
        }
    }

    public static UUID ownerUUID(OwnableEntity e) {
        EntityReference<LivingEntity> r = e.getOwnerReference();
        return r == null ? null : r.getUUID();
    }

    public static void setOwnerUUID(TamableAnimal e, UUID id) {
        e.setOwnerReference(id == null ? null : EntityReference.of(id));
    }

    /** Los modificadores de atributos ahora se identifican con un Identifier (antes UUID). */
    public static net.minecraft.resources.Identifier modifierId(UUID id) {
        return net.minecraft.resources.Identifier.fromNamespaceAndPath("saintsdragons", id.toString());
    }

    /** Los modificadores de un item para una ranura (antes Multimap por ranura). */
    public static com.google.common.collect.Multimap<net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute>, net.minecraft.world.entity.ai.attributes.AttributeModifier>
    attributeModifiers(net.minecraft.world.item.ItemStack stack, net.minecraft.world.entity.EquipmentSlot slot) {
        com.google.common.collect.Multimap<net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute>, net.minecraft.world.entity.ai.attributes.AttributeModifier> out =
                com.google.common.collect.HashMultimap.create();
        stack.forEachModifier(slot, (attr, mod) -> out.put(attr, mod));
        return out;
    }

    public static boolean hasUUID(CompoundTag t, String key) {
        return t.read(key, UUIDUtil.CODEC).isPresent();
    }

    public static UUID getUUID(CompoundTag t, String key) {
        return t.read(key, UUIDUtil.CODEC).orElse(null);
    }

    public static void putUUID(CompoundTag t, String key, UUID id) {
        if (id != null) {
            t.store(key, UUIDUtil.CODEC, id);
        }
    }
}
