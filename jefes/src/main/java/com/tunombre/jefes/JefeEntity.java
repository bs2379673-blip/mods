package com.tunombre.jefes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tunombre.jefes.core.Act;
import com.tunombre.jefes.core.Mat4;
import com.tunombre.jefes.core.Model;
import com.tunombre.jefes.core.Pose;
import com.tunombre.jefes.core.Rig;
import com.tunombre.jefes.core.Spec;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.JumpControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Un jefe: no piensa. Un administrador lo maneja (ver {@link Possession}) y el servidor solo ejecuta lo que pide:
 * moverse, saltar, zarpazos, rugidos, agarrar. Al agarrar a un jugador lo sube como pasajero a su mano, arranca la
 * cadena de quick time events y, si falla, lo mata con un screamer.
 */
public class JefeEntity extends Mob {
    private static final EntityDataAccessor<String> BOSS = SynchedEntityData.defineId(JefeEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> ACT = SynchedEntityData.defineId(JefeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SEQ = SynchedEntityData.defineId(JefeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> FLAGS = SynchedEntityData.defineId(JefeEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> STRUGGLE = SynchedEntityData.defineId(JefeEntity.class, EntityDataSerializers.BYTE);
    private static final int F_SPRINT = 1, F_CROUCH = 2, F_CONTROLLED = 4;

    // servidor
    private UUID controller;
    private Net.Input in;
    private int inTick, actTicks, airTicks, alt, killAt;
    private double lastX, lastZ, stepAcc;
    private boolean steppedLeft, releasing, qteOn;
    private Integer victimId;
    // cliente
    private int clientActStart;
    // comun
    private Pose rigPose;
    private Model rigModel;

    public JefeEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setMaxUpStep(1.3F);
        noCulling = true;
        moveControl = new MoveControl(this) { @Override public void tick() { } };
        lookControl = new LookControl(this) { @Override public void tick() { } };
        jumpControl = new JumpControl(this) { @Override public void tick() { } };
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 1000).add(Attributes.MOVEMENT_SPEED, 0.05).add(Attributes.KNOCKBACK_RESISTANCE, 1).add(Attributes.FOLLOW_RANGE, 16);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(BOSS, "");
        entityData.define(ACT, 0);
        entityData.define(SEQ, 0);
        entityData.define(FLAGS, (byte) 0);
        entityData.define(STRUGGLE, (byte) 0);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key == SEQ && level().isClientSide) clientActStart = tickCount;
        if (key == BOSS) refreshDimensions();
    }

    // ------------------------------------------------------------------ datos ----
    public String getBossId() { return entityData.get(BOSS); }

    public void setBossId(String id) { entityData.set(BOSS, id); refreshDimensions(); }

    public Boss getBoss() { return Bosses.get(level().isClientSide, getBossId()); }

    public Act getAct() { return Act.of(entityData.get(ACT)); }

    public boolean isSprinting2() { return (entityData.get(FLAGS) & F_SPRINT) != 0; }

    public boolean isCrouching2() { return (entityData.get(FLAGS) & F_CROUCH) != 0; }

    public float struggle() { return entityData.get(STRUGGLE) / 100F; }

    public UUID getController() { return controller; }

    public void setController(UUID id) { controller = id; setFlag(F_CONTROLLED, id != null); }

    public String displayName() { Boss b = getBoss(); return b == null ? "Jefe" : b.spec.name; }

    @Override
    protected Component getTypeName() { return Component.literal(displayName()); }

    private void setFlag(int f, boolean on) {
        byte v = entityData.get(FLAGS);
        byte n = (byte) (on ? v | f : v & ~f);
        if (n != v) entityData.set(FLAGS, n);
    }

    private void setAct(Act a) {
        entityData.set(ACT, a.ordinal());
        entityData.set(SEQ, entityData.get(SEQ) + 1);
        actTicks = 0;
    }

    /** segundos de la accion actual, con parcial (cliente) */
    public float actTime(float pt) { return level().isClientSide ? Math.max(0F, tickCount - clientActStart + pt) : actTicks; }

    @Override
    public EntityDimensions getDimensions(net.minecraft.world.entity.Pose p) {
        Boss b = getBoss();
        return b == null ? super.getDimensions(p) : EntityDimensions.scalable(b.spec.width, b.spec.height);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); t.putString("Boss", getBossId()); }

