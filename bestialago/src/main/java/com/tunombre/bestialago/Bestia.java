package com.tunombre.bestialago;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;
import net.minecraftforge.network.PacketDistributor;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;

import java.util.List;
import java.util.UUID;

/**
 * La BESTIA DEL LAGO. Inmortal, gigante, solo nada. Se spawnea a mano (/summon bestialago:bestia o su huevo) dentro del
 * lago y desde ese momento lo vigila: su "casa" es donde aparecio y patrulla en un radio (por defecto 64 bloques).
 * <p>
 * Estados (sincronizados): PATRULLA (hondo, lento), ACECHO (justo bajo la superficie, se le ven las aletas), RUGIDO
 * (asoma la cabeza), EMBESTIDA (sube desde abajo y rompe el bote), ATAQUE (boca abierta a toda velocidad), ATRAPADO
 * (el jugador va dentro de la boca sujetando las mandibulas; quick time event), TRAGAR, RETIRADA.
 * Todo el movimiento lo calcula el servidor; el cliente interpola y anima (ver BestiaAnim).
 */
public class Bestia extends PathfinderMob {
    public static final float WIDTH = 4.5F, HEIGHT = 3.2F;
    private static final float R2D = (float) (180.0 / Math.PI), D2R = (float) (Math.PI / 180.0);

    public static final byte PATROL = 0, STALK = 1, ROAR = 2, RAM = 3, LUNGE = 4, GRAB = 5, SWALLOW = 6, RETREAT = 7;

    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(Bestia.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> JAW = SynchedEntityData.defineId(Bestia.class, EntityDataSerializers.BYTE);

    // ---- servidor ----
    private BlockPos home;
    private int radius = 64;
    private double surfaceY = Double.NaN;
    private Vec3 goal;
    private int goalTicks;
    private UUID targetId;
    private int lostTicks;
    private int stateTicks;
    private int cooldown;
    private int ramPhase;
    private boolean releasing;
    /** hay una cadena de quick time events (mod QuickTime Events) en curso; el cliente la dibuja y la juega */
    private boolean qteOn;
    private int presses;
    private double speed;
    private float turnBias;
    private int roarCd;

    // ---- animacion (cliente y servidor) ----
    public float phase, phaseO, swim, swimO, turn, turnO, jaw, jawO, shake, shakeO, lift, liftO;

    private final BestiaPart[] parts;
    private final BestiaPart head, tail1, tail2, tail3;

    public Bestia(EntityType<? extends Bestia> type, Level level) {
        super(type, level);
        this.head = new BestiaPart(this, "head", 4.6F, 3.0F);
        this.tail1 = new BestiaPart(this, "tail1", 3.6F, 2.6F);
        this.tail2 = new BestiaPart(this, "tail2", 2.8F, 2.1F);
        this.tail3 = new BestiaPart(this, "tail3", 2.0F, 1.6F);
        this.parts = new BestiaPart[]{head, tail1, tail2, tail3};
        this.setId(ENTITY_COUNTER.getAndAdd(parts.length + 1) + 1);
        this.setNoGravity(true);
        this.noCulling = true;
        this.xpReward = 0;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1000.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 96.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(STATE, PATROL);
        entityData.define(JAW, (byte) 0);
    }

    public byte getState() { return entityData.get(STATE); }

    private void setState(byte s) {
        if (getState() != s) {
            entityData.set(STATE, s);
            stateTicks = 0;
        }
    }

    private void setJaw(float open01) { entityData.set(JAW, (byte) Mth.clamp(Math.round(open01 * 100), 0, 100)); }

    // ------------------------------------------------------------------ partes (hitbox) ----
    @Override public void setId(int id) {
        super.setId(id);
        for (int i = 0; i < parts.length; i++) parts[i].setId(id + i + 1);
    }
    @Override public boolean isMultipartEntity() { return true; }
    @Override public PartEntity<?>[] getParts() { return parts; }

    private void placeParts() {
        Vec3 f = Vec3.directionFromRotation(getXRot(), getYRot());
        Vec3 c = position().add(0, HEIGHT / 2, 0);
        float k = BestiaAnim.SCALE / 16F;
        placePart(head, c.add(f.scale(30 * k)));
        placePart(tail1, c.add(f.scale(-20 * k)));
        placePart(tail2, c.add(f.scale(-34 * k)));
        placePart(tail3, c.add(f.scale(-48 * k)));
    }

    private static void placePart(BestiaPart p, Vec3 center) {
        p.xo = p.getX(); p.yo = p.getY(); p.zo = p.getZ();
        p.xOld = p.xo; p.yOld = p.yo; p.zOld = p.zo;
        p.setPos(center.x, center.y - p.getBbHeight() / 2, center.z);
    }

    @Override
    public AABB getBoundingBoxForCulling() { return getBoundingBox().inflate(14, 6, 14); }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) { return d < 256 * 256; }

