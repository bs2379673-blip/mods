package com.tunombre.infectados;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.LivingMotion;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;

import java.util.EnumSet;
import java.util.Map;

/**
 * Base de todos los infectados: animaciones de Epic Fight propias (sin la IA de combate de Epic Fight), un sistema de
 * golpes con su animacion y su instante de impacto, y el AGARRE con quick time event (el jugador queda montado delante
 * del mob, sin poder soltarse con shift, y spamea la tecla de saltar).
 */
public abstract class InfMob extends Monster {
    /** un golpe: animacion, tick del impacto, duracion, alcance, dano, angulo del arco (grados, 360 = alrededor) */
    public record Move(AnimationManager.AnimationAccessor<StaticAnimation> anim, int hit, int length, double reach, float damage, float arc) { }

    protected Move move;
    protected int moveTick;
    protected LivingEntity moveTarget;
    /** impulso hacia el objetivo justo antes del impacto (0 = sin envion) */
    protected double lunge;
    protected int attackCd;

    // ---- agarre ----
    protected float grip;
    protected int grabTicks;
    protected boolean releasing;
    protected int qteKind;
    private AnimationManager.AnimationAccessor<StaticAnimation> grabPlayerAnim;

    protected InfMob(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    // ------------------------------------------------------------------ Epic Fight ----
    /** animaciones de movimiento: IDLE/WALK/CHASE (+ las que quiera cada mob) */
    public abstract Map<LivingMotion, AnimationManager.AnimationAccessor<? extends StaticAnimation>> motions();

    /** motion forzado (por ejemplo la Witch llorando) o null para el normal */
    public LivingMotion forcedMotion() { return null; }

    public LivingEntityPatch<?> patch() { return EpicFightCapabilities.getEntityPatch(this, LivingEntityPatch.class); }

    public void play(AnimationManager.AnimationAccessor<? extends StaticAnimation> a) {
        LivingEntityPatch<?> p = patch();
        if (p != null && a != null && !level().isClientSide) p.playAnimationSynchronized(a, 0F);
    }

    public void stop(AnimationManager.AnimationAccessor<? extends StaticAnimation> a) {
        LivingEntityPatch<?> p = patch();
        if (p != null && a != null && !level().isClientSide) p.stopPlaying(a);
    }

    // ------------------------------------------------------------------ golpes ----
    public boolean busy() { return move != null || isGrabbing(); }

    protected void startMove(Move m, LivingEntity target) {
        move = m;
        moveTick = 0;
        lunge = 0;
        moveTarget = target;
        getNavigation().stop();
        play(m.anim());
    }

    /** el mob elige que hacer cuando tiene al objetivo a tiro */
    protected abstract void chooseAttack(LivingEntity target, double dist);

    /** distancia a la que empieza a atacar */
    protected double attackRange() { return 2.6; }

    protected void onMoveHit(Move m) {
        Vec3 f = Vec3.directionFromRotation(0, getYRot());
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(m.reach()), x -> x != this && x.isAlive() && !(x instanceof InfMob))) {
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            Vec3 to = e.position().subtract(position());
            double d = to.horizontalDistance();
            if (d > m.reach() + e.getBbWidth() / 2) continue;
            if (m.arc() < 360) {
                double ang = Math.toDegrees(Math.acos(Mth.clamp((to.x * f.x + to.z * f.z) / Math.max(d, 1e-4), -1, 1)));
                if (ang > m.arc() / 2) continue;
            }
            e.hurt(damageSources().mobAttack(this), m.damage());
            e.knockback(0.4, -f.x, -f.z);
            onHitTarget(m, e);
        }
    }

    protected void onHitTarget(Move m, LivingEntity e) { }

    private void tickMove() {
        if (move == null) return;
        moveTick++;
        getNavigation().stop();
        if (moveTarget != null && moveTick < move.hit()) getLookControl().setLookAt(moveTarget, 30, 30);
        if (moveTarget != null && moveTick < move.hit()) {
            float want = (float) Math.toDegrees(Mth.atan2(moveTarget.getZ() - getZ(), moveTarget.getX() - getX())) - 90F;
            setYRot(Mth.approachDegrees(getYRot(), want, 12F));
            yBodyRot = getYRot();
        }
        if (lunge > 0 && moveTarget != null && moveTick == Math.max(1, move.hit() - 5)) {
            Vec3 d = moveTarget.position().subtract(position()).multiply(1, 0, 1);
            if (d.lengthSqr() > 1e-4) {
                d = d.normalize();
                setDeltaMovement(d.x * lunge, 0.08, d.z * lunge);
                hurtMarked = true;
            }
        }
        if (moveTick == move.hit()) onMoveHit(move);
        if (move != null && moveTick >= move.length()) move = null;
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        boolean r = super.hurt(src, amount);
        if (r && move != null && moveTick < move.hit() && amount >= 5F && !isBoss()) {
            stop(move.anim());       // un buen golpe interrumpe el ataque
            move = null;
        }
        return r;
    }

    protected boolean isBoss() { return false; }

    // ------------------------------------------------------------------ agarre con QTE ----
    public boolean isGrabbing() { return !getPassengers().isEmpty() && getFirstPassenger() instanceof Player; }

    public boolean mayDismount(Entity e) { return releasing || !e.isAlive() || e.isRemoved() || isRemoved() || !isAlive(); }

    /** kind: 0 aldeano, 1 motosierra */
    protected boolean startGrab(Player p, int kind, AnimationManager.AnimationAccessor<StaticAnimation> myAnim, AnimationManager.AnimationAccessor<StaticAnimation> playerAnim) {
        if (Grabs.recentlyGrabbed(p) || p.isPassenger() || !(p instanceof ServerPlayer sp)) return false;
        if (!p.startRiding(this, true)) return false;
        Grabs.mark(p);
        grip = 50F;
        grabTicks = 0;
        releasing = false;
        qteKind = kind;
        grabPlayerAnim = playerAnim;
        getNavigation().stop();
        play(myAnim);
        ServerPlayerPatch pp = EpicFightCapabilities.getEntityPatch(sp, ServerPlayerPatch.class);
        if (pp != null && playerAnim != null) pp.playAnimationSynchronized(playerAnim, 0.1F);
        return true;
    }

    public void struggle(ServerPlayer p) {
        if (!hasPassenger(p)) return;
        grip = Math.min(100F, grip + Infectados.cfgPress(qteKind));
        if (grip >= 99.5F && grabTicks > 0) endGrab(p, true);
    }

    /** llamado cada tick mientras agarra; devuelve true si sigue */
    protected boolean tickGrab() {
        Entity e = getFirstPassenger();
        if (!(e instanceof Player p)) return false;
        grabTicks++;
        // Epic Fight pisa la animacion del jugador cuando pasa a 'montado' (pose sentada): se vuelve a poner cuando ya esta montado
        if ((grabTicks == 2 || grabTicks == 5) && grabPlayerAnim != null && p instanceof ServerPlayer sp0) {
            ServerPlayerPatch pp0 = EpicFightCapabilities.getEntityPatch(sp0, ServerPlayerPatch.class);
            if (pp0 != null) pp0.playAnimationSynchronized(grabPlayerAnim, 0.05F);
        }
        if (grip >= 99.5F) { endGrab(p, true); return false; }   // gano (se mira antes de restar la caida)
        grip -= Infectados.cfgDecay(qteKind);
        getNavigation().stop();
        getLookControl().setLookAt(p, 30, 30);
        if (p instanceof ServerPlayer sp) InfNet.CH.send(PacketDistributor.PLAYER.with(() -> sp), new InfNet.Qte(Math.round(grip), true, qteKind));
        if (grip <= 0F) { failGrab(p); return false; }
        return true;
    }

    protected abstract void failGrab(Player p);

    protected void endGrab(Player p, boolean escaped) {
        if (p instanceof ServerPlayer sp) InfNet.CH.send(PacketDistributor.PLAYER.with(() -> sp), new InfNet.Qte(escaped ? 100 : 0, false, qteKind));   // antes de bajarlo
        releasing = true;
        p.stopRiding();
        releasing = false;
        if (p instanceof ServerPlayer sp) {
            ServerPlayerPatch pp = EpicFightCapabilities.getEntityPatch(sp, ServerPlayerPatch.class);
            if (pp != null) {
                pp.stopPlaying(Anims.P_GRABBED);
                pp.stopPlaying(Anims.P_CHAINSAW);
            }
        }
        Vec3 f = Vec3.directionFromRotation(0, getYRot());
        if (escaped) {
            // el jugador lo empuja: retrocede
            setDeltaMovement(-f.x * 0.6, 0.2, -f.z * 0.6);
            p.setDeltaMovement(f.x * 0.4, 0.2, f.z * 0.4);
        } else {
            p.setDeltaMovement(f.x * 0.8, 0.3, f.z * 0.8);
        }
        p.hurtMarked = true;
        hurtMarked = true;
        attackCd = escaped ? 60 : 40;
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction cb) {
        if (!hasPassenger(passenger)) return;
        Vec3 f = Vec3.directionFromRotation(0, getYRot());
        double d = grabDistance();
        cb.accept(passenger, getX() + f.x * d, getY(), getZ() + f.z * d);
        passenger.setYBodyRot(getYRot() + 180F);
    }

    protected double grabDistance() { return 0.75; }

    @Override
    public void onPassengerTurned(Entity e) {
        e.setYBodyRot(getYRot() + 180F);
        float d = Mth.wrapDegrees(e.getYRot() - (getYRot() + 180F));
        float c = Mth.clamp(d, -90F, 90F);
        e.yRotO += c - d;
        e.setYRot(e.getYRot() + c - d);
        e.setYHeadRot(e.getYRot());
    }

    @Override public boolean shouldRiderSit() { return false; }
    @Override protected boolean canAddPassenger(Entity e) { return getPassengers().isEmpty() && e instanceof Player; }
    @Override public LivingEntity getControllingPassenger() { return null; }

    // ------------------------------------------------------------------ tick ----
    protected void onForceRelease() { }

    /** suelta al jugador y limpia el estado (el jugador murio por otra causa, se desconecto...) */
    public void forceRelease(Player p) {
        if (p instanceof ServerPlayer sp) InfNet.CH.send(PacketDistributor.PLAYER.with(() -> sp), new InfNet.Qte(0, false, qteKind));
        releasing = true;
        p.stopRiding();
        releasing = false;
        grip = 50F;
        grabTicks = 0;
        move = null;
        attackCd = 100;
        onForceRelease();
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide) {
            for (Entity e : java.util.List.copyOf(getPassengers())) if (e.isRemoved() || e.getVehicle() != this) removePassenger(e);
            Entity pas = getFirstPassenger();
            if (pas instanceof Player pp && (!pp.isAlive() || pp.isRemoved())) forceRelease(pp);
            if (attackCd > 0) attackCd--;
            tickMove();
        }
    }

    @Override
    public void die(DamageSource src) {
        Entity e = getFirstPassenger();
        if (e instanceof Player p) endGrab(p, true);
        super.die(src);
    }

    // ------------------------------------------------------------------ IA de combate propia ----
    /** persigue al objetivo y, al tenerlo a tiro, deja que el mob elija el ataque */
    public static class ChaseGoal extends Goal {
        protected final InfMob mob;
        private final double walk, run;
        private int repath;

        public ChaseGoal(InfMob mob, double walk, double run) {
            this.mob = mob;
            this.walk = walk;
            this.run = run;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = mob.getTarget();
            return t != null && t.isAlive() && !mob.busy();
        }

        @Override
        public boolean canContinueToUse() { return canUse(); }

        @Override
        public void stop() { mob.getNavigation().stop(); }

        @Override
        public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void tick() {
            LivingEntity t = mob.getTarget();
            if (t == null) return;
            double d = Math.sqrt(mob.distanceToSqr(t));
            mob.getLookControl().setLookAt(t, 30, 30);
            if (d <= mob.attackRange() && mob.attackCd <= 0 && mob.hasLineOfSight(t)) {
                mob.chooseAttack(t, d);
                return;
            }
            if (--repath <= 0) {
                repath = 6 + mob.getRandom().nextInt(6);
                mob.getNavigation().moveTo(t, d > 10 ? run : walk);
            }
        }
    }
}