    @Override
    public void readAdditionalSaveData(CompoundTag t) { super.readAdditionalSaveData(t); setBossId(t.getString("Boss")); }

    // ------------------------------------------------------------------ inmortal, sin empuje ----
    @Override
    public boolean isInvulnerableTo(DamageSource src) { return !src.is(DamageTypeTags.BYPASSES_INVULNERABILITY); }

    @Override public boolean causeFallDamage(float a, float b, DamageSource s) { return false; }

    @Override public boolean isPushable() { return false; }

    @Override public boolean removeWhenFarAway(double d) { return false; }

    @Override public boolean canBeAffected(net.minecraft.world.effect.MobEffectInstance e) { return false; }

    // ------------------------------------------------------------------ pasajero (la victima cuelga de la mano) ----
    public Pose rig(Model m) {
        if (rigPose == null || rigModel != m) { rigModel = m; rigPose = new Pose(m); }
        return rigPose;
    }

    /** donde esta la mano que agarra, en el mundo, en la postura de esta accion */
    public Vec3 handPoint(float t) {
        Boss b = getBoss();
        if (b == null) return position().add(0, 3, 0);
        Spec s = b.spec;
        Pose p = rig(b.model);
        p.reset();
        Act a = getAct();
        Rig.stance(b.model, s, p, a.stance() ? a : Act.HOLD, t, struggle(), (level().isClientSide ? tickCount : tickCount) * 50.0);
        Mat4[] w = p.world(Rig.root(b.model, s.scale, getYRot()));
        JsonObject h = s.grab.getAsJsonObject("hand");
        JsonArray lp = h.getAsJsonArray("p");
        float[] o = new float[3];
        w[b.model.idx(h.get("cube").getAsInt())].point(lp.get(0).getAsFloat(), lp.get(1).getAsFloat(), lp.get(2).getAsFloat(), o);
        return position().add(o[0], o[1], o[2]);
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        if (!hasPassenger(passenger)) return;
        Boss b = getBoss();
        float hang = b == null ? 1.25F : b.spec.grab.get("hang").getAsFloat();
        Vec3 h = handPoint(actTime(1F));
        move.accept(passenger, h.x, h.y - hang, h.z);
        passenger.setYBodyRot(getYRot() + 180F);
        passenger.setYHeadRot(getYRot() + 180F);
    }

    @Override public boolean shouldRiderSit() { return false; }

    @Override protected boolean canAddPassenger(Entity e) { return getPassengers().isEmpty() && e instanceof Player; }

    @Override public LivingEntity getControllingPassenger() { return null; }

    public boolean mayDismount(Entity e) { return releasing || !e.isAlive() || e.isRemoved() || isRemoved(); }

    private ServerPlayer victim() {
        if (victimId == null) return null;
        Entity e = level().getEntity(victimId);
        return e instanceof ServerPlayer sp && sp.isAlive() && hasPassenger(sp) ? sp : null;
    }

    // ------------------------------------------------------------------ control (lo llama Possession) ----
    public void setInput(Net.Input i) { in = i; inTick = tickCount; }

    public void releaseControl() { controller = null; in = null; setFlag(F_CONTROLLED, false); setFlag(F_SPRINT, false); setFlag(F_CROUCH, false); setZza(0); }

    /** acciones del administrador: 1 zarpazo, 3 golpe fuerte, 4 rugido, 5 agarrar, 6 olfatear, 8 gruñido, 9 voz, 10 salto, 11 soltar/lanzar, 12 matar al agarrado */
    public void doAction(int id) {
        if (level().isClientSide || !isAlive()) return;
        Act a = getAct();
        switch (id) {
            case 8 -> growl(1F);
            case 9 -> sound(Jefes.VOICE.get(), 1.4F, 0.9F + random.nextFloat() * 0.2F);
            case 11 -> { if (a == Act.HOLD) throwVictim(); }
            case 12 -> { if (a == Act.HOLD) startKill(); }
            default -> {
                if (a != Act.NONE && a != Act.LAND) return;
                switch (id) {
                    case 1 -> { setAct((alt++ & 1) == 0 ? Act.ATTACK_L : Act.ATTACK_R); }
                    case 3 -> setAct(Act.SMASH);
                    case 4 -> setAct(Act.ROAR);
                    case 5 -> setAct(Act.REACH);
                    case 6 -> setAct(Act.SNIFF);
                    case 10 -> { if (onGround()) setAct(Act.LEAP); }
                    default -> { }
                }
            }
        }
    }

