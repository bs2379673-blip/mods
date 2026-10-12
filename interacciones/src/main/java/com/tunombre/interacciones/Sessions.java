package com.tunombre.interacciones;

import io.netty.buffer.Unpooled;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;

import java.util.*;

/** Toda la logica de servidor de las interacciones. */
public final class Sessions {
    private Sessions() { }

    private record Req(UUID from, int action, long expire) { }

    private static final class Exec {
        final UUID attacker, victim;
        final Vec3 posA, posV;
        final float yaw;
        int tick;

        Exec(UUID a, UUID v, Vec3 pa, Vec3 pv, float yaw) { attacker = a; victim = v; posA = pa; posV = pv; this.yaw = yaw; }
    }

    private static final Map<UUID, Req> PENDING = new HashMap<>();
    private static final Map<UUID, UUID> CARRY = new HashMap<>();        // quien carga -> a quien
    private static final Map<UUID, Integer> CARRY_SLOT = new HashMap<>();
    private static final List<Exec> EXECS = new ArrayList<>();
    private static final List<Object[]> LATER = new ArrayList<>();         // {tick, Runnable}
    private static final UUID SLOW = UUID.fromString("6f1c3b52-6d0b-4a8e-9f0a-2a7b5c2d9e11");
    private static final int REQUEST_TICKS = 200;
    private static long now;

    // ------------------------------------------------------------------ util ----
    private static void msg(Player p, String s) { p.displayClientMessage(Component.literal(s), true); }

    private static void play(ServerPlayer p, AnimationManager.AnimationAccessor<StaticAnimation> a) {
        ServerPlayerPatch pp = EpicFightCapabilities.getEntityPatch(p, ServerPlayerPatch.class);
        if (pp != null && a != null) pp.playAnimationSynchronized(a, 0.1F);
    }

    private static void stop(ServerPlayer p, AnimationManager.AnimationAccessor<StaticAnimation> a) {
        ServerPlayerPatch pp = EpicFightCapabilities.getEntityPatch(p, ServerPlayerPatch.class);
        if (pp != null && a != null) pp.stopPlaying(a);
    }

    private static void later(int ticks, Runnable r) { LATER.add(new Object[]{now + ticks, r}); }

    private static boolean busy(ServerPlayer p) {
        if (CARRY.containsKey(p.getUUID()) || CARRY.containsValue(p.getUUID())) return true;
        for (Exec e : EXECS) if (e.attacker.equals(p.getUUID()) || e.victim.equals(p.getUUID())) return true;
        return p.containerMenu instanceof TradeMenu;
    }

    public static boolean isBlade(ItemStack s) {
        if (s.isEmpty()) return false;
        if (s.getItem() instanceof SwordItem || s.getItem() instanceof AxeItem) return true;
        var id = ForgeRegistries.ITEMS.getKey(s.getItem());
        String n = id == null ? "" : id.getPath();
        return n.contains("knife") || n.contains("cuchillo") || n.contains("dagger") || n.contains("machete") || n.contains("katana") || n.contains("blade");
    }

    /** el atacante esta detras de la victima (fuera de su campo de vision) */
    public static boolean behind(Player attacker, Player victim) {
        Vec3 look = Vec3.directionFromRotation(0, victim.getYHeadRot());
        Vec3 to = attacker.position().subtract(victim.position()).multiply(1, 0, 1);
        if (to.lengthSqr() < 1e-4) return false;
        return look.dot(to.normalize()) < -0.35;
    }

    private static float yawTo(Vec3 from, Vec3 to) {
        return (float) Math.toDegrees(Mth.atan2(to.z - from.z, to.x - from.x)) - 90F;
    }

