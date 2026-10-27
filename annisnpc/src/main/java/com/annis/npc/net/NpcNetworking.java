package com.annis.npc.net;

import com.annis.npc.entity.AnnisNpcEntity;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class NpcNetworking {
    private static final double MAX_DISTANCE_SQ = 128.0 * 128.0;

    private NpcNetworking() {
    }

    /** Se llama en cliente y servidor. */
    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(NpcActionPayload.ID, NpcActionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenEditorPayload.ID, OpenEditorPayload.CODEC);
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(NpcActionPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            if (!player.hasPermissionLevel(2)) {
                player.sendMessage(Text.literal("No tenes permiso para editar NPC"), true);
                return;
            }
            Entity entity = player.getWorld().getEntityById(payload.entityId());
            if (!(entity instanceof AnnisNpcEntity npc) || player.squaredDistanceTo(npc) > MAX_DISTANCE_SQ) {
                return;
            }
            switch (payload.action()) {
                case NpcActionPayload.SET_SKIN -> {
                    if (payload.text().length() <= 512) {
                        npc.setSkin(payload.text());
                        npc.setSlim(payload.flag());
                    }
                }
                case NpcActionPayload.PLAY_EMOTE -> npc.playEmote(payload.text(), Math.max(0, payload.number()) * 20);
                case NpcActionPayload.STOP_EMOTE -> npc.stopEmote();
                case NpcActionPayload.SET_STATIONARY -> npc.setStationary(payload.flag());
                case NpcActionPayload.SET_NAME -> {
                    String name = payload.text().trim();
                    if (name.length() > 48) {
                        name = name.substring(0, 48);
                    }
                    npc.setCustomName(name.isEmpty() ? null : Text.literal(name));
                    npc.setCustomNameVisible(!name.isEmpty());
                }
                case NpcActionPayload.REMOVE -> npc.discard();
                case NpcActionPayload.SET_PROTECTED -> npc.setProtected(payload.flag());
                default -> {
                }
            }
        });
    }

    public static void openEditor(ServerPlayerEntity player, AnnisNpcEntity npc) {
        ServerPlayNetworking.send(player, new OpenEditorPayload(npc.getId()));
    }
}
