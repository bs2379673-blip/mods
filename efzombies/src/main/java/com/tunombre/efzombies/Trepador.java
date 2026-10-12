package com.tunombre.efzombies;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * El TREPADOR: un zombi deforme que gatea a cuatro patas, trepa CUALQUIER pared, se cuelga del techo y se deja caer
 * sobre el jugador. Estados (sincronizados al cliente):
 * <ul>
 * <li>SUELO: IA normal de zombi (navegacion de arana: si no hay camino va recto contra la pared).</li>
 * <li>PARED: pegado a la cara de un bloque. Mira SIEMPRE hacia la pared (el cuerpo no puede girar y quedar en el aire).
 * Sube, baja o se desplaza de lado hacia el objetivo. Arriba del todo pasa por encima del borde o se mete al techo.</li>
 * <li>TECHO: boca arriba bajo el techo. Gatea hacia el objetivo mientras haya techo; cuando lo tiene debajo, cae encima.</li>
 * </ul>
 * Sin objetivo, a veces sube a una pared o al techo y espera ahi quieto (emboscada).
 * Si recibe un golpe trepado, se cae. No recibe dano por caida.
 * Todo el movimiento trepado lo hace el servidor; el cliente solo interpola y elige la animacion (ver TrepadorPatch).
 */
public class Trepador extends Zombie {
    public static final float WIDTH = 0.8F, HEIGHT = 0.9F;
    public static final byte GROUND = 0, WALL = 1, CEILING = 2;

    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(Trepador.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> FACE = SynchedEntityData.defineId(Trepador.class, EntityDataSerializers.BYTE);

    private static final double CLIMB_SPEED = 0.11;   // bloques por tick trepando (la animacion de pared esta hecha para esto)
    private static final double SIDE_SPEED = 0.06;
    private static final double CEIL_SPEED = 0.10;

    private int movedTicks;          // >0 = se ha movido hace poco (para elegir animacion de moverse o quieto)
    private int holdTicks;           // tiempo esperando trepado sin objetivo
    private int regrip;              // no volver a agarrarse justo despues de soltarse
    private double ambushBaseY = Double.NaN;
    private double ambushHeight = 2.5;
    private boolean wantCeiling;
    private boolean pouncing;
    private int wanderTicks;
    private float wanderYaw;

    public Trepador(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
        this.xpReward = 8;
        this.setMaxUpStep(1.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 28.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.ATTACK_DAMAGE, 4.5D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .add(Attributes.ARMOR, 3.0D);
    }

    // ------------------------------------------------------------------ estado sincronizado ----
    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(STATE, GROUND);
        this.entityData.define(FACE, (byte) Direction.NORTH.get2DDataValue());
    }

    public byte getClimbState() { return this.entityData.get(STATE); }

    public Direction getWallFace() { return Direction.from2DDataValue(this.entityData.get(FACE)); }

    /** se ha movido en los ultimos ticks (cliente y servidor) */
    public boolean isClimbMoving() { return this.movedTicks > 0; }

    private void setClimb(byte s, Direction face) {
        this.entityData.set(STATE, s);
        if (face != null) this.entityData.set(FACE, (byte) face.get2DDataValue());
        this.holdTicks = 0;
        this.fallDistance = 0;
        if (s != GROUND) this.getNavigation().stop();
    }

    // ------------------------------------------------------------------ cosas de zombi que no aplican ----
    @Override public void setBaby(boolean baby) { }
    @Override public boolean isBaby() { return false; }
    @Override protected boolean convertsInWater() { return false; }
    @Override protected void populateDefaultEquipmentSlots(RandomSource r, DifficultyInstance d) { }
    @Override public boolean canPickUpLoot() { return false; }
    @Override protected float getStandingEyeHeight(Pose pose, EntityDimensions dim) { return 0.62F; }
    @Override public boolean causeFallDamage(float dist, float mult, DamageSource src) { return false; }
    @Override public boolean onClimbable() { return false; }
    @Override public boolean isPushable() { return getClimbState() == GROUND && super.isPushable(); }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WallClimberNavigation(this, level);   // si no hay camino, va recto (y trepa)
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(6, new AmbushGoal(this));
    }