    // ------------------------------------------------------------------ sonidos ----
    public void sound(SoundEvent e, float vol, float pitch) { level().playSound(null, getX(), getY() + 2, getZ(), e, SoundSource.HOSTILE, vol, pitch); }

    private void growl(float v) { sound(Jefes.GROWL.get(), 2.2F * v, 0.9F + random.nextFloat() * 0.2F); }

    // ------------------------------------------------------------------ servidor ----
    @Override
    protected void customServerAiStep() {
        Boss b = getBoss();
        if (b == null) return;
        Spec s = b.spec;
        actTicks++;
        Act a = getAct();
        boolean ctl = controller != null && in != null && tickCount - inTick < 12;
        boolean crouch = false, sprint = false;
        float fwd = 0F;
        float yaw = getYRot();
        boolean canMove = !a.stance() && a != Act.ROAR && a != Act.SMASH && a != Act.STAGGER && a != Act.LEAP && a != Act.SNIFF;
        if (ctl) {
            crouch = in.crouch();
            sprint = in.sprint() && !crouch;
            float mx = in.strafe(), mz = in.fwd();
            float mag = Math.min(1F, (float) Math.hypot(mx, mz));
            float turn = s.turn * (a == Act.NONE || a == Act.LAND ? 1F : 0.4F);
            if (!a.stance() && a != Act.LEAP) {
                if (mag > 0.05F) {
                    double r = Math.toRadians(in.yaw());
                    double vx = -Math.sin(r) * mz + Math.cos(r) * mx, vz = Math.cos(r) * mz + Math.sin(r) * mx;
                    float ty = (float) Math.toDegrees(Math.atan2(-vx, vz));
                    float d = Mth.wrapDegrees(ty - yaw);
                    float step = Mth.clamp(d, -turn, turn);
                    yaw += step;
                    fwd = mag * Math.max(0F, (float) Math.cos(Math.toRadians(d - step)));
                } else {
                    float d = Mth.wrapDegrees(in.yaw() - yaw);
                    if (Math.abs(d) > 75F) yaw += Mth.clamp(d, -turn * 0.6F, turn * 0.6F);
                }
            }
            float head = Mth.clamp(Mth.wrapDegrees(in.yaw() - yaw), -75F, 75F);
            setYHeadRot(yaw + head);
            if (in.jump() && onGround() && (a == Act.NONE || a == Act.LAND) && !crouch) {
                Vec3 dm = getDeltaMovement();
                double boost = 0.12 + (sprint ? 0.2 : 0) * fwd;
                double r = Math.toRadians(yaw);
                setDeltaMovement(dm.x + -Math.sin(r) * boost * fwd, s.jump, dm.z + Math.cos(r) * boost * fwd);
                hasImpulse = true;
            }
        } else if (!a.stance()) {
            setYHeadRot(yaw);
        }
        setYRot(yaw);
        yBodyRot = yaw;
        setFlag(F_SPRINT, sprint && fwd > 0.05F);
        setFlag(F_CROUCH, crouch);
        float v = crouch ? s.sneak : sprint ? s.sprint : s.walk;
        if (a == Act.ATTACK_L || a == Act.ATTACK_R) v *= 0.35F;
        if (!canMove && a != Act.ATTACK_L && a != Act.ATTACK_R) { v = 0; fwd = 0; }
        setZza(fwd);
        setSpeed(v / 2.16F);

        // aterrizaje
        if (!onGround()) airTicks++;
        else {
            if (airTicks > 9 && (a == Act.NONE || a == Act.LAND || a == Act.LEAP)) {
                setAct(Act.LAND);
                sound(Jefes.LAND.get(), 2.5F, 0.8F + random.nextFloat() * 0.2F);
                if (a == Act.LEAP) melee(s.reach * 0.9F, 0.6F, true);
            }
            airTicks = 0;
        }

        // pasos
        double moved = Math.hypot(getX() - lastX, getZ() - lastZ);
        lastX = getX(); lastZ = getZ();
        if (onGround() && moved > 0.01 && !a.stance()) {
            stepAcc += moved;
            float stride = s.stride * (crouch ? 0.7F : 1F);
            if (stepAcc >= stride) {
                stepAcc -= stride;
                steppedLeft = !steppedLeft;
                sound(Jefes.STEP.get(), crouch ? 1.2F : sprint ? 2.8F : 2.0F, (steppedLeft ? 0.9F : 1.0F) + random.nextFloat() * 0.1F - (sprint ? 0.1F : 0F));
            }
        }
        if (controller == null && Jefes.cfgAmbient() && a == Act.NONE && random.nextInt(170) == 0) growl(0.8F);

        runAct(b, s, a);
    }

