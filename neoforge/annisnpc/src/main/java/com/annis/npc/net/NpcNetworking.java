package com.annis.npc.net;

import com.annis.npc.entity.AnnisNpcEntity;
import com.annis.npc.compat.PayloadTypeRegistry;
import com.annis.npc.compat.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public final class NpcNetworking {
    private static final double MAX_DISTANCE_SQ = 128.0 * 128.0;

    private NpcNetworking() {
    }

    /** Se llama en cliente y servidor. */
    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(NpcActionPayload.ID, NpcActionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenEditorPayload.ID, OpenEditorPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenDialogPayload.ID, OpenDialogPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(com.annis.npc.link.EntityAnimPayload.ID, com.annis.npc.link.EntityAnimPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(com.annis.npc.link.EntityLookPayload.ID, com.annis.npc.link.EntityLookPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(EmoteSyncPayload.ID, EmoteSyncPayload.CODEC);
    }

    public static void registerServer() {
        com.annis.npc.link.EmoteSync.init();
        ServerPlayNetworking.registerGlobalReceiver(NpcActionPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            if (!player.hasPermissions(2)) {
                player.displayClientMessage(Component.literal("No tenes permiso para editar NPC"), true);
                return;
            }
            Entity entity = player.level().getEntity(payload.entityId());
            if (!(entity instanceof AnnisNpcEntity npc) || player.distanceToSqr(npc) > MAX_DISTANCE_SQ) {
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
                    npc.setCustomName(name.isEmpty() ? null : Component.literal(name));
                    npc.setCustomNameVisible(!name.isEmpty());
                }
                case NpcActionPayload.REMOVE -> npc.discard();
                case NpcActionPayload.SET_PROTECTED -> npc.setProtected(payload.flag());
                case NpcActionPayload.SET_MODE -> npc.setMode(payload.number(), player.getUUID());
                case NpcActionPayload.SET_DIALOG -> npc.setDialog(payload.text());
                case NpcActionPayload.SET_FIGURA -> npc.setFigura(payload.text());
                case NpcActionPayload.ROUTINE_ADD -> {
                    net.minecraft.world.phys.Vec3 at = payload.flag() ? player.position() : npc.position();
                    if (!npc.addStop(at.x, at.y, at.z, payload.text(), payload.number())) {
                        player.displayClientMessage(Component.literal("Maximo de paradas alcanzado"), true);
                    }
                }
                case NpcActionPayload.ROUTINE_CLEAR -> npc.clearStops();
                case NpcActionPayload.ROUTINE_REMOVE_LAST -> npc.removeLastStop();
                case NpcActionPayload.ROUTINE_ENABLE -> npc.setRoutineOn(payload.flag());
                case NpcActionPayload.CLEAR_EQUIP -> npc.clearEquipment(player);
                case NpcActionPayload.SET_SCENE -> npc.setScene(payload.text());
                case NpcActionPayload.SYNC_SCENE -> {
                    int n = syncScene(npc);
                    player.displayClientMessage(Component.literal(n + " NPC sincronizados"), true);
                }
                case NpcActionPayload.SET_ACCESSORY -> npc.setAccessory(payload.number(), payload.text());
                case NpcActionPayload.SET_ALL_ACCESSORIES -> npc.setAccessories(payload.text());
                case NpcActionPayload.SET_MUSIC -> npc.setMusic(payload.text());
                case NpcActionPayload.STOP_MUSIC -> stopMusic(npc);
                case NpcActionPayload.SET_JOINTED -> npc.setJointed(payload.flag());
                case NpcActionPayload.CONVERT_MCA -> {
                    if (com.annis.npc.compat.FabricLoader.getInstance().isModLoaded("mca")
                            && com.annis.npc.mca.McaCommands.convertNpc(npc)) {
                        player.displayClientMessage(Component.literal("Ahora es una persona de MCA (mismo aspecto). Puedes hablar, regalar y casarte."), false);
                    } else {
                        player.displayClientMessage(Component.literal("MCA no esta instalado en el servidor"), true);
                    }
                }
                case NpcActionPayload.COPY_FROM -> {
                    Entity source = player.level().getEntity(payload.number());
                    if (source instanceof AnnisNpcEntity other && other != npc) {
                        npc.copyFrom(other);
                        player.displayClientMessage(Component.literal("Configuracion pegada"), true);
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
        int tick = (int) npc.level().getGameTime();
        String group = npc.getScene();
        if (group.isEmpty()) {
            npc.restartEmote(tick);
            playMusic(npc, npc.getMusic());
            return 1;
        }
        java.util.List<AnnisNpcEntity> members = npc.level().getEntitiesOfClass(AnnisNpcEntity.class,
                npc.getBoundingBox().inflate(160.0), other -> group.equals(other.getScene()));
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
        if (id == null || id.isEmpty() || !(npc.level() instanceof net.minecraft.server.level.ServerLevel world)) {
            return;
        }
        net.minecraft.resources.ResourceLocation ident = net.minecraft.resources.ResourceLocation.tryParse(id);
        if (ident == null) {
            return;
        }
        stopMusicId(world, npc, ident);
        world.playSound(null, npc.getX(), npc.getY() + 1.0, npc.getZ(),
                net.minecraft.sounds.SoundEvent.createVariableRangeEvent(ident), net.minecraft.sounds.SoundSource.RECORDS, 4.0f, 1.0f);
    }

    public static void stopMusic(AnnisNpcEntity npc) {
        net.minecraft.resources.ResourceLocation ident = net.minecraft.resources.ResourceLocation.tryParse(npc.getMusic());
        if (ident != null && npc.level() instanceof net.minecraft.server.level.ServerLevel world) {
            stopMusicId(world, npc, ident);
        }
    }

    private static void stopMusicId(net.minecraft.server.level.ServerLevel world, AnnisNpcEntity npc, net.minecraft.resources.ResourceLocation ident) {
        for (ServerPlayer p : world.players()) {
            if (p.distanceToSqr(npc) < 160.0 * 160.0) {
                p.connection.send(new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(ident, net.minecraft.sounds.SoundSource.RECORDS));
            }
        }
    }

    public static void openDialog(ServerPlayer player, AnnisNpcEntity npc) {
        ServerPlayNetworking.send(player, new OpenDialogPayload(npc.getId()));
    }

    public static void openEditor(ServerPlayer player, AnnisNpcEntity npc) {
        ServerPlayNetworking.send(player, new OpenEditorPayload(npc.getId()));
    }
}