    // ------------------------------------------------------------------ inmortal ----
    @Override
    public boolean isInvulnerableTo(DamageSource src) {
        return !(src.is(DamageTypes.FELL_OUT_OF_WORLD) || src.is(DamageTypes.GENERIC_KILL));
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        if (isInvulnerableTo(src)) {
            // un disparo o golpe solo lo enfurece: el atacante pasa a ser su objetivo si esta en el agua
            if (!level().isClientSide && src.getEntity() instanceof Player p && validTarget(p)) targetId = p.getUUID();
            return false;
        }
        return super.hurt(src, amount);
    }

    public boolean hurtFromPart(BestiaPart part, DamageSource src, float amount) { return hurt(src, amount); }

    @Override public boolean canBeAffected(MobEffectInstance e) { return false; }
    @Override public void knockback(double s, double x, double z) { }
    @Override public boolean isPushable() { return false; }
    @Override public boolean isPushedByFluid() { return false; }
    @Override public boolean canBreatheUnderwater() { return true; }
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean requiresCustomPersistence() { return true; }
    @Override protected boolean canRide(Entity e) { return false; }
    @Override public boolean causeFallDamage(float a, float b, DamageSource s) { return false; }
    @Override public boolean isPickable() { return true; }
    @Override public boolean shouldRiderSit() { return false; }
    @Override public int getAmbientSoundInterval() { return 200; }
    @Override protected SoundEvent getAmbientSound() { return BestiaLago.GROWL.get(); }
    @Override protected float getSoundVolume() { return 4.0F; }

    // ------------------------------------------------------------------ tick ----
    @Override
    public void tick() {
        super.tick();
        // animacion (los dos lados)
        phaseO = phase; swimO = swim; turnO = turn; jawO = jaw; shakeO = shake; liftO = lift;
        double dx = getX() - xo, dy = getY() - yo, dz = getZ() - zo;
        float spd = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        swim += (Mth.clamp(spd / 0.55F, 0F, 1F) - swim) * 0.1F;
        phase += 0.07F + 0.20F * swim;
        float dyaw = Mth.wrapDegrees(getYRot() - yRotO) * D2R;
        turn += (Mth.clamp(dyaw * 6F, -0.6F, 0.6F) - turn) * 0.12F;
        byte s = getState();
        float jt = entityData.get(JAW) / 100F;
        jaw += (jt - jaw) * (s == SWALLOW ? 0.45F : 0.2F);
        shake += ((s == GRAB ? 1F : 0F) - shake) * 0.15F;
        lift += ((s == ROAR || s == SWALLOW ? 1F : 0F) - lift) * 0.1F;
        yBodyRot = getYRot();
        yHeadRot = getYRot();
        placeParts();
    }

    /** pose interpolada para el renderer (pt = partial ticks) */
    public void animPose(float[] rot, float pt) {
        BestiaAnim.compute(rot, Mth.lerp(pt, phaseO, phase), tickCount + pt, Mth.lerp(pt, swimO, swim), Mth.lerp(pt, jawO, jaw),
                Mth.lerp(pt, shakeO, shake), Mth.lerp(pt, turnO, turn), Mth.lerp(pt, liftO, lift));
    }

