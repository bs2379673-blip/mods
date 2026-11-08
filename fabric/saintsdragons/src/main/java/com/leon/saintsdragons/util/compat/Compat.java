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