    private void runAct(Boss b, Spec s, Act a) {
        int t = actTicks;
        switch (a) {
            case ATTACK_L, ATTACK_R -> {
                if (t == 4) sound(Jefes.ATTACK.get(), 1.6F, 0.9F + random.nextFloat() * 0.2F);
                if (t == 9 && melee(s.reach, 1F, false)) sound(Jefes.HIT.get(), 1.8F, 0.9F + random.nextFloat() * 0.2F);
                if (t >= a.ticks) setAct(Act.NONE);
            }
            case SMASH -> {
                if (t == 12) sound(Jefes.ATTACK.get(), 2.0F, 0.6F);
                if (t == 19) {
                    sound(Jefes.LAND.get(), 3F, 0.7F);
                    if (melee(s.reach * 1.2F, 1.6F, true)) sound(Jefes.HIT.get(), 2F, 0.7F);
                }
                if (t >= a.ticks) setAct(Act.NONE);
            }
            case ROAR -> {
                if (t == 2) sound(Jefes.ROAR.get(), 3F, 0.9F + random.nextFloat() * 0.15F);
                if (t >= a.ticks) setAct(Act.NONE);
            }
            case SNIFF -> {
                if (t == 6) sound(Jefes.SNIFF.get(), 1.4F, 1F);
                if (t == 56) sound(Jefes.SNIFF.get(), 1.4F, 0.9F);
                if (t >= a.ticks) setAct(Act.NONE);
            }
            case LEAP -> {
                if (t == 12) {
                    double r = Math.toRadians(getYRot());
                    setDeltaMovement(-Math.sin(r) * 0.75, s.jump * 1.1, Math.cos(r) * 0.75);
                    hasImpulse = true;
                    sound(Jefes.ROAR.get(), 2F, 1.2F);
                }
                if (t > 150) setAct(Act.NONE);
            }
            case LAND, STAGGER -> { if (t >= a.ticks) setAct(Act.NONE); }
            case REACH -> {
                if (t == 3) sound(Jefes.ATTACK.get(), 1.4F, 0.7F);
                if (t == 10) tryGrab(s);
                else if (t >= a.ticks + 6) { setAct(Act.NONE); }
            }
            case HOLD -> holdTick(b, s, t);
            case KILL -> killTick(s, t);
            case THROW -> {
                if (t == 6) releaseVictim(true);
                if (t >= a.ticks + 10) setAct(Act.NONE);
            }
            default -> { }
        }
    }

