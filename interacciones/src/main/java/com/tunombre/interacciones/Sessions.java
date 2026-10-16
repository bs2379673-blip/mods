package com.tunombre.interacciones;

import io.netty.buffer.Unpooled;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
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
import net.minecraft.world.entity.LivingEntity;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

import java.util.*;

/** Toda la logica de servidor de las interacciones. */
public final class Sessions {
    private Sessions() { }

    private record Req(UUID from, int action, long expire) { }

    private static final class Exec {
        final UUID attacker, victim;
        final Vec3 posA, posV;
        final float yaw;
        final boolean npcVictim;
        final int kind;          // 0 ejecucion, 1 llave de sueno, 2 arresto
        int tick;

        Exec(UUID a, UUID v, Vec3 pa, Vec3 pv, float yaw, boolean npcVictim, int kind) {
            attacker = a; victim = v; posA = pa; posV = pv; this.yaw = yaw; this.npcVictim = npcVictim; this.kind = kind;
        }

        int length() { return kind == 1 ? 60 : kind == 2 ? 44 : 36; }
    }

    private static final Map<UUID, Req> PENDING = new HashMap<>();
    private static final Map<UUID, UUID> CARRY = new HashMap<>();        // quien carga -> a quien
    private static final Map<UUID, Integer> CARRY_SLOT = new HashMap<>();
    private static final Map<UUID, Integer> CARRY_MODE = new HashMap<>();  // 0 al hombro, 1 a caballito, 2 en brazos
    private static final Map<UUID, Long> LAST_SNEAK = new HashMap<>();      // ultimo tick en que cada jugador iba agachado
    private static final List<Exec> EXECS = new ArrayList<>();
    private static final List<Object[]> LATER = new ArrayList<>();         // {tick, Runnable}
    private static final UUID SLOW = UUID.fromString("6f1c3b52-6d0b-4a8e-9f0a-2a7b5c2d9e11");
    private static final int REQUEST_TICKS = 200;
    private static long now;

    // ------------------------------------------------------------------ util ----
    private static void msg(Player p, String s) { p.displayClientMessage(Component.literal(s), true); }

    /** funciona con cualquier entidad viva con parche de Epic Fight (jugador o un mob/NPC ya compatible) */
    private static void play(LivingEntity p, AnimationManager.AnimationAccessor<StaticAnimation> a) {
        LivingEntityPatch<?> pp = EpicFightCapabilities.getEntityPatch(p, LivingEntityPatch.class);
        if (pp != null && a != null) pp.playAnimationSynchronized(a, 0.1F);
    }

