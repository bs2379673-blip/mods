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

    /** El NBT propio de un item (antes com.leon.saintsdragons.util.compat.Compat.getTag(stack)); null si no tiene. Es una copia: guardar con setTag. */
    public static CompoundTag getTag(net.minecraft.world.item.ItemStack stack) {
        net.minecraft.world.item.component.CustomData d = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        return d == null ? null : d.copyTag();
    }

    public static CompoundTag getOrCreateTag(net.minecraft.world.item.ItemStack stack) {
        CompoundTag t = getTag(stack);
        return t == null ? new CompoundTag() : t;
    }

    public static boolean hasTag(net.minecraft.world.item.ItemStack stack) {
        return stack.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
    }

    public static void setTag(net.minecraft.world.item.ItemStack stack, CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            stack.remove(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        } else {
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
        }
    }

    public static String componentToJson(net.minecraft.network.chat.Component c) {
        return net.minecraft.network.chat.ComponentSerialization.CODEC.encodeStart(registries().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE), c)
                .result().map(Object::toString).orElse("\"\"");
    }

    public static net.minecraft.network.chat.Component componentFromJson(String json) {
        try {
            return net.minecraft.network.chat.ComponentSerialization.CODEC.parse(registries().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),
                    com.google.gson.JsonParser.parseString(json)).result().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /** Guardar / cargar una entidad en NBT (el juego ahora usa ValueOutput / ValueInput). */
    public static CompoundTag saveEntity(net.minecraft.world.entity.Entity e, CompoundTag into) {
        net.minecraft.world.level.storage.TagValueOutput out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                net.minecraft.util.ProblemReporter.DISCARDING, e.registryAccess());
        e.saveWithoutId(out);
        into.merge(out.buildResult());
        return into;
    }

    public static boolean saveEntityAsPassenger(net.minecraft.world.entity.Entity e, CompoundTag into) {
        net.minecraft.world.level.storage.TagValueOutput out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                net.minecraft.util.ProblemReporter.DISCARDING, e.registryAccess());
        boolean ok = e.save(out);
        into.merge(out.buildResult());
        return ok;
    }

    public static void loadEntity(net.minecraft.world.entity.Entity e, CompoundTag tag) {
        e.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, e.registryAccess(), tag));
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

    /** Port 1.21.11: textos por la red como JSON (FriendlyByteBuf ya no tiene writeComponent). */
    public static void writeComponent(net.minecraft.network.FriendlyByteBuf buf, net.minecraft.network.chat.Component c) {
        buf.writeUtf(componentToJson(c == null ? net.minecraft.network.chat.Component.empty() : c), 262144);
    }

    public static net.minecraft.network.chat.Component readComponent(net.minecraft.network.FriendlyByteBuf buf) {
        net.minecraft.network.chat.Component c = componentFromJson(buf.readUtf(262144));
        return c == null ? net.minecraft.network.chat.Component.empty() : c;
    }

    public static net.minecraft.network.chat.Component componentFromJson(com.google.gson.JsonElement json) {
        try {
            return net.minecraft.network.chat.ComponentSerialization.CODEC.parse(registries().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),
                    json).result().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    public static net.minecraft.world.item.ItemStack loadStack(java.util.Optional<CompoundTag> tag) {
        return loadStack(tag.orElse(null));
    }

    /** Port 1.21.11: la altura de paso ahora es un atributo. */
    public static void setMaxUpStep(net.minecraft.world.entity.LivingEntity e, float v) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance a = e.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT);
        if (a != null) {
            a.setBaseValue(v);
        }
    }

    /** Port 1.21.11: getMyRidingOffset de 1.20.1 (el jugador iba 0,35 mas abajo; el resto, 0). */
    public static double myRidingOffset(net.minecraft.world.entity.Entity passenger) {
        return passenger instanceof net.minecraft.world.entity.player.Player ? -0.35D : 0.0D;
    }

    /** Port 1.21.11: gastar un uso del item que tiene en la mano (o puesto). */
    public static void damageItem(net.minecraft.world.item.ItemStack stack, int amount, net.minecraft.world.entity.LivingEntity holder) {
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            if (holder.getItemBySlot(slot) == stack) {
                stack.hurtAndBreak(amount, holder, slot);
                return;
            }
        }
        stack.hurtAndBreak(amount, holder, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
    }

    /** Port 1.21.11: el inventario de un SimpleContainer en NBT (como createTag / fromTag de 1.20.1). */
    public static net.minecraft.nbt.ListTag containerToTag(net.minecraft.world.SimpleContainer c) {
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < c.getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack st = c.getItem(i);
            if (!st.isEmpty()) {
                CompoundTag t = saveStack(st);
                t.putByte("Slot", (byte) i);
                list.add(t);
            }
        }
        return list;
    }

    public static void containerFromTag(net.minecraft.world.SimpleContainer c, net.minecraft.nbt.ListTag list) {
        c.clearContent();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompoundOrEmpty(i);
            int slot = t.getByteOr("Slot", (byte) -1) & 255;
            if (t.contains("Slot") && slot < c.getContainerSize()) {
                c.setItem(slot, loadStack(t));
            } else if (!t.contains("Slot")) {
                c.addItem(loadStack(t));
            }
        }
    }

    /** Port 1.21.11: los permisos de comandos. */
    public static boolean isOp(net.minecraft.commands.CommandSourceStack source) {
        return net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS).test(source);
    }

    /** Port 1.21.11: datos guardados del mundo con la forma de 1.20.1 (load(tag) / save(tag)). */
    public static <T extends net.minecraft.world.level.saveddata.SavedData> net.minecraft.world.level.saveddata.SavedDataType<T> savedData(
            String id, java.util.function.Supplier<T> ctor, java.util.function.Function<CompoundTag, T> load,
            java.util.function.BiFunction<T, CompoundTag, CompoundTag> save) {
        return new net.minecraft.world.level.saveddata.SavedDataType<>(id, ctor,
                CompoundTag.CODEC.xmap(load, t -> save.apply(t, new CompoundTag())), null);
    }
}