    /** zarpazo: golpea a lo que haya delante. devuelve si dio a algo */
    private boolean melee(float reach, float dmgMul, boolean round) {
        Boss b = getBoss();
        if (b == null) return false;
        double r = Math.toRadians(getYRot());
        Vec3 fwd = new Vec3(-Math.sin(r), 0, Math.cos(r));
        Vec3 c = position().add(fwd.scale(round ? 0 : reach * 0.45));
        AABB box = new AABB(c, c).inflate(round ? reach : reach * 0.65, 1.8, round ? reach : reach * 0.65).move(0, 1.5, 0);
        boolean hit = false;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box, e -> e != this && !(e instanceof JefeEntity) && !e.isSpectator() && e.isAlive()
                && !(controller != null && controller.equals(e.getUUID())) && e != victim())) {
            Vec3 to = e.position().subtract(position()).multiply(1, 0, 1);
            if (!round && to.lengthSqr() > 0.01 && to.normalize().dot(fwd) < 0.2) continue;
            if (e instanceof Player pl && (pl.isCreative() || pl.isSpectator())) continue;
            if (e.hurt(damageSources().mobAttack(this), (float) (b.spec.damage * dmgMul * Jefes.cfgDamage()))) {
                hit = true;
                e.knockback(b.spec.knock * (round ? 1.3 : 1.0), -to.x, -to.z);
            }
        }
        return hit;
    }

    // ------------------------------------------------------------------ agarre ----
    private void tryGrab(Spec s) {
        double r = Math.toRadians(getYRot());
        Vec3 fwd = new Vec3(-Math.sin(r), 0, Math.cos(r));
        Vec3 centre = position().add(fwd.scale(s.reach * 0.55));
        AABB box = new AABB(centre, centre).inflate(s.reach * 0.7, 3.2, s.reach * 0.7).move(0, 1.5, 0);
        List<ServerPlayer> l = level().getEntitiesOfClass(ServerPlayer.class, box, p -> p.isAlive() && !p.isCreative() && !p.isSpectator() && !p.isPassenger()
                && !(controller != null && controller.equals(p.getUUID())) && p.distanceTo(this) <= s.reach + 0.8 + p.getBbWidth()
                && p.position().subtract(position()).multiply(1, 0, 1).normalize().dot(fwd) > 0.25);
        ServerPlayer t = l.stream().min(Comparator.comparingDouble(p -> p.distanceToSqr(centre))).orElse(null);
        if (t == null) { setAct(Act.NONE); return; }
        if (!Qte.available(t, QteDefs.phases(getBoss()))) {
            ServerPlayer adm = controller == null || level().getServer() == null ? null : level().getServer().getPlayerList().getPlayer(controller);
            if (adm != null) adm.displayClientMessage(Component.literal("§c" + t.getGameProfile().getName() + " no tiene el mod QuickTime Events (o no esta activo): no se lo puede agarrar."), true);
            setAct(Act.NONE);
            return;
        }
        if (!t.startRiding(this, true)) { setAct(Act.NONE); return; }
        victimId = t.getId();
        qteOn = false;
        entityData.set(STRUGGLE, (byte) 0);
        setAct(Act.HOLD);
        sound(Jefes.GRAB.get(), 2F, 0.9F);
        Net.toPlayer(t, new Net.Cine(getId()));
        Jefes.LOG.info("[jefes] {} agarro a {}", getBossId(), t.getGameProfile().getName());
        final ServerPlayer sp = t;
        qteOn = Qte.start(sp, QteDefs.phases(getBoss()), new Qte.Run() {
            @Override public void press(int phase, int presses, int required) { onQtePress(phase, presses, required); }

            @Override public void phaseWon(int phase) { sound(Jefes.ROAR.get(), 2.2F, 1.0F + phase * 0.1F); }

            @Override public void end(boolean won, String reason) { onQteEnd(sp, won, reason); }
        });
        if (!qteOn) { forceRelease(sp); setAct(Act.NONE); }
    }

    private void onQtePress(int phase, int n, int required) {
        if (getAct() != Act.HOLD) return;
        entityData.set(STRUGGLE, (byte) Mth.clamp(Math.round(100F * n / Math.max(1, required)), 0, 100));
        if (n % 3 == 0) growl(0.9F);
    }

    private void onQteEnd(ServerPlayer p, boolean won, String reason) {
        qteOn = false;
        if (getAct() != Act.HOLD || victim() != p) return;
        if (won) throwVictim();
        else { Jefes.LOG.info("[jefes] {} perdio el quick time ({})", p.getGameProfile().getName(), reason); startKill(); }
    }

    private void holdTick(Boss b, Spec s, int t) {
        ServerPlayer v = victim();
        if (v == null) { setAct(Act.NONE); return; }
        if (t % 28 == 5) growl(0.7F);
        if (t > Jefes.cfgHoldLimit() * 20) startKill();
    }

    private void throwVictim() {
        ServerPlayer vv = victim();
        if (vv != null) Qte.cancel(vv);
        qteOn = false;
        entityData.set(STRUGGLE, (byte) 0);
        setAct(Act.THROW);
        sound(Jefes.ROAR.get(), 2.4F, 1.1F);
    }

    private void startKill() {
        ServerPlayer v = victim();
        if (v != null) Qte.cancel(v);
        qteOn = false;
        Boss b = getBoss();
        float secs = b != null && b.spec.screamer.has("secs") ? b.spec.screamer.get("secs").getAsFloat() : 2.5F;
        killAt = 36 + Math.round(secs * 20F);
        setAct(Act.KILL);
        sound(Jefes.GRAB.get(), 2.4F, 0.6F);
    }

    private void killTick(Spec s, int t) {
        ServerPlayer v = victim();
        if (t == 22) sound(Jefes.ROAR.get(), 2.6F, 0.8F);
        if (t == 30 && v != null) {
            sound(Jefes.CHOMP.get(), 2.6F, 0.8F);
            screamer(v, s);
        }
        if (t == killAt && v != null) {
            releasing = true;
            v.hurt(new DamageSource(level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(Jefes.ATRAPADO), this), Float.MAX_VALUE);
            if (v.isAlive()) forceRelease(v);       // totem de la inmortalidad: lo suelta
            releasing = false;
        }
        if (t >= killAt + 26) {
            if (v != null) forceRelease(v);
            setAct(Act.NONE);
        }
    }

    private void screamer(ServerPlayer v, Spec s) {
        String img = s.screamer.has("image") ? s.screamer.get("image").getAsString() : "";
        float secs = s.screamer.has("secs") ? s.screamer.get("secs").getAsFloat() : 2.5F;
        boolean tac = !img.isEmpty() && net.minecraftforge.fml.ModList.get().isLoaded("tacui") && level().getServer() != null;
        if (tac) {
            String snd = s.screamer.has("sound") ? s.screamer.get("sound").getAsString() : "";
            level().getServer().getCommands().performPrefixedCommand(level().getServer().createCommandSourceStack().withSuppressedOutput(),
                    "tacshow " + v.getGameProfile().getName() + " " + img + " " + String.format(java.util.Locale.ROOT, "%.1f", secs) + " center " + (snd.isEmpty() ? "jefes:scream" : snd));
        }
        Net.toPlayer(v, new Net.Scream(secs, !tac));
    }

    private void releaseVictim(boolean thrown) {
        ServerPlayer v = victim();
        if (v == null) return;
        forceRelease(v);
        if (thrown) {
            double r = Math.toRadians(getYRot());
            v.setDeltaMovement(-Math.sin(r) * 1.1, 0.55, Math.cos(r) * 1.1);
            v.hurtMarked = true;
        }
    }

    /** suelta a ese jugador y deja al jefe en un estado limpio (muerte, desconexion, quick time ganado...) */
    public void forceRelease(Player p) {
        if (p instanceof ServerPlayer sp) {
            Qte.cancel(sp);
            Net.toPlayer(sp, new Net.Cine(-1));
        }
        boolean was = releasing;
        releasing = true;
        if (p.getVehicle() == this) p.stopRiding();
        releasing = was;
        if (victimId != null && victimId == p.getId()) victimId = null;
        qteOn = false;
        entityData.set(STRUGGLE, (byte) 0);
        if (getAct() == Act.HOLD) setAct(Act.NONE);
    }

    public void forceReleaseAll() {
        for (Entity e : List.copyOf(getPassengers())) if (e instanceof Player p) forceRelease(p);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!level().isClientSide) forceReleaseAll();
        super.remove(reason);
    }

    @Override
    public void die(DamageSource src) {
        if (!level().isClientSide) forceReleaseAll();
        super.die(src);
    }
}