    // ------------------------------------------------------------------ jugador en la boca ----
    private final float[] riderPose = new float[BestiaAnim.COUNT * 3];

    /** donde van los pies del jugador atrapado: sobre la lengua, a 3.5 bloques de la bisagra */
    public Vec3 mouthFloor() {
        animPose(riderPose, 1F);
        return position().add(BestiaAnim.jawPoint(riderPose, getYRot(), getXRot(), 0F, 0F, -16F));
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        if (!hasPassenger(passenger)) return;
        Vec3 p = mouthFloor();
        move.accept(passenger, p.x, p.y + 0.05, p.z);
        if (passenger instanceof Player pl) {
            pl.setYBodyRot(getYRot());   // mirando hacia fuera de la boca
        }
    }

    @Override
    public void onPassengerTurned(Entity e) {
        e.setYBodyRot(getYRot());
        float d = Mth.wrapDegrees(e.getYRot() - getYRot());
        float c = Mth.clamp(d, -100F, 100F);
        e.yRotO += c - d;
        e.setYRot(e.getYRot() + c - d);
        e.setYHeadRot(e.getYRot());
    }

    @Override
    protected boolean canAddPassenger(Entity e) { return getPassengers().isEmpty() && e instanceof Player; }

    @Override
    public net.minecraft.world.entity.LivingEntity getControllingPassenger() { return null; }

    /** el jugador solo puede salir de la boca cuando la bestia lo suelta (o si muere / se desconecta) */
    public boolean mayDismount(Entity e) { return releasing || !e.isAlive() || e.isRemoved() || isRemoved(); }

    public boolean isHolding(Player p) { return getState() == GRAB && hasPassenger(p); }

    // ------------------------------------------------------------------ IA (servidor) ----
    @Override
    public void travel(Vec3 in) {
        // todo el movimiento lo pone aiStep: sin gravedad, sin friccion
        if (isEffectiveAi()) move(MoverType.SELF, getDeltaMovement());
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel lv = (ServerLevel) level();
        if (home == null) home = blockPosition();
        if (Double.isNaN(surfaceY)) surfaceY = findSurface(home);
        stateTicks++;
        if (cooldown > 0) cooldown--;
        if (roarCd > 0) roarCd--;

        purgeStale();
        // el pasajero murio por otra causa (o ya no existe): soltarlo y limpiar, para no quedar con un agarre fantasma
        Entity pas = getFirstPassenger();
        if (pas instanceof Player pp && (!pp.isAlive() || pp.isRemoved()) && getState() != SWALLOW) forceRelease(pp);
        Player target = target(lv);
        byte s = getState();
        switch (s) {
            case PATROL -> patrol(target);
            case STALK -> stalk(target);
            case ROAR -> roar(target);
            case RAM -> ram(target);
            case LUNGE -> lunge(target);
            case GRAB -> grab(target);
            case SWALLOW -> swallow();
            default -> retreat(target);
        }
        steer();
        keepInLake();
    }

    private Player target(ServerLevel lv) {
        if (getState() == GRAB || getState() == SWALLOW) {
            Entity e = getFirstPassenger();
            return e instanceof Player p ? p : null;
        }
        Player cur = targetId == null ? null : lv.getPlayerByUUID(targetId);
        if (cur != null && validTarget(cur)) {
            lostTicks = 0;
            return cur;
        }
        if (cur != null && ++lostTicks < 80) return null;     // se fue a tierra: espera un poco por si vuelve
        targetId = null;
        if (tickCount % 10 == 0) {
            Player best = null;
            double bd = Double.MAX_VALUE;
            for (Player p : lv.players()) {
                if (!validTarget(p)) continue;
                double d = p.distanceToSqr(this);
                if (d < bd && d < 96 * 96) { bd = d; best = p; }
            }
            if (best != null) {
                targetId = best.getUUID();
                if (getState() == PATROL && roarCd == 0 && random.nextInt(3) == 0) setState(ROAR);
                else if (getState() == PATROL) setState(STALK);
                return best;
            }
        }
        return null;
    }

