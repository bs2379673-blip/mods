package com.starlevi.starvillagers.look;

import com.starlevi.starscenes.api.StarScenesAPI;
import com.starlevi.starvillagers.Identity;
import com.starlevi.starvillagers.Looks;
import com.starlevi.starvillagers.StarVillagers;
import com.starlevi.starvillagers.family.Family;
import com.starlevi.starvillagers.family.FamilyStore;
import com.starlevi.starvillagers.social.Mind;
import com.starlevi.starvillagers.social.Personality;
import com.starlevi.starvillagers.social.Social;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * El Libro de aldeanos (como el de MCA): tocando a un aldeano con el libro se abre su ficha, donde se le
 * cambia el nombre, el genero, la personalidad, el humor, la amistad con vos, su pareja (vos u otro
 * aldeano), se le puede hacer tener un bebe, ver su arbol y cambiarle la ropa.
 */
public final class Editor {
    private Editor() {
    }

    public record Other(int entity, String name, boolean female) {
    }

    public record Open(int entity, String name, boolean female, String personality, int mood, int hearts, String partner, String spouse,
                       List<String> personalities, List<Other> others) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(StarVillagers.id("ficha"));
        public static final StreamCodec<FriendlyByteBuf, Open> CODEC = CustomPacketPayload.codec(Open::write, Open::new);

        Open(FriendlyByteBuf b) {
            this(b.readVarInt(), b.readUtf(64), b.readBoolean(), b.readUtf(64), b.readInt(), b.readInt(), b.readUtf(64), b.readUtf(64), strings(b), others(b));
        }

