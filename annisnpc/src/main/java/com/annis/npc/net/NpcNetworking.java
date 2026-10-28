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
        PayloadTypeRegistry.playS2C().register(OpenDialogPayload.ID, OpenDialogPayload.CODEC);
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
                case NpcActionPayload.SET_MODE -> npc.setMode(payload.number(), player.getUuid());
                case NpcActionPayload.SET_DIALOG -> npc.setDialog(payload.text());
                case NpcActionPayload.SET_FIGURA -> npc.setFigura(payload.text());
                case NpcActionPayload.ROUTINE_ADD -> {
                    net.minecraft.util.math.Vec3d at = payload.flag() ? player.getPos() : npc.getPos();
                    if (!npc.addStop(at.x, at.y, at.z, payload.text(), payload.number())) {
                        player.sendMessage(Text.literal("Maximo de paradas alcanzado"), true);
                    }
                }
                case NpcActionPayload.ROUTINE_CLEAR -> npc.clearStops();
                case NpcActionPayload.ROUTINE_REMOVE_LAST -> npc.removeLastStop();
                case NpcActionPayload.ROUTINE_ENABLE -> npc.setRoutineOn(payload.flag());
                case NpcActionPayload.CLEAR_EQUIP -> npc.clearEquipment(player);
                case NpcActionPayload.SET_SCENE -> npc.setScene(payload.text());
                case NpcActionPayload.SYNC_SCENE -> {
                    int n = syncScene(npc);
                    player.sendMessage(Text.literal(n + " NPC sincronizados"), true);
                }
                case NpcActionPayload.SET_ACCESSORY -> npc.setAccessory(payload.number(), payload.text());
                case NpcActionPayload.SET_ALL_ACCESSORIES -> npc.setAccessories(payload.text());
                case NpcActionPayload.SET_MUSIC -> npc.setMusic(payload.text());
                case NpcActionPayload.STOP_MUSIC -> stopMusic(npc);
                case NpcActionPayload.SET_JOINTED -> npc.setJointed(payload.flag());
                case NpcActionPayload.COPY_FROM -> {
                    Entity source = player.getWorld().getEntityById(payload.number());
                    if (source instanceof AnnisNpcEntity other && other != npc) {
                        npc.copyFrom(other);
                        player.sendMessage(Text.literal("Configuracion pegada"), true);
                    }
                }
                default -> {
                }
            }
        });
    }

    /**
     * Reinicia en el mismo tick el emote/actividad de todos los NPC del grupo de este NPC
     * (o solo este si no tiene grupo). Devuelve cuantos se sincronizaron.
     */
    public static int syncScene(AnnisNpcEntity npc) {
        int tick = (int) npc.getWorld().getTime();
        String group = npc.getScene();
        if (group.isEmpty()) {
            npc.restartEmote(tick);
            playMusic(npc, npc.getMusic());
            return 1;
        }
        java.util.List<AnnisNpcEntity> members = npc.getWorld().getEntitiesByClass(AnnisNpcEntity.class,
                npc.getBoundingBox().expand(160.0), other -> group.equals(other.getScene()));
        String music = npc.getMusic();
        for (AnnisNpcEntity other : members) {
            other.restartEmote(tick);
            if (music.isEmpty()) {
                music = other.getMusic();
            }
        }
        playMusic(npc, music);
        return members.size();
    }

    /** Hace sonar la musica de la escena desde el NPC (se oye a unos 64 bloques), cortando la anterior. */
    public static void playMusic(AnnisNpcEntity npc, String id) {
        if (id == null || id.isEmpty() || !(npc.getWorld() instanceof net.minecraft.server.world.ServerWorld world)) {
            return;
        }
        net.minecraft.util.Identifier ident = net.minecraft.util.Identifier.tryParse(id);
        if (ident == null) {
            return;
        }
        stopMusicId(world, npc, ident);
        world.playSound(null, npc.getX(), npc.getY() + 1.0, npc.getZ(),
                net.minecraft.sound.SoundEvent.of(ident), net.minecraft.sound.SoundCategory.RECORDS, 4.0f, 1.0f);
    }

    public static void stopMusic(AnnisNpcEntity npc) {
        net.minecraft.util.Identifier ident = net.minecraft.util.Identifier.tryParse(npc.getMusic());
        if (ident != null && npc.getWorld() instanceof net.minecraft.server.world.ServerWorld world) {
            stopMusicId(world, npc, ident);
        }
    }

    private static void stopMusicId(net.minecraft.server.world.ServerWorld world, AnnisNpcEntity npc, net.minecraft.util.Identifier ident) {
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (p.squaredDistanceTo(npc) < 160.0 * 160.0) {
                p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.StopSoundS2CPacket(ident, net.minecraft.sound.SoundCategory.RECORDS));
            }
        }
    }

    public static void openDialog(ServerPlayerEntity player, AnnisNpcEntity npc) {
        ServerPlayNetworking.send(player, new OpenDialogPayload(npc.getId()));
    }

    public static void openEditor(ServerPlayerEntity player, AnnisNpcEntity npc) {
        ServerPlayNetworking.send(player, new OpenEditorPayload(npc.getId()));
    }
}