    private boolean validTarget(Player p) {
        if (p.isCreative() || p.isSpectator() || !p.isAlive()) return false;
        if (home != null && horiz(p.position(), Vec3.atCenterOf(home)) > radius + 12) return false;
        Entity v = p.getVehicle();
        if (v instanceof Boat b) return b.isInWater() || isWater(b.blockPosition()) || isWater(b.blockPosition().below());
        return p.isInWater();
    }

    private static double horiz(Vec3 a, Vec3 b) { return Math.sqrt((a.x - b.x) * (a.x - b.x) + (a.z - b.z) * (a.z - b.z)); }

    private boolean isWater(BlockPos p) { return level().getFluidState(p).is(FluidTags.WATER); }

    private boolean isWater(Vec3 v) { return isWater(BlockPos.containing(v)); }

    private double findSurface(BlockPos from) {
        BlockPos.MutableBlockPos p = from.mutable();
        for (int i = 0; i < 64 && isWater(p.above()); i++) p.move(0, 1, 0);
        return p.getY() + 0.9;
    }

    private Vec3 mouth() { return position().add(0, HEIGHT / 2, 0).add(Vec3.directionFromRotation(getXRot(), getYRot()).scale(30 * BestiaAnim.SCALE / 16F)); }

    // ---- estados ----
    private void patrol(Player t) {
        setJaw(0.05F);
        speed = 0.16;
        if (goal == null || --goalTicks <= 0 || position().add(0, HEIGHT / 2, 0).distanceTo(goal) < 4) pickPatrolGoal();
    }

    private void pickPatrolGoal() {
        for (int i = 0; i < 24; i++) {
            double a = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * radius;
            double x = home.getX() + 0.5 + Math.cos(a) * r, z = home.getZ() + 0.5 + Math.sin(a) * r;
            double y = surfaceY - 4.5 - random.nextDouble() * 4;
            Vec3 g = new Vec3(x, y, z);
            if (roomy(g)) { goal = g; goalTicks = 400; return; }
        }
        goal = Vec3.atCenterOf(home);
        goalTicks = 200;
    }

    /** cabe el cuerpo (centro y alrededores en agua) */
    private boolean roomy(Vec3 c) {
        return isWater(c) && isWater(c.add(2, 0, 0)) && isWater(c.add(-2, 0, 0)) && isWater(c.add(0, 0, 2)) && isWater(c.add(0, 0, -2))
                && isWater(c.add(0, -1.5, 0));
    }

    /** el punto de agua mas cercano por debajo de 'want' (para seguir a alguien que esta en la superficie) */
    private Vec3 underwater(Vec3 want, double depth) {
        Vec3 g = new Vec3(want.x, surfaceY - depth, want.z);
        for (int i = 0; i < 8 && !roomy(g); i++) g = g.add(0, 0.8, 0);
        return g;
    }

    private void stalk(Player t) {
        setJaw(0.1F);
        if (t == null) { setState(PATROL); goal = null; return; }
        Vec3 tp = t.position();
        Vec3 tv = (t.getVehicle() != null ? t.getVehicle() : t).getDeltaMovement();
        double d = horiz(mouth(), tp);
        // lo sigue por detras y por debajo, justo bajo la superficie (asoma el lomo)
        Vec3 behind = tp.subtract(tv.multiply(8, 0, 8));
        goal = underwater(behind, d > 30 ? 3.5 : 2.2);
        speed = d > 40 ? 0.62 : d > 20 ? 0.52 : 0.42;
        if (cooldown > 0) return;
        if (t.getVehicle() instanceof Boat && d < 16) { setState(RAM); ramPhase = 0; return; }
        if (!(t.getVehicle() instanceof Boat) && d < 13) {
            setState(LUNGE);
            playLoud(BestiaLago.ROAR.get(), 1.15F);
        }
    }