        private static List<String> strings(FriendlyByteBuf b) {
            int n = Math.min(64, b.readVarInt());
            List<String> l = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                l.add(b.readUtf(64));
            }
            return l;
        }

        private static List<Other> others(FriendlyByteBuf b) {
            int n = Math.min(64, b.readVarInt());
            List<Other> l = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                l.add(new Other(b.readVarInt(), b.readUtf(64), b.readBoolean()));
            }
            return l;
        }

        void write(FriendlyByteBuf b) {
            b.writeVarInt(this.entity);
            b.writeUtf(this.name, 64);
            b.writeBoolean(this.female);
            b.writeUtf(this.personality, 64);
            b.writeInt(this.mood);
            b.writeInt(this.hearts);
            b.writeUtf(this.partner, 64);
            b.writeUtf(this.spouse, 64);
            b.writeVarInt(this.personalities.size());
            this.personalities.forEach(x -> b.writeUtf(x, 64));
            b.writeVarInt(this.others.size());
            for (Other o : this.others) {
                b.writeVarInt(o.entity());
                b.writeUtf(o.name(), 64);
                b.writeBoolean(o.female());
            }
        }

        @Override
        public Type<Open> type() {
            return TYPE;
        }
    }

    /** Cambiar algo de la ficha: que ("nombre", "genero", "personalidad", "humor", "amistad", "pareja", "esposo", "bebe", "arbol", "vestir") y valor. */
    public record Set(int entity, String what, String value) implements CustomPacketPayload {
        public static final Type<Set> TYPE = new Type<>(StarVillagers.id("ficha_cambio"));
        public static final StreamCodec<FriendlyByteBuf, Set> CODEC = CustomPacketPayload.codec(Set::write, Set::new);

        Set(FriendlyByteBuf b) {
            this(b.readVarInt(), b.readUtf(16), b.readUtf(64));
        }

        void write(FriendlyByteBuf b) {
            b.writeVarInt(this.entity);
            b.writeUtf(this.what, 16);
            b.writeUtf(this.value, 64);
        }

        @Override
        public Type<Set> type() {
            return TYPE;
        }
    }

    private static String name(UUID id) {
        if (id == null) {
            return "";
        }
        FamilyStore.Person p = FamilyStore.get(id);
        return p == null ? "?" : p.name;
    }

    public static void open(ServerPlayer p, Villager v) {
        StarVillagers.ensure(v);
        Identity id = StarVillagers.identity(v);
        if (id == null) {
            return;
        }
        Mind m = Social.mind(v);
        FamilyStore.Person pv = Family.person(v);
        Family.person(p);
        List<Other> others = new ArrayList<>();
        for (Villager o : v.level().getEntitiesOfClass(Villager.class, v.getBoundingBox().inflate(32), x -> x != v && !x.isBaby())) {
            Identity oi = StarVillagers.identity(o);
            if (oi != null && others.size() < 40) {
                Family.person(o);
                others.add(new Other(o.getId(), oi.name(), oi.female()));
            }
        }
        String partner = pv.partner == null ? "" : pv.partner.equals(p.getUUID()) ? "vos" : name(pv.partner);
        String spouse = pv.spouse == null ? "" : pv.spouse.equals(p.getUUID()) ? "vos" : name(pv.spouse);
        ServerPlayNetworking.send(p, new Open(v.getId(), id.name(), id.female(), m.personality, m.mood, m.hearts(p.getUUID()), partner, spouse,
                new ArrayList<>(Personality.ALL.keySet()), others));
    }

    /** Desarma la pareja de alguien (de los dos lados). */
    private static void unlink(FamilyStore.Person a) {
        for (UUID o : new UUID[]{a.partner, a.spouse}) {
            FamilyStore.Person b = o == null ? null : FamilyStore.get(o);
            if (b != null) {
                b.partner = null;
                b.spouse = null;
            }
        }
        a.partner = null;
        a.spouse = null;
    }

    private static void apply(ServerPlayer p, Villager v, String what, String value) {
        Identity id = StarVillagers.identity(v);
        Mind m = Social.mind(v);
        FamilyStore.Person pv = Family.person(v);
        AttachmentTarget t = (AttachmentTarget) (Object) v;
        switch (what) {
            case "nombre" -> {
                String n = value.trim();
                if (!n.isEmpty() && n.length() <= 32) {
                    t.setAttached(StarVillagers.IDENTITY, Looks.rename(id, n));
                    v.setCustomName(Component.literal(n));
                    pv.name = n;
                    FamilyStore.changed();
                }
            }
            case "genero" -> {
                boolean f = value.equals("mujer");
                t.setAttached(StarVillagers.IDENTITY, Looks.regender(id, f, StarVillagers.profession(v)));
                pv.female = f;
                FamilyStore.changed();
            }
            case "personalidad" -> {
                if (Personality.ALL.containsKey(value)) {
                    m.personality = value;
                    t.setAttached(Social.MIND, m);
                }
            }
            case "humor" -> {
                m.mood = Math.max(-100, Math.min(100, Integer.parseInt(value)));
                t.setAttached(Social.MIND, m);
            }
            case "amistad" -> {
                m.bond(p.getUUID()).hearts = Math.max(-100, Math.min(100, Integer.parseInt(value)));
                t.setAttached(Social.MIND, m);
            }
            case "pareja", "esposo" -> {
                boolean married = what.equals("esposo");
                unlink(pv);
                UUID other = null;
                if (value.equals("vos")) {
                    other = p.getUUID();
                    unlink(Family.person(p));
                } else if (!value.equals("nadie")) {
                    Entity e = v.level().getEntity(Integer.parseInt(value));
                    if (e instanceof Villager o) {
                        FamilyStore.Person po = Family.person(o);
                        unlink(po);
                        other = o.getUUID();
                    }
                }
                if (other != null) {
                    FamilyStore.Person po = FamilyStore.get(other);
                    if (married) {
                        pv.spouse = other;
                        po.spouse = v.getUUID();
                    } else {
                        pv.partner = other;
                        po.partner = v.getUUID();
                    }
                    StarScenesAPI.play(v, "happy");
                }
                FamilyStore.changed();
            }
            case "bebe" -> {
                if (pv.spouse == null) {
                    p.sendSystemMessage(Component.literal("§cPrimero tiene que estar casado/a."));
                    return;
                }
                Entity e = ((net.minecraft.server.level.ServerLevel) v.level()).getEntity(pv.spouse);
                Villager other = e instanceof Villager o ? o : null;
                FamilyStore.Person ps = FamilyStore.get(pv.spouse);
                Family.baby(v, other, other == null && ps != null && ps.player ? pv.spouse : null);
                StarScenesAPI.play(v, "happy");
            }
            case "arbol" -> {
                Family.send(p, v.getUUID());
                return;
            }
            case "vestir" -> {
                ServerPlayNetworking.send(p, new Wardrobe.Open(v.getId(), id.name(), id.female()));
                return;
            }
            default -> {
                return;
            }
        }
        open(p, v);
    }

    public static void init() {
        PayloadTypeRegistry.playS2C().register(Open.TYPE, Open.CODEC);
        PayloadTypeRegistry.playC2S().register(Set.TYPE, Set.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Set.TYPE, (msg, ctx) -> {
            ServerPlayer p = ctx.player();
            Entity e = p.level().getEntity(msg.entity());
            if (e instanceof Villager v && p.distanceTo(v) < 24 && p.getMainHandItem().is(StarVillagers.BOOK)) {
                try {
                    apply(p, v, msg.what(), msg.value());
                } catch (Exception ex) {
                    StarVillagers.LOGGER.warn("StarVillagers: no pude cambiar la ficha: {}", ex.toString());
                }
            }
        });
    }
}
