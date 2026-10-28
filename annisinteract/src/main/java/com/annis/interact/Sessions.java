package com.annis.interact;

import com.annis.interact.net.AnchorPayload;
import com.annis.interact.net.InvitePayload;
import com.annis.interact.net.SessionPayload;
import com.annis.npc.entity.AnnisNpcEntity;
import com.annis.npc.link.EntityLinks;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.s2c.play.EntityPassengersSetS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Servidor: invitaciones, colocacion y seguimiento de las interacciones en curso. */
public final class Sessions {
    static final class Session {
        final Interaction type;
        final LivingEntity a;
        final LivingEntity b;
        final long start;
        Vec3d slotA;
        Vec3d slotB;
        float yawA;
        float yawB;
        boolean done;

        Session(Interaction type, LivingEntity a, LivingEntity b, long start) {
            this.type = type;
            this.a = a;
            this.b = b;
            this.start = start;
        }
    }

    record Invite(UUID from, UUID target, Interaction type, long expires) {
    }

    private static final List<Session> ACTIVE = new ArrayList<>();
    private static final Map<UUID, Session> BY_ENTITY = new HashMap<>();
    private static final Map<UUID, Invite> INVITES = new HashMap<>();
    private static final Map<UUID, Float> ANCHORS = new HashMap<>();

    private Sessions() {
    }

    // ------------------------------------------------------------------ consultas

    public static boolean busy(Entity e) {
        return BY_ENTITY.containsKey(e.getUuid());
    }

    public static boolean inRevive(Entity e) {
        Session s = BY_ENTITY.get(e.getUuid());
        return s != null && s.type.kind() == Interaction.Kind.REVIVE;
    }

    public static Float anchorOf(Entity e) {
        return ANCHORS.get(e.getUuid());
    }

    /** Entidades con las que se puede interactuar. */
    public static boolean canTarget(Entity e) {
        if (!(e instanceof LivingEntity l) || !l.isAlive()) {
            return false;
        }
        return e instanceof ServerPlayerEntity || e instanceof AnnisNpcEntity
                || (AnnisInteract.MCA && com.annis.interact.mca.McaBridge.isVillager(e))
                || EntityLinks.hasLook(l);
    }

    // ------------------------------------------------------------------ pedir / responder

    public static void request(ServerPlayerEntity player, String id, int targetId) {
        Interaction type = Interaction.get(id);
        Entity target = player.getServerWorld().getEntityById(targetId);
        if (type == null || target == null || target == player || !canTarget(target)) {
            return;
        }
        LivingEntity t = (LivingEntity) target;
        if (player.squaredDistanceTo(t) > 6.0 * 6.0) {
            say(player, "Estas demasiado lejos de " + name(t));
            return;
        }
        String problem = check(type, player, t);
        if (problem != null) {
            say(player, problem);
            return;
        }
        if (t instanceof ServerPlayerEntity other && type.kind() != Interaction.Kind.REVIVE) {
            invite(player, other, type);
            return;
        }
        if (AnnisInteract.MCA && com.annis.interact.mca.McaBridge.isVillager(t)) {
            String no = mcaRefusal(type, player, t);
            if (no != null) {
                say(player, no);
                return;
            }
        }
        start(type, player, t);
    }

    /** null si se puede, o el motivo. */
    static String check(Interaction type, LivingEntity a, LivingEntity b) {
        if (busy(a)) {
            return "Ya estas en una interaccion (Shift para terminar)";
        }
        if (busy(b)) {
            return name(b) + " esta ocupado ahora";
        }
        boolean downedB = Downed.isDowned(b);
        if (type.kind() == Interaction.Kind.REVIVE) {
            return downedB ? null : name(b) + " no necesita ayuda";
        }
        if (Downed.isDowned(a)) {
            return "Estas caido";
        }
        if (downedB) {
            return name(b) + " esta caido: elige \"Ayudar a levantarse\"";
        }
        if (type.kind() == Interaction.Kind.CARRY) {
            if (a.hasVehicle() || a.hasPassengers() || b.hasPassengers()) {
                return "No se puede cargar ahora";
            }
        }
        if (Math.abs(a.getY() - b.getY()) > 0.75 && type.kind() != Interaction.Kind.CARRY) {
            return "Tienen que estar a la misma altura";
        }
        return null;
    }