    private static void stop(LivingEntity p, AnimationManager.AnimationAccessor<StaticAnimation> a) {
        LivingEntityPatch<?> pp = EpicFightCapabilities.getEntityPatch(p, LivingEntityPatch.class);
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

    /** el atacante esta detras de la victima (fuera de su campo de vision); la victima puede ser otro jugador o un NPC */
    public static boolean behind(Player attacker, LivingEntity victim) {
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
        if (!p.isAlive()) return;
        Entity e = p.level().getEntity(targetId);
        if (e == null) return;
        if (e instanceof ServerPlayer t) {
            if (t == p || !t.isAlive() || t.isSpectator()) return;
            double d = p.distanceTo(t);
            if (d > 4.5) { msg(p, "Estas demasiado lejos"); return; }
            if (busy(p) || busy(t)) { msg(p, "Ahora no se puede"); return; }
            if (Act.instant(action)) { instant(p, t, action, false); return; }
            if (action < 0 || action >= Act.COUNT || action == Act.DROP) return;
            if (!precheck(p, action)) return;
            if (Act.direct(action)) { pairAct(p, t, action); return; }
            PENDING.put(t.getUUID(), new Req(p.getUUID(), action, now + REQUEST_TICKS));
            Net.CH.send(PacketDistributor.PLAYER.with(() -> t), new Net.Prompt(p.getGameProfile().getName(), action, REQUEST_TICKS, true));
            msg(p, "Solicitud enviada a " + t.getGameProfile().getName());
            return;
        }
        requestNpc(p, action, e);
    }

    /** interaccion directa contra un NPC de Easy NPC: solo para probar en solitario, sin otro jugador. No hay pedido
     * ni aceptar/rechazar (el NPC no puede responder), se ejecuta al toque. Solo creativo o admin, para que un
     * jugador comun no pueda usarlo para saltarse el PvP ni romper los NPCs del mundo. */
    private static void requestNpc(ServerPlayer p, int action, Entity e) {
        if (!(e instanceof LivingEntity npc) || npc == p || !npc.isAlive()) return;
        boolean admin = p.isCreative() || p.hasPermissions(2);
        boolean isNpc = Interacciones.EASY_NPC_LOADED && EasyNpcSupport.isEasyNpc(npc);
        if (!isNpc) { if (admin) msg(p, "Eso no es un NPC de Easy NPC"); return; }
        if (!admin) { msg(p, "Necesitas estar en creativo o ser admin para probar esto con NPCs"); return; }
        if (p.distanceTo(npc) > 4.5) { msg(p, "Estas demasiado lejos"); return; }
        if (busy(p)) { msg(p, "Ahora no se puede"); return; }
        if (action < 0 || action >= Act.COUNT || action == Act.DROP || Act.playerOnly(action)) { msg(p, "Eso no se puede probar con un NPC"); return; }
        if (Act.instant(action)) { instant(p, npc, action, true); return; }
        if (!precheck(p, action)) return;
        pairAct(p, npc, action);
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
            case Act.CARRY -> startCarry(p, t, 0);
            case Act.PIGGYBACK -> startCarry(p, t, 1);
            case Act.BRIDAL -> startCarry(p, t, 2);
            case Act.TRADE -> TradeMenu.Session.open(p, t);
            default -> pairAct(p, t, r.action);
        }
    }

    // ------------------------------------------------------------------ saludos ----
    /** reposiciona una entidad: a un jugador real hay que movelo via su conexion (si no, el cliente lo corrige de
     * vuelta); a un NPC (sin conexion) se lo mueve directo. */
    private static void face(LivingEntity e, double x, double y, double z, float yaw, float pitch) {
        if (e instanceof ServerPlayer sp) sp.connection.teleport(x, y, z, yaw, pitch);
        else { e.moveTo(x, y, z, yaw, pitch); e.setYHeadRot(yaw); }
    }

    /**
     * Interaccion de dos: los pone de frente a 'dist' bloques, cada uno con su animacion, y en el tick 'contact' lanza
     * el efecto (sonido, particulas, empujon...). 'b' puede ser otro jugador o, para probar en solitario, un NPC.
     */
    private static void pair(ServerPlayer a, LivingEntity b, AnimationManager.AnimationAccessor<StaticAnimation> animA,
                             AnimationManager.AnimationAccessor<StaticAnimation> animB, double dist, int contact, Runnable fx) {
        Vec3 dir = b.position().subtract(a.position()).multiply(1, 0, 1);
        if (dir.lengthSqr() < 1e-3) dir = Vec3.directionFromRotation(0, a.getYRot());
        dir = dir.normalize();
        Vec3 nb = a.position().add(dir.scale(dist));
        nb = new Vec3(nb.x, b.getY(), nb.z);
        if (Math.abs(a.getY() - b.getY()) > 0.8 || !b.level().noCollision(b, b.getBoundingBox().move(nb.subtract(b.position())))) nb = b.position();
        float ya = yawTo(a.position(), nb), yb = ya + 180F;
        a.connection.teleport(a.getX(), a.getY(), a.getZ(), ya, a.getXRot());
        face(b, nb.x, nb.y, nb.z, yb, b.getXRot());
        play(a, animA);
        play(b, animB);
        if (fx != null) later(contact, () -> { if (a.isAlive() && b.isAlive() && a.level() == b.level()) fx.run(); });
    }

    private static Vec3 between(LivingEntity a, LivingEntity b, double h) { return a.position().add(b.position()).scale(0.5).add(0, h, 0); }

