package com.annis.interact;

import com.annis.interact.net.AnchorPayload;
import com.annis.interact.net.InvitePayload;
import com.annis.interact.net.SessionPayload;
import com.annis.interact.net.TouchPayload;
import com.annis.interact.net.TouchSyncPayload;
import com.annis.npc.entity.AnnisNpcEntity;
import com.annis.npc.link.EntityLinks;
import com.annis.npc.compat.PlayerLookup;
import com.annis.npc.compat.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import com.annis.npc.compat.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
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
        Vec3 slotA;
        Vec3 slotB;
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

    record Invite(UUID from, UUID target, Interaction type, long expires, boolean follow) {
    }

    private static final List<Session> ACTIVE = new ArrayList<>();
    private static final Map<UUID, Session> BY_ENTITY = new HashMap<>();
    private static final Map<UUID, Invite> INVITES = new HashMap<>();
    private static final Map<UUID, Float> ANCHORS = new HashMap<>();

    private Sessions() {
    }

    // ------------------------------------------------------------------ consultas

    public static boolean busy(Entity e) {
        return BY_ENTITY.containsKey(e.getUUID());
    }

    public static boolean inRevive(Entity e) {
        Session s = BY_ENTITY.get(e.getUUID());
        return s != null && s.type.kind() == Interaction.Kind.REVIVE;
    }

    public static Float anchorOf(Entity e) {
        return ANCHORS.get(e.getUUID());
    }

    /** Entidades con las que se puede interactuar. */
    public static boolean canTarget(Entity e) {
        if (!(e instanceof LivingEntity l) || !l.isAlive()) {
            return false;
        }
        return e instanceof ServerPlayer || e instanceof AnnisNpcEntity
                || (AnnisInteract.MCA && com.annis.interact.mca.McaBridge.isVillager(e))
                || EntityLinks.hasLook(l);
    }

    // ------------------------------------------------------------------ pedir / responder

    public static void request(ServerPlayer player, String id, int targetId) {
        // en los bailes, "+sigo" = quien pide hace la parte que sigue (femenina)
        boolean follow = id.endsWith(Interaction.FOLLOW);
        if (follow) {
            id = id.substring(0, id.length() - Interaction.FOLLOW.length());
        }
        Interaction type = Interaction.get(id);
        follow &= type != null && type.dance();
        Entity target = player.serverLevel().getEntity(targetId);
        if (type == null || target == null || target == player || !canTarget(target)) {
            return;
        }
        LivingEntity t = (LivingEntity) target;
        if (player.distanceToSqr(t) > 6.0 * 6.0) {
            say(player, "Estas demasiado lejos de " + name(t));
            return;
        }
        String problem = check(type, player, t);
        if (problem != null) {
            say(player, problem);
            return;
        }
        if (t instanceof ServerPlayer other && type.kind() != Interaction.Kind.REVIVE) {
            invite(player, other, type, follow);
            return;
        }
        if (AnnisInteract.MCA && com.annis.interact.mca.McaBridge.isVillager(t)) {
            String no = mcaRefusal(type, player, t);
            if (no != null) {
                say(player, no);
                return;
            }
        }
        start(type, player, t, follow);
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
            if (a.isPassenger() || a.isVehicle() || b.isVehicle()) {
                return "No se puede cargar ahora";
            }
        }
        if (Math.abs(a.getY() - b.getY()) > 0.75 && type.kind() != Interaction.Kind.CARRY
                && type.place() != Interaction.Place.BED) {
            return "Tienen que estar a la misma altura";
        }
        return null;
    }

    private static String mcaRefusal(Interaction type, ServerPlayer player, LivingEntity v) {
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

    private static void invite(ServerPlayer from, ServerPlayer to, Interaction type, boolean follow) {
        long now = from.serverLevel().getGameTime();
        int secs = Math.max(5, InteractConfig.get().segundosInvitacion);
        INVITES.put(to.getUUID(), new Invite(from.getUUID(), to.getUUID(), type, now + secs * 20L, follow));
        ServerPlayNetworking.send(to, new InvitePayload(from.getGameProfile().getName(), type.id(), secs));
        String role = !type.dance() ? "" : follow ? " (tu guias)" : " (tu sigues)";
        MutableComponent msg = Component.literal(from.getGameProfile().getName() + ": " + type.name() + role + "  ").withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(button("[Aceptar]", "/interaccion aceptar", ChatFormatting.GREEN))
                .append(Component.literal(" "))
                .append(button("[Rechazar]", "/interaccion rechazar", ChatFormatting.RED));
        to.displayClientMessage(msg, false);
        say(from, "Esperando a " + to.getGameProfile().getName() + "...");
    }

    private static MutableComponent button(String label, String cmd, ChatFormatting color) {
        return Component.literal(label).setStyle(Style.EMPTY.withColor(color).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(cmd))));
    }

    public static void answer(ServerPlayer to, boolean accept) {
        Invite inv = INVITES.remove(to.getUUID());
        ServerPlayNetworking.send(to, new InvitePayload("", "", 0));
        if (inv == null) {
            say(to, "No hay ninguna invitacion");
            return;
        }
        ServerPlayer from = to.getServer().getPlayerList().getPlayer(inv.from());
        if (from == null || !from.isAlive()) {
            say(to, "Ya no esta");
            return;
        }
        if (!accept) {
            say(from, to.getGameProfile().getName() + " dijo que no");
            return;
        }
        if (from.level() != to.level() || from.distanceToSqr(to) > 8.0 * 8.0) {
            say(to, "Estan demasiado lejos");
            return;
        }
        String problem = check(inv.type(), from, to);
        if (problem != null) {
            say(to, problem);
            say(from, problem);
            return;
        }
        start(inv.type(), from, to, inv.follow());
    }

    // ------------------------------------------------------------------ empezar

    private static Vec3 fwd(float yaw) {
        float r = yaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(r), 0, Mth.cos(r));
    }

    private static Vec3 left(float yaw) {
        float r = yaw * Mth.DEG_TO_RAD;
        return new Vec3(Mth.cos(r), 0, Mth.sin(r));
    }

    private static float yawOf(Vec3 u) {
        return (float) (Mth.atan2(-u.x, u.z) * Mth.RAD_TO_DEG);
    }

    private static boolean fits(LivingEntity e, Vec3 pos) {
        return e.level().noCollision(e, e.getBoundingBox().move(pos.subtract(e.position())).deflate(0.02));
    }

    /** Coloca a los dos segun la interaccion. false si no hay sitio. */
    private static boolean place(Session s) {
        LivingEntity a = s.a, b = s.b;
        Interaction type = s.type;
        Vec3 pa = a.position(), pb = b.position();
        switch (type.place()) {
            case FACE, KISS -> {
                float d = type.dist();
                float extra = 0;
                if (type.place() == Interaction.Place.KISS) {
                    d = (float) Math.sqrt(Layout.KISS_FWD * Layout.KISS_FWD + Layout.KISS_LEFT * Layout.KISS_LEFT);
                    extra = (float) Math.toDegrees(Math.atan2(Layout.KISS_LEFT, Layout.KISS_FWD));
                }
                Vec3 u = new Vec3(pb.x - pa.x, 0, pb.z - pa.z);
                u = u.lengthSqr() < 0.01 ? fwd(a.getYRot()) : u.normalize();
                Vec3 mid = new Vec3((pa.x + pb.x) / 2, pa.y, (pa.z + pb.z) / 2);
                Vec3 sa = mid.subtract(u.scale(d / 2)), sb = mid.add(u.scale(d / 2));
                if (!fits(a, sa) || !fits(b, sb)) {
                    sa = new Vec3(pa.x, pa.y, pa.z);
                    sb = sa.add(u.scale(d));
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
                float yaw = a.getYRot();
                Vec3 right = left(yaw).scale(-type.dist());
                Vec3 sa = pa, sb = pa.add(right);
                if (!fits(b, sb)) {
                    sb = new Vec3(pb.x, pa.y, pb.z);
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
            case BED -> {
                Bed bed = findBed(a);
                if (bed == null) {
                    bed = findBed(b);
                }
                if (bed == null) {
                    return false;
                }
                // cadera a 1/4 de bloque del centro del pie hacia la cabecera; mirando hacia los pies
                // (acostado boca arriba, la cabeza queda hacia la cabecera)
                Vec3 hip = bed.foot().add(bed.dir().scale(0.25));
                float yaw = yawOf(bed.dir().reverse());
                Vec3 side = left(yaw).scale(Layout.BED_SIDE);
                // el que es abrazado va a la izquierda; el que abraza a la derecha (mira hacia el otro)
                boolean aHugged = "cama_abrazado".equals(type.animA());
                s.slotA = aHugged ? hip.add(side) : hip.subtract(side);
                s.slotB = aHugged ? hip.subtract(side) : hip.add(side);
                s.yawA = yaw;
                s.yawB = yaw;
            }
            case HELP -> {
                Float y = Downed.yawOf(b);
                float yaw = y == null ? b.yBodyRot : y;
                Vec3 sa = pb.add(fwd(yaw).scale(type.dist()));
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

    /** Cama: centro del bloque de los pies (a la altura del colchon) y direccion hacia la cabecera. */
    record Bed(Vec3 foot, Vec3 dir) {
    }

    /** La cama libre mas cercana a 4 bloques. */
    static Bed findBed(LivingEntity e) {
        net.minecraft.world.level.Level w = e.level();
        net.minecraft.core.BlockPos c = e.blockPosition();
        Bed best = null;
        double bestD = Double.MAX_VALUE;
        for (net.minecraft.core.BlockPos p : net.minecraft.core.BlockPos.betweenClosed(c.offset(-4, -2, -4), c.offset(4, 2, 4))) {
            net.minecraft.world.level.block.state.BlockState st = w.getBlockState(p);
            if (!(st.getBlock() instanceof net.minecraft.world.level.block.BedBlock)) {
                continue;
            }
            if (st.getValue(net.minecraft.world.level.block.BedBlock.OCCUPIED)) {
                continue;
            }
            net.minecraft.core.Direction f = st.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING);
            net.minecraft.core.BlockPos foot = st.getValue(net.minecraft.world.level.block.BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.HEAD
                    ? p.relative(f.getOpposite()) : p.immutable();
            double d = foot.distToCenterSqr(e.position());
            if (d < bestD) {
                bestD = d;
                best = new Bed(new Vec3(foot.getX() + 0.5, foot.getY() + 0.5625, foot.getZ() + 0.5),
                        new Vec3(f.getStepX(), 0, f.getStepZ()));
            }
        }
        return best;
    }

    private static void moveTo(LivingEntity e, Vec3 pos, float yaw) {
        if (e instanceof ServerPlayer p) {
            p.connection.teleport(pos.x, pos.y, pos.z, yaw, p.getXRot());
        } else {
            e.moveTo(pos.x, pos.y, pos.z, yaw, e.getXRot());
            e.setDeltaMovement(Vec3.ZERO);
            if (e instanceof Mob m) {
                m.getNavigation().stop();
            }
        }
        e.setYBodyRot(yaw);
        e.setYHeadRot(yaw);
    }

    public static boolean start(Interaction type, LivingEntity a, LivingEntity b) {
        return start(type, a, b, false);
    }

    /** follow: en los bailes, quien pide (a) hace la parte que sigue. */
    public static boolean start(Interaction type, LivingEntity a, LivingEntity b, boolean follow) {
        String animA = type.animA(), animB = type.animB();
        if (follow && type.dance()) {
            // bailes: quien pide hace la parte que sigue (mismo sitio, se cambian solo las animaciones)
            animA = type.animB();
            animB = type.animA();
        }
        if (type.swap()) {
            // "que me lleve": el que pide va encima; en la sesion "a" es siempre quien carga
            LivingEntity tmp = a;
            a = b;
            b = tmp;
            animA = type.animB();
            animB = type.animA();
        }
        ServerLevel world = (ServerLevel) a.level();
        Session s = new Session(type, a, b, world.getGameTime());
        if (!place(s)) {
            sayBoth(s, type.place() == Interaction.Place.BED ? "No hay una cama libre cerca (hasta 4 bloques)"
                    : "No hay espacio para " + type.name().toLowerCase());
            return false;
        }
        if (type.kind() == Interaction.Kind.CARRY) {
            if (b.isPassenger()) {
                b.stopRiding();
            }
            if (!b.startRiding(a, true)) {
                sayBoth(s, "No se pudo cargar a " + name(b));
                return false;
            }
            syncPassengers(a);
            if (b instanceof Mob m) {
                m.getNavigation().stop();
            }
        } else if (s.slotA != null) {
            moveTo(a, s.slotA, s.yawA);
            moveTo(b, s.slotB, s.yawB);
        }
        boolean anchored = type.kind() == Interaction.Kind.SCENE || type.kind() == Interaction.Kind.LOOP
                || type.kind() == Interaction.Kind.REVIVE || type.kind() == Interaction.Kind.TOUCH;
        if (anchored) {
            anchor(a, s.yawA);
            anchor(b, s.yawB);
        }
        int hold = type.timed() ? type.ticks() + 40 : 0;
        EntityLinks.play(a, animA, hold);
        EntityLinks.play(b, animB, hold);
        ACTIVE.add(s);
        BY_ENTITY.put(a.getUUID(), s);
        BY_ENTITY.put(b.getUUID(), s);
        if (!type.timed()) {
            for (LivingEntity e : new LivingEntity[]{a, b}) {
                if (e instanceof ServerPlayer p) {
                    ServerPlayNetworking.send(p, new SessionPayload(type.id()));
                }
            }
            String hint = type.kind() == Interaction.Kind.CARRY ? " (Shift para bajar)" : " (Shift para terminar)";
            sayBoth(s, type.name() + hint);
        }
        if (type.kind() == Interaction.Kind.TOUCH) {
            // avisa a todos quien toca a quien (el que toca abre la pantalla de mimos)
            touchBroadcast(s, new TouchSyncPayload(a.getId(), b.getId(), Touch.NONE, 0, 0, 0, 0, 0, 0, false));
        }
        return true;
    }

    // ------------------------------------------------------------------ cada tick

    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        Iterator<Map.Entry<UUID, Invite>> it = INVITES.entrySet().iterator();
        while (it.hasNext()) {
            Invite inv = it.next().getValue();
            if (now > inv.expires()) {
                it.remove();
                ServerPlayer to = server.getPlayerList().getPlayer(inv.target());
                ServerPlayer from = server.getPlayerList().getPlayer(inv.from());
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
            String why = tickSession(s, s.a.level().getGameTime());
            if (why != null) {
                end(s, why.isEmpty() ? null : why, why.isEmpty());
            }
        }
    }

    /** null = sigue; "" = termino bien; texto = se corto. */
    private static String tickSession(Session s, long now) {
        LivingEntity a = s.a, b = s.b;
        if (!a.isAlive() || !b.isAlive() || a.isRemoved() || b.isRemoved() || a.level() != b.level()) {
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
            case SCENE, LOOP, REVIVE, TOUCH -> {
                if (!hold(a, s.slotA, s.yawA) || !hold(b, s.slotB, s.yawB)) {
                    return type.timed() ? "Se movio" : "";
                }
            }
            case FOLLOW -> {
                if (a.distanceToSqr(b) > 6.0 * 6.0) {
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
        return e instanceof ServerPlayer p && p.isShiftKeyDown();
    }

    /** Mobs: los deja en su sitio. Jugadores: si se alejan, se termina. */
    private static boolean hold(LivingEntity e, Vec3 slot, float yaw) {
        if (slot == null) {
            return true;
        }
        if (e instanceof ServerPlayer) {
            return e.position().distanceToSqr(slot) < 1.0;
        }
        if (e.position().distanceToSqr(slot) > 0.0004) {
            e.moveTo(slot.x, slot.y, slot.z, yaw, e.getXRot());
        }
        e.setDeltaMovement(Vec3.ZERO);
        e.setYRot(yaw);
        e.setYBodyRot(yaw);
        if (e instanceof Mob m) {
            m.getNavigation().stop();
        }
        return true;
    }

    /** De la mano: el que no es jugador camina al lado del otro. */
    private static void follow(Session s, long now) {
        LivingEntity lead = s.a, mate = s.b;
        boolean mateRight = true;
        if (!(mate instanceof Mob) && lead instanceof Mob) {
            lead = s.b;
            mate = s.a;
            mateRight = false;
        }
        if (!(mate instanceof Mob m)) {
            return;
        }
        float yaw = lead.yBodyRot;
        Vec3 side = left(yaw).scale(mateRight ? -Layout.HAND_SIDE : Layout.HAND_SIDE);
        Vec3 want = lead.position().add(side);
        Vec3 gap = want.subtract(m.position());
        double d = gap.horizontalDistance();
        m.getNavigation().stop();
        if (d > 4.0 && fits(m, want)) {
            m.moveTo(want.x, want.y, want.z, yaw, m.getXRot());
            return;
        }
        // camina pegado al otro: cada tick recorre casi todo lo que falta (con choques normales)
        double step = Math.min(d, 0.12 + lead.getDeltaMovement().horizontalDistance() * 1.6 + d * 0.5);
        if (d > 0.01) {
            Vec3 move = new Vec3(gap.x, 0, gap.z).normalize().scale(step);
            m.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(move.x, m.getDeltaMovement().y, move.z));
            if (m.horizontalCollision && m.onGround()) {
                m.getJumpControl().jump();
            }
        }
        m.setYRot(yaw);
        m.setYBodyRot(yaw);
        m.setYHeadRot(lead.getYHeadRot());
    }

    // ------------------------------------------------------------------ terminar

    public static void endFor(Entity e, String why) {
        Session s = BY_ENTITY.get(e.getUUID());
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
        BY_ENTITY.remove(s.a.getUUID());
        BY_ENTITY.remove(s.b.getUUID());
        if (s.type.kind() == Interaction.Kind.CARRY) {
            if (s.b.getVehicle() == s.a) {
                s.b.stopRiding();
            }
            syncPassengers(s.a);
            if (s.b.isAlive()) {
                Vec3 front = s.a.position().add(fwd(s.a.getYRot()).scale(0.8));
                Vec3 to = fits(s.b, front) ? front : s.a.position();
                if (s.b instanceof ServerPlayer p) {
                    p.connection.teleport(to.x, to.y, to.z, p.getYRot(), p.getXRot());
                } else {
                    s.b.moveTo(to.x, to.y, to.z, s.b.getYRot(), s.b.getXRot());
                }
            }
        }
        if (s.type.kind() == Interaction.Kind.TOUCH) {
            TOUCH_RATE.remove(s.a.getUUID());
            touchBroadcast(s, new TouchSyncPayload(s.a.getId(), -1, Touch.NONE, 0, 0, 0, 0, 0, 0, false));
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
                    anchor(e, Downed.yawOf(e) == null ? e.yBodyRot : Downed.yawOf(e));
                } else {
                    EntityLinks.stop(e);
                }
            }
            if (e instanceof ServerPlayer p && !s.type.timed()) {
                ServerPlayNetworking.send(p, new SessionPayload(""));
            }
        }
        long age = s.a.level().getGameTime() - s.start;
        boolean counts = s.type.timed() ? completed : age > 200;
        if (counts && AnnisInteract.MCA) {
            if (s.a instanceof ServerPlayer p && com.annis.interact.mca.McaBridge.isVillager(s.b)) {
                com.annis.interact.mca.McaBridge.addHearts(s.b, p, s.type.heartsGain());
            } else if (s.b instanceof ServerPlayer p && com.annis.interact.mca.McaBridge.isVillager(s.a)) {
                com.annis.interact.mca.McaBridge.addHearts(s.a, p, s.type.heartsGain());
            }
        }
        if (why != null && !revived) {
            sayBoth(s, why);
        }
    }

    /** Si el que propone lleva un anillo de MCA, lo usa con el aldeano (MCA decide si acepta). */
    private static void proposalDone(Session s) {
        if (!(s.a instanceof ServerPlayer p) || !AnnisInteract.MCA || !com.annis.interact.mca.McaBridge.isVillager(s.b)) {
            return;
        }
        net.minecraft.world.item.ItemStack stack = p.getMainHandItem();
        net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
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

    // ------------------------------------------------------------------ mimos

    private static final Map<UUID, long[]> TOUCH_RATE = new HashMap<>();

    /** El que toca manda donde esta tocando; se reparte a todos los que ven a alguno de los dos. */
    public static void touch(ServerPlayer player, TouchPayload p) {
        Session s = BY_ENTITY.get(player.getUUID());
        if (s == null || s.done || s.type.kind() != Interaction.Kind.TOUCH || s.a != player) {
            return;
        }
        int zone = p.zone();
        if (zone < 0 || zone >= Touch.COUNT || !finite(p)) {
            return;
        }
        // como mucho unos 25 mensajes por segundo
        long now = player.serverLevel().getGameTime();
        long[] rate = TOUCH_RATE.computeIfAbsent(player.getUUID(), k -> new long[]{now, 0});
        if (rate[0] != now) {
            rate[0] = now;
            rate[1] = 0;
        }
        if (++rate[1] > 2 && p.active()) {
            return;
        }
        float[] pt = Touch.constrain(zone, p.x(), p.y(), p.z());
        float[] st = Touch.constrain(zone, p.sx(), p.sy(), p.sz());
        touchBroadcast(s, new TouchSyncPayload(s.a.getId(), s.b.getId(), zone, pt[0], pt[1], pt[2], st[0], st[1], st[2],
                p.active() && zone != Touch.NONE));
    }

    private static boolean finite(TouchPayload p) {
        float[] v = {p.x(), p.y(), p.z(), p.sx(), p.sy(), p.sz()};
        for (float f : v) {
            if (!Float.isFinite(f) || Math.abs(f) > 64) {
                return false;
            }
        }
        return true;
    }

    private static void touchBroadcast(Session s, CustomPacketPayload payload) {
        java.util.Set<ServerPlayer> to = new java.util.HashSet<>(PlayerLookup.tracking(s.a));
        to.addAll(PlayerLookup.tracking(s.b));
        for (LivingEntity e : new LivingEntity[]{s.a, s.b}) {
            if (e instanceof ServerPlayer p) {
                to.add(p);
            }
        }
        for (ServerPlayer p : to) {
            ServerPlayNetworking.send(p, payload);
        }
    }

    // ------------------------------------------------------------------ red

    static void syncPassengers(Entity vehicle) {
        ClientboundSetPassengersPacket pkt = new ClientboundSetPassengersPacket(vehicle);
        for (ServerPlayer p : PlayerLookup.tracking(vehicle)) {
            p.connection.send(pkt);
        }
        if (vehicle instanceof ServerPlayer self) {
            self.connection.send(pkt);
        }
    }

    public static void anchor(LivingEntity e, float yaw) {
        ANCHORS.put(e.getUUID(), yaw);
        broadcast(e, new AnchorPayload(e.getId(), yaw, true));
    }

    public static void unanchor(LivingEntity e) {
        if (ANCHORS.remove(e.getUUID()) != null) {
            broadcast(e, new AnchorPayload(e.getId(), 0, false));
        }
    }

    public static void onStartTracking(Entity e, ServerPlayer player) {
        Float yaw = ANCHORS.get(e.getUUID());
        if (yaw != null) {
            ServerPlayNetworking.send(player, new AnchorPayload(e.getId(), yaw, true));
        }
    }

    static void broadcast(Entity e, CustomPacketPayload payload) {
        for (ServerPlayer p : PlayerLookup.tracking(e)) {
            ServerPlayNetworking.send(p, payload);
        }
        if (e instanceof ServerPlayer self) {
            ServerPlayNetworking.send(self, payload);
        }
    }

    static String name(Entity e) {
        return e.getDisplayName() == null ? e.getName().getString() : e.getDisplayName().getString();
    }

    static void say(LivingEntity e, String msg) {
        if (e instanceof ServerPlayer p) {
            p.displayClientMessage(Component.literal(msg), true);
        }
    }

    private static void sayBoth(Session s, String msg) {
        say(s.a, msg);
        say(s.b, msg);
    }
}
