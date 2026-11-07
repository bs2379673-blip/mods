package com.starlevi.starvillagers.look;

import com.starlevi.starscenes.api.StarScenesAPI;
import com.starlevi.starvillagers.Identity;
import com.starlevi.starvillagers.Looks;
import com.starlevi.starvillagers.StarVillagers;
import com.starlevi.starvillagers.family.Family;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;

import java.util.List;

/**
 * Cambiarle la ropa o el pelo a un aldeano con la biblioteca de skins de MCA. Pueden hacerlo los
 * admins, en creativo, o su esposo/a. Accion "vestir" y condicion "puede_vestir".
 */
public final class Wardrobe {
    private Wardrobe() {
    }

    /** Abrir la biblioteca para este aldeano. */
    public record Open(int entity, String name, boolean female) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(StarVillagers.id("abrir_biblioteca"));
        public static final StreamCodec<FriendlyByteBuf, Open> CODEC = CustomPacketPayload.codec(Open::write, Open::new);

        Open(FriendlyByteBuf b) {
            this(b.readVarInt(), b.readUtf(64), b.readBoolean());
        }

        void write(FriendlyByteBuf b) {
            b.writeVarInt(this.entity);
            b.writeUtf(this.name, 64);
            b.writeBoolean(this.female);
        }

        @Override
        public Type<Open> type() {
            return TYPE;
        }
    }

    /** Ponerle algo: kind "ropa" o "pelo"; content -1 = volver a lo de siempre. */
    public record Set(int entity, String kind, int content, int version) implements CustomPacketPayload {
        public static final Type<Set> TYPE = new Type<>(StarVillagers.id("vestir"));
        public static final StreamCodec<FriendlyByteBuf, Set> CODEC = CustomPacketPayload.codec(Set::write, Set::new);

        Set(FriendlyByteBuf b) {
            this(b.readVarInt(), b.readUtf(8), b.readInt(), b.readInt());
        }

        void write(FriendlyByteBuf b) {
            b.writeVarInt(this.entity);
            b.writeUtf(this.kind, 8);
            b.writeInt(this.content);
            b.writeInt(this.version);
        }

        @Override
        public Type<Set> type() {
            return TYPE;
        }
    }

    public static boolean allowed(ServerPlayer p, Villager v) {
        return p.getAbilities().instabuild || p.level().getServer() != null && p.level().getServer().getPlayerList().isOp(p.nameAndId())
                || Family.married(v, p);
    }

    public static void init() {
        PayloadTypeRegistry.playS2C().register(Open.TYPE, Open.CODEC);
        PayloadTypeRegistry.playC2S().register(Set.TYPE, Set.CODEC);
        StarScenesAPI.registerCondition("puede_vestir", (p, s, v) -> s instanceof Villager vil && allowed(p, vil));
        StarScenesAPI.registerAction("vestir", (p, s, val) -> {
            if (s instanceof Villager v && allowed(p, v)) {
                StarScenesAPI.run(p, v, List.of("cerrar"));
                Identity id = StarVillagers.identity(v);
                ServerPlayNetworking.send(p, new Open(v.getId(), id != null ? id.name() : "", id != null && id.female()));
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(Set.TYPE, (msg, ctx) -> {
            ServerPlayer p = ctx.player();
            Entity e = p.level().getEntity(msg.entity());
            if (!(e instanceof Villager v) || p.distanceTo(v) > 16 || !allowed(p, v)) {
                return;
            }
            Identity id = StarVillagers.identity(v);
            if (id == null) {
                return;
            }
            String layer = msg.content() < 0 ? null : "biblioteca:" + msg.content() + ":" + msg.version();
            Identity n;
            if (msg.kind().equals("pelo")) {
                n = id.withHair(layer != null ? layer : Looks.hair(id.seed(), id.female()));
            } else {
                n = id.withClothes(layer != null ? layer : Looks.clothes(id, StarVillagers.profession(v)));
            }
            ((AttachmentTarget) (Object) v).setAttached(StarVillagers.IDENTITY, n);
            StarScenesAPI.play(v, "happy");
        });
    }
}
