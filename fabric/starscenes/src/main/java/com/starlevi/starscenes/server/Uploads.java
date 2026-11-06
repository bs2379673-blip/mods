package com.starlevi.starscenes.server;

import com.starlevi.starscenes.net.Net;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Envia los modelos y skins a quien los pide, de a pedazos (sin trabar la conexion). */
public final class Uploads {
    private static final int PIECE = 60000;
    private static final int PER_TICK = 6;

    private record Job(String hash, byte[] data, int[] sent) {
    }

    private static final Map<UUID, Deque<Job>> QUEUE = new HashMap<>();

    private Uploads() {
    }

    public static synchronized void need(ServerPlayer p, String hash) {
        byte[] data = SceneLibrary.byHash(hash);
        if (data == null) {
            return;
        }
        Deque<Job> q = QUEUE.computeIfAbsent(p.getUUID(), u -> new ArrayDeque<>());
        for (Job j : q) {
            if (j.hash().equals(hash)) {
                return;
            }
        }
        q.add(new Job(hash, data, new int[1]));
    }

    public static synchronized void left(ServerPlayer p) {
        QUEUE.remove(p.getUUID());
    }

    public static synchronized void tick(MinecraftServer server) {
        QUEUE.entrySet().removeIf(en -> {
            ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
            if (p == null) {
                return true;
            }
            Deque<Job> q = en.getValue();
            for (int n = 0; n < PER_TICK && !q.isEmpty(); n++) {
                Job j = q.peek();
                int off = j.sent()[0];
                int len = Math.min(PIECE, j.data().length - off);
                byte[] part = new byte[len];
                System.arraycopy(j.data(), off, part, 0, len);
                ServerPlayNetworking.send(p, new Net.Chunk(j.hash(), off, j.data().length, part));
                j.sent()[0] += len;
                if (j.sent()[0] >= j.data().length) {
                    q.poll();
                }
            }
            return q.isEmpty();
        });
    }
}