    private void roar(Player t) {
        // asoma la cabeza a la superficie, abre la boca y ruge
        setJaw(stateTicks > 8 ? 0.9F : 0.3F);
        speed = 0.1;
        Vec3 c = position();
        goal = new Vec3(c.x, surfaceY - 0.2, c.z).add(Vec3.directionFromRotation(0, getYRot()).scale(3));
        if (t != null) lookYaw(t.position());
        if (stateTicks == 10) {
            playLoud(BestiaLago.ROAR.get(), 0.9F);
            splash(mouth(), 40);
        }
        if (stateTicks > 60) {
            roarCd = 1200;
            setState(t != null ? STALK : PATROL);
        }
    }

    private void ram(Player t) {
        setJaw(0.6F);
        if (t == null || !(t.getVehicle() instanceof Boat boat)) { setState(t == null ? RETREAT : STALK); return; }
        Vec3 bp = boat.position();
        if (ramPhase == 0) {
            // ponerse debajo del bote, hondo
            goal = underwater(bp, 7);
            speed = 0.62;
            if (horiz(mouth(), bp) < 3.5 || stateTicks > 160) ramPhase = 1;
        } else {
            // subir en vertical hacia el bote
            goal = bp.add(0, 6, 0);
            speed = 0.95;
            setJaw(0.85F);
            if (mouth().distanceTo(bp) < 4.5 || getY() + HEIGHT > surfaceY + 1.5) {
                breakBoat(boat);
                cooldown = 50;
                setState(RETREAT);
            }
            if (stateTicks > 120) setState(STALK);
        }
    }

    private void breakBoat(Boat boat) {
        ServerLevel lv = (ServerLevel) level();
        Vec3 p = boat.position();
        List<Entity> riders = List.copyOf(boat.getPassengers());
        boat.ejectPassengers();
        for (Entity r : riders) {
            double a = random.nextDouble() * Math.PI * 2;
            r.setDeltaMovement(Math.cos(a) * 0.5, 1.1, Math.sin(a) * 0.5);
            r.hurtMarked = true;
            r.hurt(damageSources().mobAttack(this), 4F);
        }
        boat.spawnAtLocation(boat.getDropItem());
        boat.discard();
        playLoud(BestiaLago.SPLASH.get(), 1.0F);
        playLoud(BestiaLago.ROAR.get(), 1.05F);
        splash(p, 120);
        lv.sendParticles(ParticleTypes.CLOUD, p.x, p.y + 1, p.z, 30, 2, 1, 2, 0.05);
    }

    private void lunge(Player t) {
        setJaw(1F);
        if (t == null || t.getVehicle() instanceof Boat) { setState(t == null ? RETREAT : STALK); return; }
        Vec3 tp = t.position().add(0, 0.6, 0);
        goal = tp;
        speed = 0.82;
        if (mouth().distanceTo(tp) < 3.2) {
            startGrab(t);
            return;
        }
        if (stateTicks > 70) {           // fallo: se pasa de largo y vuelve
            cooldown = 40;
            setState(RETREAT);
        }
    }

    private void startGrab(Player t) {
        if (t.isPassenger() && !(t.getVehicle() instanceof Boat)) { setState(RETREAT); return; }
        // sin el mod de quick time en el jugador no hay agarre (se va y vuelve a intentarlo)
        if (!(t instanceof ServerPlayer sp) || !Qte.available(sp, QteDefs.BESTIA)) { cooldown = 60; setState(RETREAT); return; }
        if (!t.startRiding(this, true)) { setState(RETREAT); return; }
        presses = 0;
        releasing = false;
        setState(GRAB);
        BestiaLago.LOG.info("[Bestia] agarre iniciado sobre {}", t.getName().getString());
        playLoud(BestiaLago.BITE.get(), 1.0F);
        splash(mouth(), 60);
        ServerPlayerPatch pp = EpicFightCapabilities.getEntityPatch(sp, ServerPlayerPatch.class);
        if (pp != null && BestiaLago.JAW_HOLD != null) pp.playAnimationSynchronized(BestiaLago.JAW_HOLD, 0.1F);
        BestiaNet.CH.send(PacketDistributor.PLAYER.with(() -> sp), new BestiaNet.Hold(true));
        qteOn = Qte.start(sp, QteDefs.BESTIA, new Qte.Run() {
            @Override
            public void press(int phase, int presses, int required) { onQtePress(sp, phase, presses, required); }

            @Override
            public void phaseWon(int phase) { onQtePhase(sp, phase); }

            @Override
            public void end(boolean won, String reason) { onQteEnd(sp, won, reason); }
        });
        if (!qteOn) endGrab(t, true);
    }