    private static String mcaRefusal(Interaction type, ServerPlayerEntity player, LivingEntity v) {
        if (com.annis.interact.mca.McaBridge.isBusy(v)) {
            return name(v) + " no puede ahora";
        }
        if (type.romantic() && com.annis.interact.mca.McaBridge.isMinor(v)) {
            return name(v) + " es pequeno para eso";
        }
        InteractConfig cfg = InteractConfig.get();
        if (cfg.parejaAceptaTodo && com.annis.interact.mca.McaBridge.isPartner(v, player)) {
            return null;
        }
        int hearts = com.annis.interact.mca.McaBridge.hearts(v, player);
        int need = cfg.hearts(type);
        if (hearts < need) {
            return name(v) + " no quiere (" + type.name() + " necesita " + need + " corazones, tienes " + hearts + ")";
        }
        return null;
    }

    private static void invite(ServerPlayerEntity from, ServerPlayerEntity to, Interaction type) {
        long now = from.getServerWorld().getTime();
        int secs = Math.max(5, InteractConfig.get().segundosInvitacion);
        INVITES.put(to.getUuid(), new Invite(from.getUuid(), to.getUuid(), type, now + secs * 20L));
        ServerPlayNetworking.send(to, new InvitePayload(from.getGameProfile().getName(), type.id(), secs));
        MutableText msg = Text.literal(from.getGameProfile().getName() + ": " + type.name() + "  ").formatted(Formatting.LIGHT_PURPLE)
                .append(button("[Aceptar]", "/interaccion aceptar", Formatting.GREEN))
                .append(Text.literal(" "))
                .append(button("[Rechazar]", "/interaccion rechazar", Formatting.RED));
        to.sendMessage(msg, false);
        say(from, "Esperando a " + to.getGameProfile().getName() + "...");
    }