    private static void sfx(LivingEntity at, Vec3 pos, net.minecraft.sounds.SoundEvent ev, float vol, float pitch) {
        at.level().playSound(null, pos.x, pos.y, pos.z, ev, SoundSource.PLAYERS, vol, pitch);
    }

    private static void particles(LivingEntity at, net.minecraft.core.particles.ParticleOptions o, Vec3 pos, int n, double spread, double speed) {
        if (at.level() instanceof ServerLevel lv) lv.sendParticles(o, pos.x, pos.y, pos.z, n, spread, spread, spread, speed);
    }

    /** chocar los cinco / choque de puno: misma animacion los dos, chispas y golpe en el contacto */
    private static void greet(ServerPlayer a, LivingEntity b, AnimationManager.AnimationAccessor<StaticAnimation> anim, int contact) {
        boolean five = anim == Anims.HIGH_FIVE;
        pair(a, b, anim, anim, 1.0, contact, () -> {
            Vec3 mid = between(a, b, five ? 1.85 : 1.25);
            sfx(a, mid, five ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_WEAK, 0.9F, five ? 1.5F : 1.1F);
            particles(a, ParticleTypes.CRIT, mid, 10, 0.05, 0.25);
        });
    }

    /** todas las interacciones "de dos" (las que no son cargar, tradear ni la ejecucion) */
    private static void pairAct(ServerPlayer a, LivingEntity b, int action) {
        switch (action) {
            case Act.HIGH_FIVE -> greet(a, b, Anims.HIGH_FIVE, 10);
            case Act.FIST_BUMP -> greet(a, b, Anims.FIST_BUMP, 9);
            case Act.HUG -> pair(a, b, Anims.HUG, Anims.HUG, 0.5, 18, () -> particles(a, ParticleTypes.HEART, between(a, b, 2.1), 3, 0.25, 0.02));
            case Act.HANDSHAKE -> pair(a, b, Anims.HANDSHAKE, Anims.HANDSHAKE, 0.85, 10,
                    () -> sfx(a, between(a, b, 1.1), SoundEvents.ARMOR_EQUIP_LEATHER, 0.6F, 1.4F));
            case Act.BOW -> pair(a, b, Anims.BOW, Anims.BOW, 1.4, 0, null);
            case Act.SALUTE -> pair(a, b, Anims.SALUTE, Anims.SALUTE, 1.6, 6, () -> sfx(a, a.position(), SoundEvents.ARMOR_EQUIP_IRON, 0.5F, 1.3F));
            case Act.CHEST_BUMP -> pair(a, b, Anims.CHEST_BUMP, Anims.CHEST_BUMP, 0.9, 11, () -> {
                Vec3 m = between(a, b, 1.3);
                sfx(a, m, SoundEvents.PLAYER_ATTACK_STRONG, 1F, 0.8F);
                particles(a, ParticleTypes.CRIT, m, 14, 0.1, 0.3);
            });
            case Act.PAT -> pair(a, b, Anims.PAT_GIVE, Anims.PAT_TAKE, 0.75, 9, () -> {
                for (int i = 0; i < 3; i++) later(i * 4, () -> sfx(a, b.position().add(0, 1.4, 0), SoundEvents.PLAYER_ATTACK_WEAK, 0.4F, 1.6F));
            });
            case Act.DANCE -> pair(a, b, Anims.DANCE, Anims.DANCE, 1.6, 2, () -> {
                for (int i = 0; i < 10; i++) later(i * 9, () -> {
                    particles(a, ParticleTypes.NOTE, a.position().add(0, 2.2, 0), 1, 0.3, 1);
                    particles(b, ParticleTypes.NOTE, b.position().add(0, 2.2, 0), 1, 0.3, 1);
                });
            });
            case Act.SHOVE -> pair(a, b, Anims.SHOVE, Anims.SHOVED, 0.8, 7, () -> {
                Vec3 d = b.position().subtract(a.position()).multiply(1, 0, 1);
                d = d.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, a.getYRot()) : d.normalize();
                b.setDeltaMovement(d.x * 0.7, 0.22, d.z * 0.7);
                b.hasImpulse = true;
                if (b instanceof ServerPlayer sp) sp.hurtMarked = true;
                sfx(a, b.position().add(0, 1.2, 0), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.9F, 0.9F);
            });
            case Act.SLAP -> pair(a, b, Anims.SLAP, Anims.SLAPPED, 0.8, 8, () -> {
                Vec3 face = b.position().add(0, 1.6, 0);
                sfx(a, face, SoundEvents.PLAYER_ATTACK_STRONG, 1F, 1.9F);
                particles(a, ParticleTypes.CRIT, face, 6, 0.05, 0.2);
            });
            case Act.FRISK -> pair(a, b, Anims.FRISK, Anims.FRISKED, 0.75, 56, () -> friskReport(a, b));
            case Act.BANDAGE -> pair(a, b, Anims.BANDAGE_GIVE, Anims.BANDAGE_TAKE, 0.75, 52, () -> {
                ItemStack med = a.getMainHandItem();
                if (!isMedical(med)) { msg(a, "Ya no tienes la venda en la mano"); return; }
                if (!a.isCreative()) med.shrink(1);
                b.heal(6F);
                b.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 80, 0));
                sfx(a, b.position().add(0, 1.2, 0), SoundEvents.ARMOR_EQUIP_LEATHER, 0.8F, 1.2F);
                particles(a, ParticleTypes.HAPPY_VILLAGER, b.position().add(0, 1.3, 0), 8, 0.3, 0.02);
            });
            case Act.GIVE -> pair(a, b, Anims.GIVE_OFFER, Anims.GIVE_TAKE, 0.85, 15, () -> giveItem(a, b));
            case Act.TOAST -> pair(a, b, Anims.TOAST, Anims.TOAST, 0.9, 12, () -> {
                sfx(a, between(a, b, 1.75), SoundEvents.AMETHYST_BLOCK_CHIME, 1F, 1.7F);
                later(18, () -> { sfx(a, a.position().add(0, 1.6, 0), SoundEvents.GENERIC_DRINK, 0.6F, 1F); sfx(b, b.position().add(0, 1.6, 0), SoundEvents.GENERIC_DRINK, 0.6F, 1.1F); });
            });
            case Act.RPS -> pair(a, b, Anims.RPS, Anims.RPS, 0.9, 30, () -> rpsResult(a, b));
            case Act.SECRET -> pair(a, b, Anims.SECRET_HANDSHAKE, Anims.SECRET_HANDSHAKE, 0.85, 6, () -> {
                sfx(a, between(a, b, 1.1), SoundEvents.PLAYER_ATTACK_WEAK, 0.6F, 1.5F);
                later(21, () -> sfx(a, between(a, b, 1.2), SoundEvents.PLAYER_ATTACK_WEAK, 0.7F, 1.0F));
                later(28, () -> { sfx(a, between(a, b, 1.6), SoundEvents.FIREWORK_ROCKET_BLAST, 0.5F, 1.6F); particles(a, ParticleTypes.FIREWORK, between(a, b, 1.7), 12, 0.2, 0.08); });
            });
            case Act.SLOW_DANCE -> pair(a, b, Anims.SLOW_DANCE_LEAD, Anims.SLOW_DANCE_FOLLOW, 0.45, 1, () -> {
                for (int i = 0; i < 6; i++) later(i * 20, () -> particles(a, ParticleTypes.NOTE, between(a, b, 2.3), 1, 0.3, 1));
                later(110, () -> particles(a, ParticleTypes.HEART, between(a, b, 2.1), 4, 0.25, 0.02));
            });
            case Act.BOOST -> pair(a, b, Anims.BOOST_GIVE, Anims.BOOST_TAKE, 0.6, 14, () -> {
                Vec3 d = b.position().subtract(a.position()).multiply(1, 0, 1);
                d = d.lengthSqr() < 1e-4 ? Vec3.ZERO : d.normalize().scale(-0.12);
                b.setDeltaMovement(d.x, 1.0, d.z);
                b.hasImpulse = true;
                b.fallDistance = 0;
                if (b instanceof ServerPlayer sp) sp.hurtMarked = true;
                sfx(a, a.position().add(0, 0.8, 0), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.7F, 1.2F);
            });
            default -> { }
        }
    }

    /** dar lo que se tiene en la mano: pasa entero al otro (si no le cabe, se le cae al lado) */
    private static void giveItem(ServerPlayer a, LivingEntity b) {
        ItemStack st = a.getMainHandItem();
        if (st.isEmpty()) { msg(a, "Ya no tienes nada en la mano"); return; }
        ItemStack give = st.copy();
        a.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        String what = give.getHoverName().getString() + (give.getCount() > 1 ? " x" + give.getCount() : "");
        if (b instanceof Player bp) { if (!bp.getInventory().add(give)) bp.drop(give, false); }
        else b.spawnAtLocation(give);
        msg(a, "Le diste " + what + " a " + b.getDisplayName().getString());
        if (b instanceof ServerPlayer bp) bp.displayClientMessage(Component.literal(a.getGameProfile().getName() + " te dio " + what), true);
        sfx(a, between(a, b, 1.1), SoundEvents.ITEM_PICKUP, 0.6F, 1.2F);
    }

    private static final String[] RPS_NAME = {"Piedra", "Papel", "Tijera"};

    /** piedra, papel o tijera: cada uno saca algo al azar y se anuncia a los dos */
    private static void rpsResult(ServerPlayer a, LivingEntity b) {
        int x = a.getRandom().nextInt(3), y = a.getRandom().nextInt(3);
        String na = a.getGameProfile().getName(), nb = b.getDisplayName().getString();
        String res = x == y ? "¡Empate!" : ((x - y + 3) % 3 == 1 ? "¡Gana " + na + "!" : "¡Gana " + nb + "!");
        Component c = Component.literal(na + ": " + RPS_NAME[x] + "  vs  " + nb + ": " + RPS_NAME[y] + "   " + res);
        a.sendSystemMessage(c);
        if (b instanceof ServerPlayer bp) bp.sendSystemMessage(c);
        sfx(a, between(a, b, 1.2), SoundEvents.NOTE_BLOCK_PLING.value(), 0.7F, x == y ? 1F : 1.5F);
    }

    /** al terminar de cachear: lo que el otro lleva a mano (cinturon / hotbar y mano izquierda) */
    private static void friskReport(ServerPlayer a, LivingEntity b) {
        List<String> found = new ArrayList<>();
        if (b instanceof Player bp) {
            for (int i = 0; i < 9; i++) {
                ItemStack st = bp.getInventory().getItem(i);
                if (!st.isEmpty()) found.add(st.getHoverName().getString() + (st.getCount() > 1 ? " x" + st.getCount() : ""));
            }
        } else if (!b.getMainHandItem().isEmpty()) found.add(b.getMainHandItem().getHoverName().getString());
        if (!b.getOffhandItem().isEmpty()) found.add(b.getOffhandItem().getHoverName().getString());
        String who = b.getDisplayName().getString();
        a.sendSystemMessage(Component.literal(found.isEmpty() ? "Cacheo de " + who + ": no lleva nada encima"
                : "Cacheo de " + who + ": " + String.join(", ", found)));
        sfx(a, a.position(), SoundEvents.ARMOR_EQUIP_LEATHER, 0.6F, 0.9F);
    }

    // ------------------------------------------------------------------ cargar ----
    private static AnimationManager.AnimationAccessor<StaticAnimation> carrierAnim(int mode) {
        return mode == 1 ? Anims.PIGGY_CARRY : mode == 2 ? Anims.BRIDAL_CARRY : Anims.CARRY_HOLD;
    }

    private static AnimationManager.AnimationAccessor<StaticAnimation> riderAnim(int mode) {
        return mode == 1 ? Anims.PIGGY_RIDE : mode == 2 ? Anims.BRIDAL_CARRIED : Anims.CARRIED;
    }

    /** Minecraft solo avisa de los pasajeros a quienes ven al vehiculo, no al propio jugador-vehiculo: sin esto, en la
     * pantalla de quien carga el cargado nunca se sube (queda parado donde empezo la accion haciendo la animacion). */
    private static void syncPassengers(ServerPlayer a) { a.connection.send(new ClientboundSetPassengersPacket(a)); }

    private static void startCarry(ServerPlayer a, ServerPlayer b, int mode) {
        if (a.isPassenger() || b.isPassenger() || a.isVehicle() || b.isVehicle()) { msg(a, "Ahora no se puede"); return; }
        if (!b.startRiding(a, true)) return;
        syncPassengers(a);
        CARRY.put(a.getUUID(), b.getUUID());
        CARRY_SLOT.put(a.getUUID(), a.getInventory().selected);
        AttributeInstance sp = a.getAttribute(Attributes.MOVEMENT_SPEED);
        if (sp != null && sp.getModifier(SLOW) == null)
            sp.addTransientModifier(new AttributeModifier(SLOW, "cargando a alguien", mode == 1 ? -0.15 : -0.25, AttributeModifier.Operation.MULTIPLY_TOTAL));
        emptyHands(a);
        CARRY_MODE.put(a.getUUID(), mode);
        play(a, carrierAnim(mode));
        play(b, riderAnim(mode));
        String[] verb = {"Cargando a ", "Llevas a caballito a ", "Llevas en brazos a "};
        String[] them = {"Te cargan al hombro", "Vas a caballito", "Te llevan en brazos"};
        msg(a, verb[mode] + b.getGameProfile().getName() + " (vuelve a pulsar la tecla de interaccion para soltarlo)");
        msg(b, them[mode] + " (agachate para bajarte)");
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
        Integer modeObj = CARRY_MODE.remove(a.getUUID());
        int mode = modeObj == null ? 0 : modeObj;
        stop(a, carrierAnim(mode));
        ServerPlayer b = a.server.getPlayerList().getPlayer(bid);
        if (b != null) {
            stop(b, riderAnim(mode));
            if (b.getVehicle() == a) b.stopRiding();
            syncPassengers(a);
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
        if (!a.server.isPvpAllowed() || !a.canHarmPlayer(v)) { msg(a, "El PvP esta desactivado"); return; }
        startExecute(a, v, false);
    }

    /** la misma animacion, pero contra un NPC de Easy NPC (ya se valido permiso/distancia en requestNpc): no lo mata,
     * solo se usa para ver como queda la animacion sin tener que buscar a otro jugador. */
    private static void executeNpc(ServerPlayer a, LivingEntity v) { startExecute(a, v, true); }

    private static void startExecute(ServerPlayer a, LivingEntity v, boolean npcVictim) {
        if (!isBlade(a.getMainHandItem())) { msg(a, "Necesitas un cuchillo, espada o hacha en la mano"); return; }
        if (!sneaking(a)) { msg(a, "Tienes que ir agachado"); return; }
        if (!behind(a, v)) { msg(a, "Tienes que estar a su espalda"); return; }
        if (a.distanceTo(v) > 2.2) { msg(a, "Acercate mas"); return; }
        float yaw = v.getYRot();
        Vec3 f = Vec3.directionFromRotation(0, yaw);
        Vec3 pv = v.position();
        Vec3 pa = pv.subtract(f.scale(0.45));
        EXECS.add(new Exec(a.getUUID(), v.getUUID(), pa, pv, yaw, npcVictim, 0));
        play(a, Anims.EXEC_ATTACKER);
        play(v, Anims.EXEC_VICTIM);
        a.level().playSound(null, a.getX(), a.getY(), a.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.7F, 0.7F);
    }

    private static void tickExecs(net.minecraft.server.MinecraftServer s) {
        for (Iterator<Exec> it = EXECS.iterator(); it.hasNext(); ) {
            Exec e = it.next();
            ServerPlayer a = s.getPlayerList().getPlayer(e.attacker);
            Entity vEnt = a != null && a.level() instanceof ServerLevel lv ? lv.getEntity(e.victim) : null;
            LivingEntity v = vEnt instanceof LivingEntity le ? le : null;
            e.tick++;
            if (a == null || v == null || !a.isAlive() || e.tick > e.length()) { it.remove(); continue; }
            // los dos quietos en su sitio durante la animacion
            if (e.tick % 2 == 0 || e.tick < 3) {
                a.connection.teleport(e.posA.x, e.posA.y, e.posA.z, e.yaw, a.getXRot());
                if (v.isAlive()) face(v, e.posV.x, e.posV.y, e.posV.z, e.yaw, v.getXRot());
            }
            if (e.kind != 0) { tickHold(e, a, v); continue; }
            if (e.tick == 18 && v.isAlive() && v.level() instanceof ServerLevel lv) {
                lv.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1F, 0.8F);
                Vec3 neck = v.position().add(0, 1.45, 0);
                lv.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()), neck.x, neck.y, neck.z, 40, 0.12, 0.08, 0.12, 0.3);
            }
            if (e.tick > 18 && e.tick < 30 && e.tick % 3 == 0 && v.level() instanceof ServerLevel lv) {
                Vec3 neck = v.position().add(0, 1.0, 0);
                lv.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()), neck.x, neck.y, neck.z, 8, 0.1, 0.2, 0.1, 0.1);
            }
            // al NPC de prueba no lo matamos: es solo para ver la animacion sin gastar un NPC cada vez
            if (e.tick == 30 && v.isAlive() && !e.npcVictim) v.hurt(a.damageSources().playerAttack(a), Float.MAX_VALUE);
        }
    }

    /** agachado ahora, o hace menos de 4 s: al abrir el menu (G) el personaje se levanta y si no nunca valdria */
    private static boolean sneaking(ServerPlayer a) {
        return a.isShiftKeyDown() || now - LAST_SNEAK.getOrDefault(a.getUUID(), -1000L) < 80;
    }

    // ------------------------------------------------------------------ por la espalda, sin pedido ----
    private static void instant(ServerPlayer a, LivingEntity v, int action, boolean npc) {
        boolean pvpOk = npc || !(v instanceof ServerPlayer vp) || (a.server.isPvpAllowed() && a.canHarmPlayer(vp));
        switch (action) {
            case Act.EXECUTE -> {
                if (!pvpOk) { msg(a, "El PvP esta desactivado"); return; }
                startExecute(a, v, npc);
            }
            case Act.CHOKE -> {
                if (!pvpOk) { msg(a, "El PvP esta desactivado"); return; }
                if (!a.getMainHandItem().isEmpty()) { msg(a, "Necesitas las manos libres"); return; }
                startHold(a, v, npc, 1, 0.4, Anims.CHOKE_ATTACKER, Anims.CHOKE_VICTIM);
            }
            case Act.ARREST -> {
                if (!(v instanceof ServerPlayer)) { msg(a, "Solo se puede esposar a un jugador"); return; }
                if (!CuffedSupport.loaded()) { msg(a, "Hace falta el mod Cuffed"); return; }
                if (!CuffedSupport.isRestraint(a.getMainHandItem())) { msg(a, "Necesitas unas esposas (Cuffed) en la mano"); return; }
                startHold(a, v, npc, 2, 0.45, Anims.ARREST_OFFICER, Anims.ARREST_SUSPECT);
            }
            case Act.SCARE -> {
                if (!behind(a, v)) { msg(a, "Tienes que estar a su espalda"); return; }
                if (a.distanceTo(v) > 3.0) { msg(a, "Acercate mas"); return; }
                play(a, Anims.SCARE_ATTACKER);
                later(6, () -> {
                    play(v, Anims.SCARED);
                    sfx(a, v.position().add(0, 1.6, 0), SoundEvents.ENDERMAN_SCREAM, 0.7F, 1.4F);
                    if (v instanceof ServerPlayer vp) vp.displayClientMessage(Component.literal("¡BU!"), true);
                });
            }
            default -> { }
        }
    }

    /** llave de sueno / arresto: los dos quietos durante la animacion, como la ejecucion */
    private static void startHold(ServerPlayer a, LivingEntity v, boolean npc, int kind, double dist,
                                  AnimationManager.AnimationAccessor<StaticAnimation> animA, AnimationManager.AnimationAccessor<StaticAnimation> animV) {
        if (!behind(a, v)) { msg(a, "Tienes que estar a su espalda"); return; }
        if (a.distanceTo(v) > 2.2) { msg(a, "Acercate mas"); return; }
        float yaw = v.getYRot();
        Vec3 f = Vec3.directionFromRotation(0, yaw);
        Vec3 pv = v.position();
        EXECS.add(new Exec(a.getUUID(), v.getUUID(), pv.subtract(f.scale(dist)), pv, yaw, npc, kind));
        play(a, animA);
        play(v, animV);
        sfx(a, a.position(), SoundEvents.ARMOR_EQUIP_LEATHER, 0.8F, 0.8F);
    }

    private static void tickHold(Exec e, ServerPlayer a, LivingEntity v) {
        if (e.kind == 1) {   // llave de sueno: forcejeo y al final cae dormido un rato
            if (e.tick % 8 == 0 && e.tick < 40) sfx(v, v.position().add(0, 1.4, 0), SoundEvents.PLAYER_HURT, 0.3F, 0.6F);
            if (e.tick == 50 && v.isAlive()) {
                v.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.BLINDNESS, 120, 0));
                v.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 120, 4));
                v.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, 160, 1));
                v.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.CONFUSION, 140, 0));
                if (v instanceof ServerPlayer vp) vp.displayClientMessage(Component.literal("Te quedas sin aire... todo se vuelve negro"), true);
            }
        } else if (e.kind == 2 && e.tick == 30 && v instanceof ServerPlayer vp) {   // arresto: clic de las esposas
            String err = CuffedSupport.cuff(vp, a, a.getMainHandItem());
            if (err != null) msg(a, err);
            else {
                sfx(a, vp.position().add(0, 1.0, 0), SoundEvents.CHAIN_PLACE, 1F, 1.3F);
                msg(a, "Has esposado a " + vp.getGameProfile().getName());
                vp.displayClientMessage(Component.literal("Te han esposado"), true);
            }
        }
    }

    /** lo que hace falta ANTES de mandar la solicitud (asi no se pide algo que luego no se puede hacer) */
    private static boolean precheck(ServerPlayer p, int action) {
        if (action == Act.BANDAGE && !isMedical(p.getMainHandItem())) { msg(p, "Necesitas una venda o un botiquin en la mano"); return false; }
        if (action == Act.GIVE && p.getMainHandItem().isEmpty()) { msg(p, "No tienes nada en la mano para dar"); return false; }
        return true;
    }

    static boolean isMedical(ItemStack s) {
        if (s.isEmpty()) return false;
        var id = ForgeRegistries.ITEMS.getKey(s.getItem());
        String n = id == null ? "" : id.getPath();
        return n.contains("bandage") || n.contains("medkit") || n.contains("med_kit") || n.contains("first_aid") || n.contains("firstaid")
                || n.contains("gauze") || n.contains("medic") || n.contains("syringe") || n.contains("splint") || n.contains("venda");
    }

    // ------------------------------------------------------------------ eventos ----
    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        now++;
        var s = e.getServer();
        // se sacan primero: un efecto puede programar otros (palmadas, notas del baile...) mientras se recorre la lista
        List<Runnable> due = new ArrayList<>();
        for (Iterator<Object[]> it = LATER.iterator(); it.hasNext(); ) {
            Object[] o = it.next();
            if ((long) o[0] <= now) { it.remove(); due.add((Runnable) o[1]); }
        }
        for (Runnable r : due) r.run();
        tickExecs(s);
        for (ServerPlayer sp : s.getPlayerList().getPlayers()) if (sp.isShiftKeyDown()) LAST_SNEAK.put(sp.getUUID(), now);
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
            if (now % 40 == 0) syncPassengers(a);      // por si el cliente de quien carga lo perdio (cambio de chunk, lag...)
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
        if (e.getEntity() instanceof ServerPlayer p) { stopCarry(p, false); carriedBy(p); PENDING.remove(p.getUUID()); LAST_SNEAK.remove(p.getUUID()); }
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