    /** cada pulsacion valida: las mandibulas ceden poco a poco */
    private void onQtePress(ServerPlayer p, int phase, int n, int required) {
        if (!qteOn || !isHolding(p)) return;
        presses++;
        float k = Mth.clamp(n / (float) Math.max(1, required), 0F, 1F);
        setJaw(phase == 0 ? 0.58F + 0.12F * k : 0.70F - 0.10F * k);
        if (n % 4 == 0) {
            playLoud(BestiaLago.GROWL.get(), 0.9F + 0.3F * k);
            splash(mouth(), 12);
        }
    }

    /** una fase ganada y quedan mas: la bestia vuelve a apretar */
    private void onQtePhase(ServerPlayer p, int phase) {
        if (!qteOn || !isHolding(p)) return;
        playLoud(BestiaLago.ROAR.get(), 0.8F);
        splash(mouth(), 40);
        setJaw(0.9F - 0.2F * phase);
    }

    /** fin de la cadena: ganada = escapa; perdida = se lo traga */
    private void onQteEnd(ServerPlayer p, boolean won, String reason) {
        if (!qteOn || !isHolding(p) || getState() != GRAB) return;
        qteOn = false;
        if (won) {
            endGrab(p, true);
        } else {
            BestiaLago.LOG.info("[Bestia] el jugador perdio el quick time ({})", reason);
            setState(SWALLOW);
            setJaw(0F);
        }
    }

    private void grab(Player t) {
        if (t == null || !hasPassenger(t)) { endGrab(null, false); return; }
        // forcejea: se sacude cerca de la superficie con la cabeza fuera del agua
        speed = 0.12;
        if (goal == null || stateTicks % 40 == 0) {
            Vec3 f = Vec3.directionFromRotation(0, getYRot() + (random.nextFloat() - 0.5F) * 120F);
            goal = underwater(position().add(f.scale(8)), 0.9);
        }
        if (stateTicks % 30 == 0) playLoud(BestiaLago.GROWL.get(), 1.2F);
        if (t instanceof ServerPlayer sp) {
            t.setAirSupply(t.getMaxAirSupply());
            if (stateTicks % 12 == 2 && BestiaLago.JAW_HOLD != null) {
                ServerPlayerPatch pp = EpicFightCapabilities.getEntityPatch(sp, ServerPlayerPatch.class);
                if (pp != null) pp.playAnimationSynchronized(BestiaLago.JAW_HOLD, 0.05F);
            }
            // por si el cliente nunca responde (se colgo, perdio la conexion...): se da por perdido
            if (qteOn && stateTicks > 1500) { qteOn = false; Qte.cancel(sp); setState(SWALLOW); setJaw(0F); }
        }
    }

    /** quita de la lista de pasajeros a quien ya no va montado aqui (o ya no existe): evita agarres fantasma */
    private void purgeStale() {
        for (Entity e : List.copyOf(getPassengers())) if (e.isRemoved() || e.getVehicle() != this) removePassenger(e);
    }

    /** suelta al jugador y deja a la bestia en un estado limpio (muerte del jugador, desconexion...) */
    public void forceRelease(Player p) {
        if (p instanceof ServerPlayer sq) Qte.cancel(sq);
        qteOn = false;
        if (p instanceof ServerPlayer sp) BestiaNet.CH.send(PacketDistributor.PLAYER.with(() -> sp), new BestiaNet.Hold(false));
        releasing = true;
        p.stopRiding();
        releasing = false;
        if (p instanceof ServerPlayer sp) {
            ServerPlayerPatch pp = EpicFightCapabilities.getEntityPatch(sp, ServerPlayerPatch.class);
            if (pp != null && BestiaLago.JAW_HOLD != null) pp.stopPlaying(BestiaLago.JAW_HOLD);
        }
        targetId = null;
        cooldown = 120;
        setState(RETREAT);
    }