    // ------------------------------------------------------------------ sonidos: voz grave y chasquidos ----
    @Override public float getVoicePitch() { return 0.5F + this.random.nextFloat() * 0.2F; }
    @Override protected SoundEvent getAmbientSound() {
        return getClimbState() != GROUND && getTarget() == null && this.random.nextInt(3) != 0 ? null : SoundEvents.ZOMBIE_AMBIENT;
    }
    @Override protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.SPIDER_STEP, 0.25F, 0.6F);
    }

    // ------------------------------------------------------------------ tick ----
    @Override
    public void tick() {
        super.tick();
        double dx = getX() - xo, dy = getY() - yo, dz = getZ() - zo;
        if (dx * dx + dy * dy + dz * dz > 0.0004) movedTicks = 4;
        else if (movedTicks > 0) movedTicks--;
        lockRotation();
    }

    /** trepado, el cuerpo no gira: en la pared mira a la pared, en el techo mira hacia donde gatea. */
    private void lockRotation() {
        byte s = getClimbState();
        if (s == GROUND) return;
        float yaw = s == WALL ? getWallFace().toYRot() : getYRot();
        setYRot(yaw);
        yRotO = yaw;
        yBodyRot = yaw;
        yBodyRotO = yaw;
        yHeadRot = yaw;
        yHeadRotO = yaw;
        setXRot(0);
        xRotO = 0;
    }

    @Override
    public void travel(Vec3 input) {
        if (getClimbState() == GROUND || !isEffectiveAi()) {
            super.travel(input);
            return;
        }
        // trepado: sin gravedad ni friccion, se mueve exactamente lo que diga climbTick
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.fallDistance = 0;
        this.calculateEntityAnimation(false);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (regrip > 0) regrip--;
        switch (getClimbState()) {
            case WALL -> wallTick();
            case CEILING -> ceilingTick();
            default -> groundTick();
        }
    }

    // ------------------------------------------------------------------ helpers de colision ----
    private boolean touches(AABB box) { return !level().noCollision(this, box); }

    private AABB box() { return getBoundingBox(); }

    private boolean wallAt(Direction d, AABB b) {
        return touches(b.move(d.getStepX() * 0.08, 0, d.getStepZ() * 0.08).deflate(0.02, 0.0, 0.02).move(0, 0.01, 0));
    }

    private boolean ceilingAbove(AABB b) { return touches(b.move(0, 0.12, 0).deflate(0.05, 0, 0.05)); }

    private boolean groundBelow(AABB b) { return touches(b.move(0, -0.12, 0).deflate(0.05, 0, 0.05)); }

    /** la pared mide al menos 2 bloques (si no, el zombi solo salta). */
    private boolean tallWall(Direction d) {
        AABB up = box().move(0, 1.05, 0);
        return wallAt(d, up) || ceilingAbove(box());
    }

    /** direccion hacia donde quiere ir (objetivo, navegacion o mirada). */
    private Vec3 wish() {
        LivingEntity t = getTarget();
        if (t != null) return new Vec3(t.getX() - getX(), 0, t.getZ() - getZ());
        if (getNavigation().getPath() != null && !getNavigation().isDone()) {
            Vec3 n = getNavigation().getPath().getNextEntityPos(this);
            return new Vec3(n.x - getX(), 0, n.z - getZ());
        }
        return Vec3.directionFromRotation(0, getYRot());
    }

    private Direction bestWall() {
        Vec3 w = wish();
        Direction best = null;
        double bd = -2;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (!wallAt(d, box())) continue;
            double dot = w.lengthSqr() < 1e-6 ? 0 : (d.getStepX() * w.x + d.getStepZ() * w.z) / w.length();
            if (dot > bd) { bd = dot; best = d; }
        }
        return best;
    }

    // ------------------------------------------------------------------ SUELO ----
    private void groundTick() {
        if (pouncing && onGround()) {           // aterrizo encima: zarpazo
            pouncing = false;
            LivingEntity t = getTarget();
            if (t != null && distanceToSqr(t) < 4.0) doHurtTarget(t);
        }
        if (regrip > 0 || !onGroundOrClose()) return;
        boolean wants = getTarget() != null || (getNavigation().getPath() != null && !getNavigation().isDone());
        if (horizontalCollision && wants) {
            Direction d = bestWall();
            if (d != null && tallWall(d) && towards(d) > 0.2) startWall(d, false);
        }
    }

    private boolean onGroundOrClose() { return onGround() || groundBelow(box()); }

    private double towards(Direction d) {
        Vec3 w = wish();
        if (w.lengthSqr() < 1e-6) return 1;
        return (d.getStepX() * w.x + d.getStepZ() * w.z) / w.length();
    }

    void startWall(Direction d, boolean ambush) {
        setClimb(WALL, d);
        ambushBaseY = ambush ? getY() : Double.NaN;
        ambushHeight = 1.5 + random.nextDouble() * 2.5;
        wantCeiling = ambush && random.nextInt(10) < 6;
        this.playSound(SoundEvents.SPIDER_AMBIENT, 0.5F, 0.55F);
    }

    private void release(Vec3 push) {
        setClimb(GROUND, null);
        regrip = 50;
        setDeltaMovement(push);
        hasImpulse = true;
    }

    // ------------------------------------------------------------------ PARED ----
    private void wallTick() {
        Direction d = getWallFace();
        Vec3 n = new Vec3(d.getStepX(), 0, d.getStepZ());         // hacia la pared
        Vec3 side = new Vec3(-d.getStepZ(), 0, d.getStepX());     // a lo largo de la pared
        AABB b = box();
        boolean lower = wallAt(d, b.setMaxY(b.minY + HEIGHT * 0.5));
        boolean upper = wallAt(d, b.setMinY(b.minY + HEIGHT * 0.5));

        if (!lower && !upper) {                                   // la pared desaparecio
            release(n.scale(-0.05));
            return;
        }
        double vy = 0, vs = 0;
        LivingEntity t = getTarget();
        if (t != null) {
            holdTicks = 0;
            double dy = t.getY() - getY();
            Vec3 to = new Vec3(t.getX() - getX(), 0, t.getZ() - getZ());
            double hd = to.horizontalDistance();
            double beyond = hd < 1e-4 ? 0 : (to.x * n.x + to.z * n.z) / hd;   // >0: el objetivo esta del otro lado de la pared
            if (hd < 3.0 && Math.abs(dy) < 3.0 && beyond < 0.3) {          // cerca: se lanza encima
                pounceAt(t, n.scale(-1));
                return;
            }
            if (dy < -1.5 && hd < 7 && beyond < 0.3) {                      // esta debajo: se deja caer
                pounceAt(t, n.scale(-1));
                return;
            }
            if (dy > 0.4 || beyond > 0.3) vy = CLIMB_SPEED;                 // arriba o detras de la pared: subir
            else if (getY() - groundY() > 0.6) vy = -CLIMB_SPEED * 0.8;     // bajar para perseguir
            else { release(n.scale(-0.1)); return; }
            double lat = to.x * side.x + to.z * side.z;
            if (Math.abs(lat) > 0.6) vs = Math.signum(lat) * SIDE_SPEED;
        } else {
            // emboscada: subir hasta la altura elegida y esperar
            if (Double.isNaN(ambushBaseY)) ambushBaseY = groundY();
            holdTicks++;
            if (getY() - ambushBaseY < ambushHeight && holdTicks < 400) vy = CLIMB_SPEED * 0.8;
            if (holdTicks > 1500 + random.nextInt(600)) {                   // se aburre: baja
                vy = -CLIMB_SPEED * 0.7;
                if (groundBelow(b)) { setClimb(GROUND, null); regrip = 200; return; }
            }
        }
        // arriba del todo: pasar por encima del borde
        if (vy > 0 && !upper && tryLedge(d)) return;
        // techo encima: meterse al techo (si se puede) o quedarse
        if (vy > 0 && ceilingAbove(b)) {
            vy = 0;
            boolean goCeil = (t == null && wantCeiling) || (t != null && t.getY() < getY());
            if (goCeil && tryCeiling(d)) return;
        }
        if (vy < 0 && groundBelow(b)) {                                    // ya abajo
            setClimb(GROUND, null);
            regrip = 30;
            return;
        }
        if (vs != 0 && !wallAt(d, b.move(side.x * vs * 6, 0, side.z * vs * 6))) vs = 0;   // no salirse de la pared de lado
        Vec3 v = n.scale(0.03).add(side.scale(vs)).add(0, vy, 0);
        setDeltaMovement(v);
        if (vy != 0 || vs != 0) clicks();
    }

    private double groundY() {
        BlockPos.MutableBlockPos p = blockPosition().mutable();
        for (int i = 0; i < 16; i++) {
            p.move(Direction.DOWN);
            if (!level().getBlockState(p).getCollisionShape(level(), p).isEmpty()) return p.getY() + 1.0;
        }
        return getY() - 16;
    }

    private boolean tryLedge(Direction d) {
        for (double up : new double[]{0.3, 0.6, 0.95}) {
            AABB c = box().move(d.getStepX() * 0.8, up, d.getStepZ() * 0.8);
            if (!touches(c) && touches(c.move(0, -0.35, 0))) {
                setPos(getX() + d.getStepX() * 0.8, getY() + up, getZ() + d.getStepZ() * 0.8);
                setClimb(GROUND, null);
                regrip = 20;
                setDeltaMovement(d.getStepX() * 0.12, 0.05, d.getStepZ() * 0.12);
                return true;
            }
        }
        return false;
    }

    private boolean tryCeiling(Direction d) {
        AABB c = box().move(-d.getStepX() * 0.55, 0, -d.getStepZ() * 0.55);
        if (touches(c) || !ceilingAbove(c)) return false;
        setPos(getX() - d.getStepX() * 0.55, getY(), getZ() - d.getStepZ() * 0.55);
        setYRot(d.getOpposite().toYRot());
        setClimb(CEILING, d);
        return true;
    }

    private void pounceAt(LivingEntity t, Vec3 away) {
        Vec3 to = new Vec3(t.getX() - getX(), 0, t.getZ() - getZ());
        double hd = Math.max(0.5, to.horizontalDistance());
        Vec3 v = to.scale(Math.min(0.55, 0.12 * hd) / hd).add(away.scale(0.12)).add(0, getClimbState() == CEILING ? -0.2 : 0.32, 0);
        release(v);
        regrip = 60;
        pouncing = true;
        setYRot((float) Math.toDegrees(Mth.atan2(to.z, to.x)) - 90F);
        this.playSound(SoundEvents.ZOMBIE_AMBIENT, 1.4F, 0.45F);
    }

    private void clicks() {
        if (tickCount % 6 == 0) this.playSound(SoundEvents.SPIDER_STEP, 0.3F, 0.55F + random.nextFloat() * 0.2F);
    }

    // ------------------------------------------------------------------ TECHO ----
    private void ceilingTick() {
        AABB b = box();
        if (!ceilingAbove(b)) {                       // sin techo: cae
            release(Vec3.ZERO);
            return;
        }
        LivingEntity t = getTarget();
        Vec3 dir = Vec3.ZERO;
        if (t != null) {
            holdTicks = 0;
            Vec3 to = new Vec3(t.getX() - getX(), 0, t.getZ() - getZ());
            double hd = to.horizontalDistance();
            double dy = getY() - t.getY();
            if (hd < 1.3 && dy > 0.5 && dy < 12) {       // justo encima: se deja caer sobre el
                pounceAt(t, Vec3.ZERO);
                return;
            }
            if (hd > 1e-3) dir = to.scale(1 / hd);
            if (!ceilingAhead(dir)) {
                dir = Vec3.ZERO;
                if (hd < 5 && dy < 6) { pounceAt(t, Vec3.ZERO); return; }   // no puede llegar por el techo: salta igual
            }
        } else {
            holdTicks++;
            if (wanderTicks > 0) {
                wanderTicks--;
                dir = Vec3.directionFromRotation(0, wanderYaw);
                if (!ceilingAhead(dir)) { dir = Vec3.ZERO; wanderTicks = 0; }
            } else if (random.nextInt(260) == 0) {
                wanderTicks = 15 + random.nextInt(30);
                wanderYaw = random.nextFloat() * 360F;
            }
            if (holdTicks > 4000 + random.nextInt(1200)) { release(Vec3.ZERO); return; }
        }
        Vec3 v = dir.scale(CEIL_SPEED).add(0, 0.04, 0);       // apretado contra el techo
        setDeltaMovement(v);
        if (dir.lengthSqr() > 0) {
            float yaw = (float) Math.toDegrees(Mth.atan2(dir.z, dir.x)) - 90F;
            setYRot(Mth.approachDegrees(getYRot(), yaw, 12F));
            clicks();
        }
    }

    private boolean ceilingAhead(Vec3 dir) {
        if (dir.lengthSqr() < 1e-6) return true;
        AABB c = box().move(dir.x * 0.6, 0, dir.z * 0.6);
        return !touches(c.deflate(0.02, 0.0, 0.02).move(0, -0.01, 0)) && ceilingAbove(c);
    }

    // ------------------------------------------------------------------ golpes ----
    @Override
    public boolean hurt(DamageSource src, float amount) {
        boolean r = super.hurt(src, amount);
        if (r && !level().isClientSide && getClimbState() != GROUND) {
            Vec3 away = getClimbState() == WALL ? new Vec3(-getWallFace().getStepX(), 0, -getWallFace().getStepZ()).scale(0.15) : Vec3.ZERO;
            release(away);      // trepado, un golpe lo tira (asi Epic Fight nunca anima un aturdimiento colgado en el aire)
            regrip = 80;
        }
        return r;
    }

    // ------------------------------------------------------------------ guardado ----
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("ClimbState", getClimbState());
        tag.putByte("ClimbFace", this.entityData.get(FACE));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(FACE, tag.getByte("ClimbFace"));
        this.entityData.set(STATE, tag.getByte("ClimbState"));
    }

    // ------------------------------------------------------------------ emboscada ----
    /** sin objetivo, de vez en cuando busca una pared alta cerca, va hasta ella y sube a esperar. */
    static class AmbushGoal extends Goal {
        private final Trepador mob;
        private BlockPos spot;
        private Direction face;
        private int time;

        AmbushGoal(Trepador mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (mob.getClimbState() != GROUND || mob.getTarget() != null || mob.regrip > 0) return false;
            if (mob.getRandom().nextInt(240) != 0) return false;
            return find();
        }

        private boolean find() {
            BlockPos base = mob.blockPosition();
            RandomSource r = mob.getRandom();
            Level lv = mob.level();
            for (int i = 0; i < 24; i++) {
                BlockPos p = base.offset(r.nextInt(17) - 8, r.nextInt(5) - 2, r.nextInt(17) - 8);
                if (!lv.getBlockState(p).getCollisionShape(lv, p).isEmpty()) continue;
                if (lv.getBlockState(p.below()).getCollisionShape(lv, p.below()).isEmpty()) continue;
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockPos w = p.relative(d);
                    if (solid(lv, w) && solid(lv, w.above()) && solid(lv, w.above(2)) && !solid(lv, p.above())) {
                        spot = p;
                        face = d;
                        return true;
                    }
                }
            }
            return false;
        }

        private static boolean solid(Level lv, BlockPos p) {
            return !lv.getBlockState(p).getCollisionShape(lv, p).isEmpty();
        }

        @Override
        public void start() {
            time = 0;
            mob.getNavigation().moveTo(spot.getX() + 0.5 + face.getStepX() * 0.1, spot.getY(), spot.getZ() + 0.5 + face.getStepZ() * 0.1, 1.0);
        }

        @Override
        public boolean canContinueToUse() {
            return mob.getClimbState() == GROUND && mob.getTarget() == null && time < 200 && spot != null;
        }

        @Override
        public void tick() {
            time++;
            double dx = spot.getX() + 0.5 - mob.getX(), dz = spot.getZ() + 0.5 - mob.getZ();
            if (dx * dx + dz * dz < 0.5 && mob.wallAt(face, mob.box().inflate(0.15, 0, 0.15))) {
                // pegarse a la pared y empezar a subir
                mob.getNavigation().stop();
                mob.setYRot(face.toYRot());
                double ox = face.getStepX() * 0.3, oz = face.getStepZ() * 0.3;
                AABB c = mob.box().move(ox, 0, oz);
                if (!mob.touches(c)) mob.setPos(mob.getX() + ox, mob.getY(), mob.getZ() + oz);
                mob.startWall(face, true);
                spot = null;
            } else if (mob.getNavigation().isDone()) {
                spot = null;
            }
        }
    }
}
