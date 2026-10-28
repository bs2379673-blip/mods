package com.annis.npc.net;

import com.annis.npc.AnnisNpc;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Accion que el editor (cliente) le pide al servidor sobre un NPC. */
public record NpcActionPayload(int entityId, int action, String text, boolean flag, int number)
        implements CustomPayload {

    public static final int SET_SKIN = 0;        // text = skin, flag = slim
    public static final int PLAY_EMOTE = 1;      // text = emote, number = segundos quieto
    public static final int STOP_EMOTE = 2;
    public static final int SET_STATIONARY = 3;  // flag
    public static final int SET_NAME = 4;        // text (vacio = sin nombre)
    public static final int REMOVE = 5;
    public static final int SET_PROTECTED = 6;
    public static final int SET_MODE = 8;
    public static final int SET_DIALOG = 9;
    public static final int SET_FIGURA = 10;
    public static final int ROUTINE_ADD = 11;
    public static final int ROUTINE_CLEAR = 12;
    public static final int ROUTINE_REMOVE_LAST = 13;
    public static final int ROUTINE_ENABLE = 14;
    public static final int CLEAR_EQUIP = 15;   // flag (invencible)

    public static final CustomPayload.Id<NpcActionPayload> ID =
            new CustomPayload.Id<>(Identifier.of(AnnisNpc.MOD_ID, "npc_action"));

    public static final PacketCodec<RegistryByteBuf, NpcActionPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, NpcActionPayload::entityId,
            PacketCodecs.VAR_INT, NpcActionPayload::action,
            PacketCodecs.STRING, NpcActionPayload::text,
            PacketCodecs.BOOL, NpcActionPayload::flag,
            PacketCodecs.VAR_INT, NpcActionPayload::number,
            NpcActionPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