    private void swallow() {
        Entity e = getFirstPassenger();
        speed = 0.05;
        if (stateTicks == 20 && e instanceof Player p) {
            playLoud(BestiaLago.SWALLOW.get(), 1.0F);
            releasing = true;
            if (p instanceof ServerPlayer sp) BestiaNet.CH.send(PacketDistributor.PLAYER.with(() -> sp), new BestiaNet.Hold(false));
            p.hurt(new DamageSource(level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(BestiaLago.SWALLOWED), this), Float.MAX_VALUE);
            if (p.isAlive()) p.stopRiding();   // totem de la inmortalidad: lo escupe
        }
        if (stateTicks > 45) {
            if (e instanceof Player dp && !dp.isAlive()) forceRelease(dp);
            releasing = false;
            targetId = null;
            cooldown = 200;
            setState(RETREAT);
        }
    }

    private void endGrab(Player t, boolean escaped) {
        if (t instanceof ServerPlayer sq) Qte.cancel(sq);
        qteOn = false;
        BestiaLago.LOG.info("[Bestia] agarre terminado (escapo={}, pulsaciones recibidas={})", escaped, presses);
        if (t instanceof ServerPlayer sp) {
            // primero se avisa al cliente (los paquetes llegan en orden) y recien despues se lo baja
            BestiaNet.CH.send(PacketDistributor.PLAYER.with(() -> sp), new BestiaNet.Hold(false));
        }
        releasing = true;
        if (t != null) {
            t.stopRiding();
            if (t instanceof ServerPlayer sp) {
                ServerPlayerPatch pp = EpicFightCapabilities.getEntityPatch(sp, ServerPlayerPatch.class);
                if (pp != null && BestiaLago.JAW_HOLD != null) pp.stopPlaying(BestiaLago.JAW_HOLD);
            }
            if (escaped) {
                Vec3 f = Vec3.directionFromRotation(0, getYRot());
                t.setDeltaMovement(f.x * 0.9, 0.7, f.z * 0.9);
                t.hurtMarked = true;
                playLoud(BestiaLago.ROAR.get(), 1.3F);
            }
        }
        releasing = false;
        cooldown = escaped ? 160 : 40;
        setState(RETREAT);
    }

    private void retreat(Player t) {
        setJaw(0.05F);
        speed = 0.35;
        if (stateTicks == 1 || goal == null) {
            Vec3 away = t != null ? position().subtract(t.position()).multiply(1, 0, 1).normalize() : Vec3.directionFromRotation(0, getYRot());
            goal = underwater(position().add(away.scale(18)), 6);
        }
        if (stateTicks > 60 && cooldown == 0) setState(t != null ? STALK : PATROL);
    }

    // ---- movimiento ----
    private void lookYaw(Vec3 p) {
        float want = (float) (Mth.atan2(p.z - getZ(), p.x - getX()) * R2D) - 90F;
        setYRot(Mth.approachDegrees(getYRot(), want, 4F));
    }

    private void steer() {
        if (goal == null) goal = position().add(0, HEIGHT / 2, 0);
        Vec3 c = position().add(0, HEIGHT / 2, 0);
        Vec3 to = goal.subtract(c);
        double h = Math.sqrt(to.x * to.x + to.z * to.z);
        byte s = getState();
        float turnRate = s == LUNGE ? 7F : s == RAM ? 6F : s == STALK ? 4.5F : 2.6F;
        if (h > 0.5) {
            float wantYaw = (float) (Mth.atan2(to.z, to.x) * R2D) - 90F + turnBias;
            setYRot(Mth.approachDegrees(getYRot(), wantYaw, turnRate));
        }
        float wantPitch = (float) Mth.clamp(-Mth.atan2(to.y, Math.max(h, 1.0)) * R2D, -55, 45);
        if (s == RAM && ramPhase == 1) wantPitch = -60F;
        if (s == GRAB || s == SWALLOW) wantPitch = -12F;     // cabeza arriba, fuera del agua
        setXRot(Mth.approach(getXRot(), wantPitch, 2.5F));
        // velocidad: acelera/frena suave (es enorme)
        double cur = getDeltaMovement().length();
        double want = to.length() < 2 ? speed * 0.3 : speed;
        double next = cur + Mth.clamp(want - cur, -0.03, s == LUNGE || s == RAM ? 0.06 : 0.025);
        Vec3 dir = Vec3.directionFromRotation(getXRot(), getYRot());
        // la parte vertical va directa al objetivo (para no depender solo del cabeceo)
        Vec3 v = dir.scale(next);
        double vy = Mth.clamp(to.y * 0.08, -0.25, s == RAM ? 0.9 : 0.25);
        setDeltaMovement(v.x, (v.y + vy) * 0.5, v.z);
        if (horizontalCollision) turnBias = Mth.clamp(turnBias + 6F, 0, 90);
        else turnBias *= 0.95F;
    }