    // ------------------------------------------------------------------ solicitudes ----
    static void request(ServerPlayer p, int action, int targetId) {
        if (action == Act.DROP) { stopCarry(p, true); return; }
        Entity e = p.level().getEntity(targetId);
        if (!(e instanceof ServerPlayer t) || t == p || !t.isAlive() || t.isSpectator() || !p.isAlive()) return;
        double d = p.distanceTo(t);
        if (d > 4.5) { msg(p, "Estas demasiado lejos"); return; }
        if (busy(p) || busy(t)) { msg(p, "Ahora no se puede"); return; }
        if (action == Act.EXECUTE) { execute(p, t); return; }
        if (action < 0 || action > Act.TRADE) return;
        PENDING.put(t.getUUID(), new Req(p.getUUID(), action, now + REQUEST_TICKS));
        Net.CH.send(PacketDistributor.PLAYER.with(() -> t), new Net.Prompt(p.getGameProfile().getName(), action, REQUEST_TICKS, true));
        msg(p, "Solicitud enviada a " + t.getGameProfile().getName());
    }

    static void answer(ServerPlayer t, boolean accept) {
        Req r = PENDING.remove(t.getUUID());
        Net.CH.send(PacketDistributor.PLAYER.with(() -> t), new Net.Prompt("", 0, 0, false));
        if (r == null || r.expire < now) return;
        ServerPlayer p = t.server.getPlayerList().getPlayer(r.from);
        if (p == null || !p.isAlive() || !t.isAlive() || p.level() != t.level() || p.distanceTo(t) > 5) { msg(t, "Ya no se puede"); return; }
        if (!accept) { msg(p, t.getGameProfile().getName() + " rechazo"); return; }
        if (busy(p) || busy(t)) { msg(t, "Ahora no se puede"); return; }
        switch (r.action) {
            case Act.HIGH_FIVE -> greet(p, t, Anims.HIGH_FIVE, 10);
            case Act.FIST_BUMP -> greet(p, t, Anims.FIST_BUMP, 9);
            case Act.CARRY -> startCarry(p, t);
            case Act.TRADE -> TradeMenu.Session.open(p, t);
            default -> { }
        }
    }

    // ------------------------------------------------------------------ saludos ----
    /** los pone de frente a 1 bloque y los dos hacen la animacion; en el contacto, sonido y chispas */
    private static void greet(ServerPlayer a, ServerPlayer b, AnimationManager.AnimationAccessor<StaticAnimation> anim, int contact) {
        Vec3 dir = b.position().subtract(a.position()).multiply(1, 0, 1);
        if (dir.lengthSqr() < 1e-3) dir = Vec3.directionFromRotation(0, a.getYRot());
        dir = dir.normalize();
        Vec3 nb = a.position().add(dir.scale(1.0));
        nb = new Vec3(nb.x, b.getY(), nb.z);
        if (Math.abs(a.getY() - b.getY()) > 0.8 || !b.level().noCollision(b, b.getBoundingBox().move(nb.subtract(b.position())))) nb = b.position();
        float ya = yawTo(a.position(), nb), yb = ya + 180F;
        a.connection.teleport(a.getX(), a.getY(), a.getZ(), ya, a.getXRot());
        b.connection.teleport(nb.x, nb.y, nb.z, yb, b.getXRot());
        play(a, anim);
        play(b, anim);
        final Vec3 mid = a.position().add(nb).scale(0.5).add(0, anim == Anims.HIGH_FIVE ? 1.85 : 1.25, 0);
        later(contact, () -> {
            if (!(a.level() instanceof ServerLevel lv)) return;
            lv.playSound(null, mid.x, mid.y, mid.z, anim == Anims.HIGH_FIVE ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 0.9F, anim == Anims.HIGH_FIVE ? 1.5F : 1.1F);
            lv.sendParticles(ParticleTypes.CRIT, mid.x, mid.y, mid.z, 10, 0.05, 0.05, 0.05, 0.25);
        });
    }

