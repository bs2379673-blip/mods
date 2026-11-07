package com.starlevi.starvillagers.family;

import com.starlevi.starvillagers.StarVillagers;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/** Mensajes del arbol familiar. */
public final class FamilyNet {
    private FamilyNet() {
    }

    /** Una persona del arbol. role: abuelo, padre, yo, pareja, esposo, hermano, hijo. */
    public record Node(String id, String name, boolean female, boolean player, boolean dead, String role) {
    }

    public record Tree(String center, List<Node> nodes) implements CustomPacketPayload {
        public static final Type<Tree> TYPE = new Type<>(StarVillagers.id("arbol"));
        public static final StreamCodec<FriendlyByteBuf, Tree> CODEC = CustomPacketPayload.codec(Tree::write, Tree::new);

        Tree(FriendlyByteBuf b) {
            this(b.readUtf(64), read(b));
        }

        private static List<Node> read(FriendlyByteBuf b) {
            int n = Math.min(200, b.readVarInt());
            List<Node> l = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                l.add(new Node(b.readUtf(64), b.readUtf(64), b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readUtf(16)));
            }
            return l;
        }

        void write(FriendlyByteBuf b) {
            b.writeUtf(this.center, 64);
            b.writeVarInt(this.nodes.size());
            for (Node n : this.nodes) {
                b.writeUtf(n.id(), 64);
                b.writeUtf(n.name(), 64);
                b.writeBoolean(n.female());
                b.writeBoolean(n.player());
                b.writeBoolean(n.dead());
                b.writeUtf(n.role(), 16);
            }
        }

        @Override
        public Type<Tree> type() {
            return TYPE;
        }
    }

    /** El cliente pide el arbol de otra persona (al tocarla en el arbol). */
    public record Ask(String id) implements CustomPacketPayload {
        public static final Type<Ask> TYPE = new Type<>(StarVillagers.id("pedir_arbol"));
        public static final StreamCodec<FriendlyByteBuf, Ask> CODEC = CustomPacketPayload.codec(Ask::write, Ask::new);

        Ask(FriendlyByteBuf b) {
            this(b.readUtf(64));
        }

        void write(FriendlyByteBuf b) {
            b.writeUtf(this.id, 64);
        }

        @Override
        public Type<Ask> type() {
            return TYPE;
        }
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(Tree.TYPE, Tree.CODEC);
        PayloadTypeRegistry.playC2S().register(Ask.TYPE, Ask.CODEC);
    }
}
