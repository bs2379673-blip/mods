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
import java.util.HashMap;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Supplier;

public final class Net {
    private static final String V = "2";
    public static final SimpleChannel CH = NetworkRegistry.newSimpleChannel(new ResourceLocation(SpawnControl.MODID, "main"), () -> V, V::equals, V::equals);

    private Net() { }

    static void init() {
        CH.registerMessage(0, Snapshot.class, Snapshot::write, Snapshot::read, Snapshot::handle, java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CH.registerMessage(1, Cmd.class, (m, b) -> b.writeUtf(m.line(), 400), b -> new Cmd(b.readUtf(400)), Cmd::handle, java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    /**
     * una fila de la pantalla: un mob (o un comodin "mod:*") con el resumen de su regla.
     * state: 0 libre (sin regla, la politica por defecto lo deja pasar), 1 ON por regla, 2 OFF por regla, 3 bloqueado por la politica por defecto.
     * inh: la regla que lo gobierna es un comodin (mod:* o *), no una propia.
     */
    public record Row(String id, String cat, int state, boolean inh, boolean only, int zones, int day, int max, int prob, boolean ann, boolean released,
                      int alive, int ok, int blocked) {
        void write(FriendlyByteBuf b) {
            b.writeUtf(id, 120);
            b.writeUtf(cat, 48);
            b.writeByte(state);
            b.writeBoolean(inh);
            b.writeBoolean(only);
            b.writeShort(zones);
            b.writeShort(day);
            b.writeShort(max);
            b.writeByte(prob);
            b.writeBoolean(ann);
            b.writeBoolean(released);
            b.writeVarInt(alive);
            b.writeVarInt(ok);
            b.writeVarInt(blocked);
        }

        static Row read(FriendlyByteBuf b) {
            return new Row(b.readUtf(120), b.readUtf(48), b.readByte(), b.readBoolean(), b.readBoolean(), b.readShort(), b.readShort(), b.readShort(), b.readByte(),
                    b.readBoolean(), b.readBoolean(), b.readVarInt(), b.readVarInt(), b.readVarInt());
        }
    }

    public record Snapshot(boolean open, boolean defaultBlock, List<Row> rows) {
        void write(FriendlyByteBuf b) {
            b.writeBoolean(open);
            b.writeBoolean(defaultBlock);
            b.writeVarInt(rows.size());
            for (Row r : rows) r.write(b);
        }

        static Snapshot read(FriendlyByteBuf b) {
            boolean open = b.readBoolean();
            boolean def = b.readBoolean();
            int n = b.readVarInt();
            List<Row> l = new ArrayList<>(n);
            for (int i = 0; i < n; i++) l.add(Row.read(b));
            return new Snapshot(open, def, l);
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
        // mobs vivos ahora, por tipo
        Map<String, Integer> alive = new HashMap<>();
        for (ServerLevel lv : p.server.getAllLevels())
            for (Entity en : lv.getAllEntities()) {
                if (!(en instanceof Mob)) continue;
                ResourceLocation k = ForgeRegistries.ENTITY_TYPES.getKey(en.getType());
                if (k != null) alive.merge(k.toString(), 1, Integer::sum);
            }
        TreeSet<String> ids = new TreeSet<>();
        TreeSet<String> nss = new TreeSet<>();
        for (var en : ForgeRegistries.ENTITY_TYPES.getEntries()) {
            if (en.getValue().getCategory() != MobCategory.MISC) {
                ids.add(en.getKey().location().toString());
                nss.add(en.getKey().location().getNamespace());
            }
        }
        // un comodin por mod, para encender/apagar un mod entero de una
        for (String ns : nss) ids.add(ns + ":*");
        ids.addAll(Rules.all().keySet());
        List<Row> rows = new ArrayList<>();
        for (String id : ids) {
            boolean wild = id.equals("*") || id.endsWith(":*");
            String cat = wild ? "mod" : "";
            net.minecraft.world.entity.EntityType<?> type = null;
            ResourceLocation rl = wild ? null : ResourceLocation.tryParse(id);
            if (rl != null && ForgeRegistries.ENTITY_TYPES.containsKey(rl)) { type = ForgeRegistries.ENTITY_TYPES.getValue(rl); cat = type.getCategory().getName(); }
            Rule own = Rules.all().get(id);
            Rule eff = own != null ? own : (type != null ? Rules.find(type) : null);
            int state;
            if (eff != null) state = eff.enabled ? 1 : 2;
            else state = type != null && !Rules.defaultAllows(type) ? 3 : 0;
            int al = 0, ok = 0, bl = 0;
            if (wild) {
                String ns = id.equals("*") ? null : id.substring(0, id.length() - 1);
                for (var e : alive.entrySet()) if (ns == null || e.getKey().startsWith(ns)) al += e.getValue();
                for (var e : SpawnControl.STATS.entrySet()) if (ns == null || e.getKey().startsWith(ns)) { ok += e.getValue()[0]; bl += e.getValue()[1]; }
            } else {
                al = alive.getOrDefault(id, 0);
                int[] st = SpawnControl.STATS.get(id);
                if (st != null) { ok = st[0]; bl = st[1]; }
            }
            Rule v = eff;
            rows.add(new Row(id, cat, state, own == null && eff != null, v != null && v.onlyZones, v == null ? 0 : v.zones.size(), v == null ? -1 : v.releaseDay,
                    v == null ? 0 : v.max, v == null ? 100 : v.prob, v != null && v.hasAnnouncement(), v != null && v.released, al, ok, bl));
        }
        rows.sort((a, b) -> {
            boolean wa = a.id().contains("*"), wb = b.id().contains("*");
            if (wa != wb) return wa ? -1 : 1;
            return a.id().compareTo(b.id());
        });
        CH.send(PacketDistributor.PLAYER.with(() -> p), new Snapshot(open, Rules.defaultBlock, rows));
    }
}
