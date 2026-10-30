package com.annis.npc.net;

import com.annis.npc.AnnisNpc;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Accion que el editor (cliente) le pide al servidor sobre un NPC. */
public record NpcActionPayload(int entityId, int action, String text, boolean flag, int number)
        implements CustomPacketPayload {

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
    public static final int CLEAR_EQUIP = 15;
    public static final int SET_SCENE = 16;       // text = grupo
    public static final int SYNC_SCENE = 17;      // reinicia a la vez todos los NPC del grupo
    public static final int SET_ACCESSORY = 18;   // number = indice (-1 = anadir), text = entrada (vacia = quitar)
    public static final int COPY_FROM = 19;       // number = id del NPC origen
    public static final int SET_ALL_ACCESSORIES = 20; // text = lista completa
    public static final int SET_MUSIC = 21;       // text = id de sonido
    public static final int STOP_MUSIC = 22;
    public static final int SET_JOINTED = 23;     // flag
    public static final int CONVERT_MCA = 24;   // flag (invencible)

    public static final CustomPacketPayload.Type<NpcActionPayload> ID =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AnnisNpc.MOD_ID, "npc_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NpcActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, NpcActionPayload::entityId,
            ByteBufCodecs.VAR_INT, NpcActionPayload::action,
            ByteBufCodecs.STRING_UTF8, NpcActionPayload::text,
            ByteBufCodecs.BOOL, NpcActionPayload::flag,
            ByteBufCodecs.VAR_INT, NpcActionPayload::number,
            NpcActionPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