    private static MutableText button(String label, String cmd, Formatting color) {
        return Text.literal(label).setStyle(Style.EMPTY.withColor(color).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(cmd))));
    }

    public static void answer(ServerPlayerEntity to, boolean accept) {
        Invite inv = INVITES.remove(to.getUuid());
        ServerPlayNetworking.send(to, new InvitePayload("", "", 0));
        if (inv == null) {
            say(to, "No hay ninguna invitacion");
            return;
        }
        ServerPlayerEntity from = to.getServer().getPlayerManager().getPlayer(inv.from());
        if (from == null || !from.isAlive()) {
            say(to, "Ya no esta");
            return;
        }
        if (!accept) {
            say(from, to.getGameProfile().getName() + " dijo que no");
            return;
        }
        if (from.getWorld() != to.getWorld() || from.squaredDistanceTo(to) > 8.0 * 8.0) {
            say(to, "Estan demasiado lejos");
            return;
        }
        String problem = check(inv.type(), from, to);
        if (problem != null) {
            say(to, problem);
            say(from, problem);
            return;
        }
        start(inv.type(), from, to);
    }

    // ------------------------------------------------------------------ empezar

    private static Vec3d fwd(float yaw) {
        float r = yaw * MathHelper.RADIANS_PER_DEGREE;
        return new Vec3d(-MathHelper.sin(r), 0, MathHelper.cos(r));
    }

    private static Vec3d left(float yaw) {
        float r = yaw * MathHelper.RADIANS_PER_DEGREE;
        return new Vec3d(MathHelper.cos(r), 0, MathHelper.sin(r));
    }

    private static float yawOf(Vec3d u) {
        return (float) (MathHelper.atan2(-u.x, u.z) * MathHelper.DEGREES_PER_RADIAN);
    }

    private static boolean fits(LivingEntity e, Vec3d pos) {
        return e.getWorld().isSpaceEmpty(e, e.getBoundingBox().offset(pos.subtract(e.getPos())).contract(0.02));
    }

    /** Coloca a los dos segun la interaccion. false si no hay sitio. */
    private static boolean place(Session s) {
        LivingEntity a = s.a, b = s.b;
        Interaction type = s.type;
        Vec3d pa = a.getPos(), pb = b.getPos();
        switch (type.place()) {
            case FACE, KISS -> {
                float d = type.dist();
                float extra = 0;
                if (type.place() == Interaction.Place.KISS) {
                    d = (float) Math.sqrt(Layout.KISS_FWD * Layout.KISS_FWD + Layout.KISS_LEFT * Layout.KISS_LEFT);
                    extra = (float) Math.toDegrees(Math.atan2(Layout.KISS_LEFT, Layout.KISS_FWD));
                }
                Vec3d u = new Vec3d(pb.x - pa.x, 0, pb.z - pa.z);
                u = u.lengthSquared() < 0.01 ? fwd(a.getYaw()) : u.normalize();
                Vec3d mid = new Vec3d((pa.x + pb.x) / 2, pa.y, (pa.z + pb.z) / 2);
                Vec3d sa = mid.subtract(u.multiply(d / 2)), sb = mid.add(u.multiply(d / 2));
                if (!fits(a, sa) || !fits(b, sb)) {
                    sa = new Vec3d(pa.x, pa.y, pa.z);
                    sb = sa.add(u.multiply(d));
                    if (!fits(b, sb)) {
                        return false;
                    }
                }
                s.slotA = sa;
                s.slotB = sb;
                s.yawA = yawOf(u) + extra;
                s.yawB = s.yawA + 180.0f;
            }
            case RIGHT -> {
                float yaw = a.getYaw();
                Vec3d right = left(yaw).multiply(-type.dist());
                Vec3d sa = pa, sb = pa.add(right);
                if (!fits(b, sb)) {
                    sb = new Vec3d(pb.x, pa.y, pb.z);
                    sa = sb.subtract(right);
                    if (!fits(a, sa)) {
                        return false;
                    }
                }
                s.slotA = sa;
                s.slotB = sb;
                s.yawA = yaw;
                s.yawB = yaw;
            }
            case HELP -> {
                Float y = Downed.yawOf(b);
                float yaw = y == null ? b.bodyYaw : y;
                Vec3d sa = pb.add(fwd(yaw).multiply(type.dist()));
                if (!fits(a, sa)) {
                    return false;
                }
                s.slotA = sa;
                s.slotB = pb;
                s.yawA = yaw + 180.0f;
                s.yawB = yaw;
            }
            default -> {
                return true;
            }
        }
        return true;
    }

    private static void moveTo(LivingEntity e, Vec3d pos, float yaw) {
        if (e instanceof ServerPlayerEntity p) {
            p.networkHandler.requestTeleport(pos.x, pos.y, pos.z, yaw, p.getPitch());
        } else {
            e.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, e.getPitch());
            e.setVelocity(Vec3d.ZERO);
            if (e instanceof MobEntity m) {
                m.getNavigation().stop();
            }
        }
        e.setBodyYaw(yaw);
        e.setHeadYaw(yaw);
    }

    public static boolean start(Interaction type, LivingEntity a, LivingEntity b) {
        String animA = type.animA(), animB = type.animB();
        if (type.swap()) {
            // "que me lleve": el que pide va encima; en la sesion "a" es siempre quien carga
            LivingEntity tmp = a;
            a = b;
            b = tmp;
            animA = type.animB();
            animB = type.animA();
        }
        ServerWorld world = (ServerWorld) a.getWorld();
        Session s = new Session(type, a, b, world.getTime());
        if (!place(s)) {
            sayBoth(s, "No hay espacio para " + type.name().toLowerCase());
            return false;
        }
        if (type.kind() == Interaction.Kind.CARRY) {
            if (b.hasVehicle()) {
                b.stopRiding();
            }
            if (!b.startRiding(a, true)) {
                sayBoth(s, "No se pudo cargar a " + name(b));
                return false;
            }
            syncPassengers(a);
            if (b instanceof MobEntity m) {
                m.getNavigation().stop();
            }
        } else if (s.slotA != null) {
            moveTo(a, s.slotA, s.yawA);
            moveTo(b, s.slotB, s.yawB);
        }
        boolean anchored = type.kind() == Interaction.Kind.SCENE || type.kind() == Interaction.Kind.LOOP
                || type.kind() == Interaction.Kind.REVIVE;
        if (anchored) {
            anchor(a, s.yawA);
            anchor(b, s.yawB);
        }
        int hold = type.timed() ? type.ticks() + 40 : 0;
        EntityLinks.play(a, animA, hold);
        EntityLinks.play(b, animB, hold);
        ACTIVE.add(s);
        BY_ENTITY.put(a.getUuid(), s);
        BY_ENTITY.put(b.getUuid(), s);
        if (!type.timed()) {
            for (LivingEntity e : new LivingEntity[]{a, b}) {
                if (e instanceof ServerPlayerEntity p) {
                    ServerPlayNetworking.send(p, new SessionPayload(type.id()));
                }
            }
            String hint = type.kind() == Interaction.Kind.CARRY ? " (Shift para bajar)" : " (Shift para terminar)";
            sayBoth(s, type.name() + hint);
        }
        return true;
    }

    // ------------------------------------------------------------------ cada tick

    public static void tick(MinecraftServer server) {
        long now = server.getOverworld().getTime();
        Iterator<Map.Entry<UUID, Invite>> it = INVITES.entrySet().iterator();
        while (it.hasNext()) {
            Invite inv = it.next().getValue();
            if (now > inv.expires()) {
                it.remove();
                ServerPlayerEntity to = server.getPlayerManager().getPlayer(inv.target());
                ServerPlayerEntity from = server.getPlayerManager().getPlayer(inv.from());
                if (to != null) {
                    ServerPlayNetworking.send(to, new InvitePayload("", "", 0));
                }
                if (from != null && to != null) {
                    say(from, to.getGameProfile().getName() + " no respondio");
                }
            }
        }
        for (Session s : new ArrayList<>(ACTIVE)) {
            if (s.done) {
                continue;
            }
            String why = tickSession(s, s.a.getWorld().getTime());
            if (why != null) {
                end(s, why.isEmpty() ? null : why, why.isEmpty());
            }
        }
    }

    /** null = sigue; "" = termino bien; texto = se corto. */
    private static String tickSession(Session s, long now) {
        LivingEntity a = s.a, b = s.b;
        if (!a.isAlive() || !b.isAlive() || a.isRemoved() || b.isRemoved() || a.getWorld() != b.getWorld()) {
            return "La interaccion se corto";
        }
        long age = now - s.start;
        Interaction type = s.type;
        if (type.kind() != Interaction.Kind.REVIVE && (Downed.isDowned(a) || Downed.isDowned(b))) {
            return "La interaccion se corto";
        }
        if (type.timed() && age >= type.ticks()) {
            return "";
        }
        if (age > 5 && (sneaking(a) || (type.kind() != Interaction.Kind.REVIVE && sneaking(b)))) {
            return type.timed() ? "Cancelado" : "";
        }
        switch (type.kind()) {
            case SCENE, LOOP, REVIVE -> {
                if (!hold(a, s.slotA, s.yawA) || !hold(b, s.slotB, s.yawB)) {
                    return type.timed() ? "Se movio" : "";
                }
            }
            case FOLLOW -> {
                if (a.squaredDistanceTo(b) > 6.0 * 6.0) {
                    return "Se soltaron de la mano";
                }
                follow(s, now);
            }
            case CARRY -> {
                if (b.getVehicle() != a) {
                    return "";
                }
            }
        }
        return null;
    }

    private static boolean sneaking(LivingEntity e) {
        return e instanceof ServerPlayerEntity p && p.isSneaking();
    }

    /** Mobs: los deja en su sitio. Jugadores: si se alejan, se termina. */
    private static boolean hold(LivingEntity e, Vec3d slot, float yaw) {
        if (slot == null) {
            return true;
        }
        if (e instanceof ServerPlayerEntity) {
            return e.getPos().squaredDistanceTo(slot) < 1.0;
        }
        if (e.getPos().squaredDistanceTo(slot) > 0.0004) {
            e.refreshPositionAndAngles(slot.x, slot.y, slot.z, yaw, e.getPitch());
        }
        e.setVelocity(Vec3d.ZERO);
        e.setYaw(yaw);
        e.setBodyYaw(yaw);
        if (e instanceof MobEntity m) {
            m.getNavigation().stop();
        }
        return true;
    }

    /** De la mano: el que no es jugador camina al lado del otro. */
    private static void follow(Session s, long now) {
        LivingEntity lead = s.a, mate = s.b;
        boolean mateRight = true;
        if (!(mate instanceof MobEntity) && lead instanceof MobEntity) {
            lead = s.b;
            mate = s.a;
            mateRight = false;
        }
        if (!(mate instanceof MobEntity m)) {
            return;
        }
        float yaw = lead.bodyYaw;
        Vec3d side = left(yaw).multiply(mateRight ? -Layout.HAND_SIDE : Layout.HAND_SIDE);
        Vec3d want = lead.getPos().add(side);
        Vec3d gap = want.subtract(m.getPos());
        double d = gap.horizontalLength();
        m.getNavigation().stop();
        if (d > 4.0 && fits(m, want)) {
            m.refreshPositionAndAngles(want.x, want.y, want.z, yaw, m.getPitch());
            return;
        }
        // camina pegado al otro: cada tick recorre casi todo lo que falta (con choques normales)
        double step = Math.min(d, 0.12 + lead.getVelocity().horizontalLength() * 1.6 + d * 0.5);
        if (d > 0.01) {
            Vec3d move = new Vec3d(gap.x, 0, gap.z).normalize().multiply(step);
            m.move(net.minecraft.entity.MovementType.SELF, new Vec3d(move.x, m.getVelocity().y, move.z));
            if (m.horizontalCollision && m.isOnGround()) {
                m.getJumpControl().setActive();
            }
        }
        m.setYaw(yaw);
        m.setBodyYaw(yaw);
        m.setHeadYaw(lead.getHeadYaw());
    }

    // ------------------------------------------------------------------ terminar

    public static void endFor(Entity e, String why) {
        Session s = BY_ENTITY.get(e.getUuid());
        if (s != null) {
            end(s, why, false);
        }
    }

    private static void end(Session s, String why, boolean completed) {
        if (s.done) {
            return;
        }
        s.done = true;
        ACTIVE.remove(s);
        BY_ENTITY.remove(s.a.getUuid());
        BY_ENTITY.remove(s.b.getUuid());
        if (s.type.kind() == Interaction.Kind.CARRY) {
            if (s.b.getVehicle() == s.a) {
                s.b.stopRiding();
            }
            syncPassengers(s.a);
            if (s.b.isAlive()) {
                Vec3d front = s.a.getPos().add(fwd(s.a.getYaw()).multiply(0.8));
                Vec3d to = fits(s.b, front) ? front : s.a.getPos();
                if (s.b instanceof ServerPlayerEntity p) {
                    p.networkHandler.requestTeleport(to.x, to.y, to.z, p.getYaw(), p.getPitch());
                } else {
                    s.b.refreshPositionAndAngles(to.x, to.y, to.z, s.b.getYaw(), s.b.getPitch());
                }
            }
        }
        if (s.type == Interaction.PROPOSE && completed) {
            proposalDone(s);
        }
        boolean revived = false;
        if (s.type.kind() == Interaction.Kind.REVIVE && completed && Downed.isDowned(s.b)) {
            Downed.revive(s.b, s.a);
            revived = true;
        }
        for (LivingEntity e : new LivingEntity[]{s.a, s.b}) {
            unanchor(e);
            if (e.isAlive()) {
                if (Downed.isDowned(e)) {
                    EntityLinks.play(e, "caido_suelo", 0);
                    anchor(e, Downed.yawOf(e) == null ? e.bodyYaw : Downed.yawOf(e));
                } else {
                    EntityLinks.stop(e);
                }
            }
            if (e instanceof ServerPlayerEntity p && !s.type.timed()) {
                ServerPlayNetworking.send(p, new SessionPayload(""));
            }
        }
        long age = s.a.getWorld().getTime() - s.start;
        boolean counts = s.type.timed() ? completed : age > 200;
        if (counts && AnnisInteract.MCA) {
            if (s.a instanceof ServerPlayerEntity p && com.annis.interact.mca.McaBridge.isVillager(s.b)) {
                com.annis.interact.mca.McaBridge.addHearts(s.b, p, s.type.heartsGain());
            } else if (s.b instanceof ServerPlayerEntity p && com.annis.interact.mca.McaBridge.isVillager(s.a)) {
                com.annis.interact.mca.McaBridge.addHearts(s.a, p, s.type.heartsGain());
            }
        }
        if (why != null && !revived) {
            sayBoth(s, why);
        }
    }

    /** Si el que propone lleva un anillo de MCA, lo usa con el aldeano (MCA decide si acepta). */
    private static void proposalDone(Session s) {
        if (!(s.a instanceof ServerPlayerEntity p) || !AnnisInteract.MCA || !com.annis.interact.mca.McaBridge.isVillager(s.b)) {
            return;
        }
        net.minecraft.item.ItemStack stack = p.getMainHandStack();
        net.minecraft.util.Identifier id = net.minecraft.registry.Registries.ITEM.getId(stack.getItem());
        if ("mca".equals(id.getNamespace()) && id.getPath().contains("ring")) {
            com.annis.interact.mca.McaBridge.giveHeldGift(s.b, p);
            return;
        }
        say(p, "Para que cuente en MCA, propon con el anillo de compromiso en la mano");
    }

    public static void endAll() {
        for (Session s : new ArrayList<>(ACTIVE)) {
            end(s, null, false);
        }
        INVITES.clear();
        ANCHORS.clear();
    }

    // ------------------------------------------------------------------ red

    static void syncPassengers(Entity vehicle) {
        EntityPassengersSetS2CPacket pkt = new EntityPassengersSetS2CPacket(vehicle);
        for (ServerPlayerEntity p : PlayerLookup.tracking(vehicle)) {
            p.networkHandler.sendPacket(pkt);
        }
        if (vehicle instanceof ServerPlayerEntity self) {
            self.networkHandler.sendPacket(pkt);
        }
    }

    public static void anchor(LivingEntity e, float yaw) {
        ANCHORS.put(e.getUuid(), yaw);
        broadcast(e, new AnchorPayload(e.getId(), yaw, true));
    }

    public static void unanchor(LivingEntity e) {
        if (ANCHORS.remove(e.getUuid()) != null) {
            broadcast(e, new AnchorPayload(e.getId(), 0, false));
        }
    }

    public static void onStartTracking(Entity e, ServerPlayerEntity player) {
        Float yaw = ANCHORS.get(e.getUuid());
        if (yaw != null) {
            ServerPlayNetworking.send(player, new AnchorPayload(e.getId(), yaw, true));
        }
    }

    static void broadcast(Entity e, CustomPayload payload) {
        for (ServerPlayerEntity p : PlayerLookup.tracking(e)) {
            ServerPlayNetworking.send(p, payload);
        }
        if (e instanceof ServerPlayerEntity self) {
            ServerPlayNetworking.send(self, payload);
        }
    }

    static String name(Entity e) {
        return e.getDisplayName() == null ? e.getName().getString() : e.getDisplayName().getString();
    }

    static void say(LivingEntity e, String msg) {
        if (e instanceof ServerPlayerEntity p) {
            p.sendMessage(Text.literal(msg), true);
        }
    }

    private static void sayBoth(Session s, String msg) {
        say(s.a, msg);
        say(s.b, msg);
    }
}
