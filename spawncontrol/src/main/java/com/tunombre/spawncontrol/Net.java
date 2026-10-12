package com.tunombre.spawncontrol;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Supplier;

public final class Net {
    private static final String V = "1";
    public static final SimpleChannel CH = NetworkRegistry.newSimpleChannel(new ResourceLocation(SpawnControl.MODID, "main"), () -> V, V::equals, V::equals);

    private Net() { }

    static void init() {
        CH.registerMessage(0, Snapshot.class, Snapshot::write, Snapshot::read, Snapshot::handle, java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CH.registerMessage(1, Cmd.class, (m, b) -> b.writeUtf(m.line(), 400), b -> new Cmd(b.readUtf(400)), Cmd::handle, java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    /** una fila de la pantalla: un mob (o un comodin) con el resumen de su regla. state: 0 sin regla, 1 activo, 2 desactivado */
    public record Row(String id, String cat, int state, boolean only, int zones, int day, int max, int prob, boolean ann, boolean released) {
        void write(FriendlyByteBuf b) {
            b.writeUtf(id, 120);
            b.writeUtf(cat, 20);
            b.writeByte(state);
            b.writeBoolean(only);
            b.writeShort(zones);
            b.writeShort(day);
            b.writeShort(max);
            b.writeByte(prob);
            b.writeBoolean(ann);
            b.writeBoolean(released);
        }

        static Row read(FriendlyByteBuf b) {
            return new Row(b.readUtf(120), b.readUtf(20), b.readByte(), b.readBoolean(), b.readShort(), b.readShort(), b.readShort(), b.readByte(), b.readBoolean(), b.readBoolean());
        }
    }

    public record Snapshot(boolean open, List<Row> rows) {
        void write(FriendlyByteBuf b) {
            b.writeBoolean(open);
            b.writeVarInt(rows.size());
            for (Row r : rows) r.write(b);
        }

        static Snapshot read(FriendlyByteBuf b) {
            boolean open = b.readBoolean();
            int n = b.readVarInt();
            List<Row> l = new ArrayList<>(n);
            for (int i = 0; i < n; i++) l.add(Row.read(b));
            return new Snapshot(open, l);
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.tunombre.spawncontrol.client.SpawnScreen.update(this)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** el cliente pide ejecutar un comando /spawns (solo operadores) */
    public record Cmd(String line) {
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (p == null || !p.hasPermissions(2) || !line.startsWith("spawns ")) return;
                p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack(), line);
                send(p, false);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static void send(ServerPlayer p, boolean open) {
        TreeSet<String> ids = new TreeSet<>();
        for (var en : ForgeRegistries.ENTITY_TYPES.getEntries()) {
            if (en.getValue().getCategory() != MobCategory.MISC) ids.add(en.getKey().location().toString());
        }
        ids.addAll(Rules.all().keySet());
        List<Row> rows = new ArrayList<>();
        for (String id : ids) {
            String cat = "";
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl != null && ForgeRegistries.ENTITY_TYPES.containsKey(rl)) cat = ForgeRegistries.ENTITY_TYPES.getValue(rl).getCategory().getName();
            Rule r = Rules.all().get(id);
            if (r == null) rows.add(new Row(id, cat, 0, false, 0, -1, 0, 100, false, false));
            else rows.add(new Row(id, cat, r.enabled ? 1 : 2, r.onlyZones, r.zones.size(), r.releaseDay, r.max, r.prob, r.hasAnnouncement(), r.released));
        }
        // los comodines con regla van primero
        rows.sort((a, b) -> {
            boolean wa = a.id().contains("*"), wb = b.id().contains("*");
            if (wa != wb) return wa ? -1 : 1;
            return a.id().compareTo(b.id());
        });
        CH.send(PacketDistributor.PLAYER.with(() -> p), new Snapshot(open, rows));
    }
}