    /** no salir del lago: si el siguiente paso de la cabeza no es agua, frenar y girar; nunca subir de la superficie salvo al embestir/rugir */
    private void keepInLake() {
        Vec3 v = getDeltaMovement();
        byte s = getState();
        double maxTop = s == RAM || s == ROAR || s == GRAB || s == SWALLOW ? surfaceY + 2.2 : surfaceY - 0.6;
        if (getY() + HEIGHT + v.y > maxTop && v.y > 0) v = new Vec3(v.x, Math.max(0, maxTop - getY() - HEIGHT), v.z);
        if (s != RAM && getY() + HEIGHT > maxTop + 0.3) v = new Vec3(v.x, -0.08, v.z);     // vuelve a hundirse
        Vec3 ahead = mouth().add(v.multiply(6, 0, 6));
        if (!isWater(ahead) && !isWater(ahead.add(0, -1.5, 0)) && s != RAM) {
            v = new Vec3(v.x * 0.3, v.y, v.z * 0.3);
            turnBias = Mth.clamp(turnBias + 8F, 0, 120);
            if (s == PATROL) goalTicks = 0;
        }
        if (home != null && horiz(position(), Vec3.atCenterOf(home)) > radius + 8) {
            Vec3 back = Vec3.atCenterOf(home).subtract(position()).multiply(1, 0, 1).normalize().scale(0.2);
            v = v.add(back);
        }
        if (!isInWater() && s != RAM && s != ROAR) v = v.add(0, -0.06, 0);     // fuera del agua: cae
        setDeltaMovement(v);
    }

    // ---- efectos ----
    private void playLoud(SoundEvent ev, float pitch) {
        level().playSound(null, getX(), getY(), getZ(), ev, SoundSource.HOSTILE, 6.0F, pitch);
    }

    private void splash(Vec3 p, int n) {
        ServerLevel lv = (ServerLevel) level();
        lv.sendParticles(ParticleTypes.SPLASH, p.x, surfaceY, p.z, n, 2.5, 0.4, 2.5, 0.4);
        lv.sendParticles(ParticleTypes.BUBBLE, p.x, p.y, p.z, n / 2, 2, 1, 2, 0.1);
        lv.sendParticles(ParticleTypes.FISHING, p.x, surfaceY, p.z, n / 2, 3, 0.1, 3, 0.05);
    }

    // ------------------------------------------------------------------ comandos ----
    public void setRadius(int r) { this.radius = Math.max(8, r); }
    public int getRadius() { return radius; }
    public void setHomeHere() { this.home = blockPosition(); this.surfaceY = findSurface(home); }
    public BlockPos getHome() { return home; }

    // ------------------------------------------------------------------ guardado ----
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (home != null) {
            tag.putInt("HomeX", home.getX());
            tag.putInt("HomeY", home.getY());
            tag.putInt("HomeZ", home.getZ());
        }
        tag.putInt("Radius", radius);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("HomeX")) home = new BlockPos(tag.getInt("HomeX"), tag.getInt("HomeY"), tag.getInt("HomeZ"));
        if (tag.contains("Radius")) radius = tag.getInt("Radius");
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        for (BestiaPart p : parts) p.remove(reason);
    }
}