    // ------------------------------------------------------------------ cargar ----
    private static void startCarry(ServerPlayer a, ServerPlayer b) {
        if (a.isPassenger() || b.isPassenger() || a.isVehicle() || b.isVehicle()) { msg(a, "Ahora no se puede"); return; }
        if (!b.startRiding(a, true)) return;
        CARRY.put(a.getUUID(), b.getUUID());
        CARRY_SLOT.put(a.getUUID(), a.getInventory().selected);
        AttributeInstance sp = a.getAttribute(Attributes.MOVEMENT_SPEED);
        if (sp != null && sp.getModifier(SLOW) == null)
            sp.addTransientModifier(new AttributeModifier(SLOW, "cargando a alguien", -0.25, AttributeModifier.Operation.MULTIPLY_TOTAL));
        emptyHands(a);
        play(a, Anims.CARRY_HOLD);
        play(b, Anims.CARRIED);
        msg(a, "Cargando a " + b.getGameProfile().getName() + " (vuelve a pulsar la tecla de interaccion para soltarlo)");
        msg(b, "Te cargan al hombro (agachate para bajarte)");
    }

    /** mientras carga, las manos van ocupadas: se pasa a una casilla vacia de la barra (si hay) */
    private static void emptyHands(ServerPlayer a) {
        if (a.getMainHandItem().isEmpty()) return;
        for (int i = 0; i < 9; i++) {
            if (a.getInventory().getItem(i).isEmpty()) {
                a.getInventory().selected = i;
                a.connection.send(new ClientboundSetCarriedItemPacket(i));
                return;
            }
        }
    }

    static void stopCarry(ServerPlayer a, boolean drop) {
        UUID bid = CARRY.remove(a.getUUID());
        if (bid == null) return;
        AttributeInstance sp = a.getAttribute(Attributes.MOVEMENT_SPEED);
        if (sp != null) sp.removeModifier(SLOW);
        stop(a, Anims.CARRY_HOLD);
        ServerPlayer b = a.server.getPlayerList().getPlayer(bid);
        if (b != null) {
            stop(b, Anims.CARRIED);
            if (b.getVehicle() == a) b.stopRiding();
            if (drop) {
                Vec3 f = Vec3.directionFromRotation(0, a.getYRot());
                b.teleportTo(a.getX() + f.x * 0.8, a.getY(), a.getZ() + f.z * 0.8);
            }
        }
        Integer slot = CARRY_SLOT.remove(a.getUUID());
        if (slot != null && slot >= 0 && slot < 9) {
            a.getInventory().selected = slot;
            a.connection.send(new ClientboundSetCarriedItemPacket(slot));
        }
    }

    private static void carriedBy(ServerPlayer b) {
        for (Map.Entry<UUID, UUID> en : new ArrayList<>(CARRY.entrySet())) {
            if (en.getValue().equals(b.getUUID())) {
                ServerPlayer a = b.server.getPlayerList().getPlayer(en.getKey());
                if (a != null) stopCarry(a, false);
                else CARRY.remove(en.getKey());
            }
        }
    }

    // ------------------------------------------------------------------ ejecucion sigilosa ----
    private static void execute(ServerPlayer a, ServerPlayer v) {
        if (!isBlade(a.getMainHandItem())) { msg(a, "Necesitas un cuchillo, espada o hacha en la mano"); return; }
        if (!a.isShiftKeyDown()) { msg(a, "Tienes que ir agachado"); return; }
        if (!behind(a, v)) { msg(a, "Tienes que estar a su espalda"); return; }
        if (a.distanceTo(v) > 2.2) { msg(a, "Acercate mas"); return; }
        if (!a.server.isPvpAllowed() || !a.canHarmPlayer(v)) { msg(a, "El PvP esta desactivado"); return; }
        float yaw = v.getYRot();
        Vec3 f = Vec3.directionFromRotation(0, yaw);
        Vec3 pv = v.position();
        Vec3 pa = pv.subtract(f.scale(0.45));
        EXECS.add(new Exec(a.getUUID(), v.getUUID(), pa, pv, yaw));
        play(a, Anims.EXEC_ATTACKER);
        play(v, Anims.EXEC_VICTIM);
        a.level().playSound(null, a.getX(), a.getY(), a.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.7F, 0.7F);
    }

