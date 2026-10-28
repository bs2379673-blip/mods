package com.annis.interact.mca;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

/** Acceso a Minecraft Comes Alive. Solo se carga si MCA esta instalado. */
public final class McaBridge {
    private McaBridge() {
    }

    public static boolean isVillager(Entity e) {
        return e instanceof VillagerEntityMCA;
    }

    public static int hearts(Entity e, PlayerEntity player) {
        if (e instanceof VillagerEntityMCA v) {
            return v.getVillagerBrain().getMemoriesForPlayer(player).getHearts();
        }
        return 0;
    }

    public static void addHearts(Entity e, PlayerEntity player, int amount) {
        if (amount != 0 && e instanceof VillagerEntityMCA v) {
            v.getVillagerBrain().getMemoriesForPlayer(player).modHearts(amount);
        }
    }

    /** Pareja (prometido, comprometido o casado) con ese jugador. */
    public static boolean isPartner(Entity e, PlayerEntity player) {
        if (e instanceof VillagerEntityMCA v) {
            var r = v.getRelationships();
            return r.isMarriedTo(player.getUuid()) || r.isEngagedWith(player.getUuid()) || r.isPromisedTo(player.getUuid());
        }
        return false;
    }

    /** Ninos y adolescentes: nada romantico. */
    public static boolean isMinor(Entity e) {
        if (e instanceof VillagerEntityMCA v) {
            AgeState a = v.getAgeState();
            return a != AgeState.ADULT && a != AgeState.UNASSIGNED;
        }
        return false;
    }

    /** Entrega lo que tiene en la mano (el anillo) por el sistema de regalos de MCA. */
    public static void giveHeldGift(Entity e, net.minecraft.server.network.ServerPlayerEntity player) {
        if (e instanceof VillagerEntityMCA v) {
            v.getRelationships().giveGift(player, v.getVillagerBrain().getMemoriesForPlayer(player));
        }
    }

    public static boolean isBusy(Entity e) {
        if (e instanceof VillagerEntityMCA v) {
            return v.isSleeping() || v.getVillagerBrain().isPanicking();
        }
        return false;
    }
}
