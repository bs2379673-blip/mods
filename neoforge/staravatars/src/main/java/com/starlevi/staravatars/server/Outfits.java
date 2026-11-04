package com.starlevi.staravatars.server;

import com.starlevi.staravatars.net.Net;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

/** Que personaje usa cada jugador (se guarda en el jugador y sobrevive a la muerte). */
public final class Outfits {
    private static final String KEY = "staravatars_personaje";

    private Outfits() {
    }

    private static CompoundTag data(Player p) {
        CompoundTag root = p.getPersistentData();
        CompoundTag keep = root.getCompound(Player.PERSISTED_NBT_TAG);
        root.put(Player.PERSISTED_NBT_TAG, keep);
        return keep;
    }

    public static String of(Player p) {
        return data(p).getString(KEY);
    }

    public static void set(ServerPlayer p, String avatar) {
        data(p).putString(KEY, avatar == null ? "" : avatar);
        PacketDistributor.sendToAllPlayers(new Net.Outfit(p.getUUID(), of(p)));
    }

    /** Desde el menu del jugador. */
    public static void choose(ServerPlayer p, String avatar) {
        if (!avatar.isEmpty() && !AvatarLibrary.exists(avatar)) {
            p.displayClientMessage(Component.literal("No existe el personaje " + avatar), true);
            return;
        }
        set(p, avatar);
    }

    /** Al entrar: la lista de personajes y lo que usa cada uno de los que estan conectados. */
    public static void join(ServerPlayer p) {
        PacketDistributor.sendToPlayer(p, new Net.Index(AvatarLibrary.all()));
        for (ServerPlayer o : p.server.getPlayerList().getPlayers()) {
            String a = of(o);
            if (!a.isEmpty()) {
                PacketDistributor.sendToPlayer(p, new Net.Outfit(o.getUUID(), a));
            }
        }
        String mine = of(p);
        if (!mine.isEmpty()) {
            PacketDistributor.sendToAllPlayers(new Net.Outfit(p.getUUID(), mine));
        }
    }
}