    private static void tickExecs(net.minecraft.server.MinecraftServer s) {
        for (Iterator<Exec> it = EXECS.iterator(); it.hasNext(); ) {
            Exec e = it.next();
            ServerPlayer a = s.getPlayerList().getPlayer(e.attacker), v = s.getPlayerList().getPlayer(e.victim);
            e.tick++;
            if (a == null || v == null || !a.isAlive() || e.tick > 36) { it.remove(); continue; }
            // los dos quietos en su sitio durante la animacion
            if (e.tick % 2 == 0 || e.tick < 3) {
                a.connection.teleport(e.posA.x, e.posA.y, e.posA.z, e.yaw, a.getXRot());
                if (v.isAlive()) v.connection.teleport(e.posV.x, e.posV.y, e.posV.z, e.yaw, v.getXRot());
            }
            if (e.tick == 18 && v.isAlive() && v.level() instanceof ServerLevel lv) {
                lv.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1F, 0.8F);
                Vec3 neck = v.position().add(0, 1.45, 0);
                lv.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()), neck.x, neck.y, neck.z, 40, 0.12, 0.08, 0.12, 0.3);
            }
            if (e.tick > 18 && e.tick < 30 && e.tick % 3 == 0 && v.level() instanceof ServerLevel lv) {
                Vec3 neck = v.position().add(0, 1.0, 0);
                lv.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()), neck.x, neck.y, neck.z, 8, 0.1, 0.2, 0.1, 0.1);
            }
            if (e.tick == 30 && v.isAlive()) v.hurt(a.damageSources().playerAttack(a), Float.MAX_VALUE);
        }
    }

    // ------------------------------------------------------------------ eventos ----
    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        now++;
        var s = e.getServer();
        for (Iterator<Object[]> it = LATER.iterator(); it.hasNext(); ) {
            Object[] o = it.next();
            if ((long) o[0] <= now) { it.remove(); ((Runnable) o[1]).run(); }
        }
        tickExecs(s);
        PENDING.entrySet().removeIf(en -> {
            if (en.getValue().expire >= now) return false;
            ServerPlayer t = s.getPlayerList().getPlayer(en.getKey());
            if (t != null) Net.CH.send(PacketDistributor.PLAYER.with(() -> t), new Net.Prompt("", 0, 0, false));
            return true;
        });
        for (Map.Entry<UUID, UUID> en : new ArrayList<>(CARRY.entrySet())) {
            ServerPlayer a = s.getPlayerList().getPlayer(en.getKey()), b = s.getPlayerList().getPlayer(en.getValue());
            if (a == null) { CARRY.remove(en.getKey()); continue; }
            if (b == null || !a.isAlive() || !b.isAlive() || b.getVehicle() != a) { stopCarry(a, false); continue; }
            if (now % 10 == 0) emptyHands(a);
        }
    }

    /** quien carga no puede usar objetos ni pegar (las manos estan ocupadas) */
    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickItem e) { if (CARRY.containsKey(e.getEntity().getUUID())) e.setCanceled(true); }

    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock e) { if (CARRY.containsKey(e.getEntity().getUUID())) e.setCanceled(true); }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent e) {
        if (CARRY.containsKey(e.getEntity().getUUID()) || CARRY.containsValue(e.getEntity().getUUID())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) { stopCarry(p, false); carriedBy(p); }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) { stopCarry(p, false); carriedBy(p); PENDING.remove(p.getUUID()); }
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) { stopCarry(p, false); carriedBy(p); }
    }

    static void openTrade(ServerPlayer p, TradeMenu.Session s, int side, String other) {
        NetworkHooks.openScreen(p, new SimpleMenuProvider((id, inv, pl) -> new TradeMenu(id, inv, s, side), Component.literal("Tradeo con " + other)),
                buf -> buf.writeUtf(other, 64));
        play(p, Anims.TRADE_TALK);
    }

    static void endTradeAnim(ServerPlayer p) { stop(p, Anims.TRADE_TALK); }

    @SuppressWarnings("unused")
    private static FriendlyByteBuf buf() { return new FriendlyByteBuf(Unpooled.buffer()); }
}
