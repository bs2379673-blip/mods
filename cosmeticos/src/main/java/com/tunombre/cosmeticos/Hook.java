package com.tunombre.cosmeticos;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gancho invisible de la guadaña del dios oscuro. El jugador enganchado "monta" este gancho, que sigue al administrador: si el admin vuela,
 * cuelga de la punta de la hoja y se balancea; si camina, va arrastrado por el piso detrás de él. La posición se calcula igual en el
 * servidor y en cada cliente (a partir del dueño), así se ve suave para todos, también para el que va colgado.
 */
public class Hook extends Entity {
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(Hook.class, EntityDataSerializers.INT);
    /** dueño → último tick en que se lo vio llevando a alguien (para que los cosméticos sepan que está cargando) */
    private static final Map<Integer, Long> CARRIERS = new ConcurrentHashMap<>();

    /** si es false, el enganchado no se puede soltar solo */
    public boolean releasing;
    /** balanceo del colgado (solo cliente, para el dibujo) */
    public float swingX, swingZ, swingVX, swingVZ;

    public Hook(EntityType<? extends Hook> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setInvulnerable(true);
    }

    public static boolean isCarrying(Entity owner) {
        Long t = CARRIERS.get(owner.getId());
        return t != null && owner.level().getGameTime() - t < 4;
    }

    @Override
    protected void defineSynchedData() { entityData.define(OWNER, -1); }

    public void setOwner(int id) { entityData.set(OWNER, id); }

    public Entity owner() {
        int id = entityData.get(OWNER);
        return id < 0 ? null : level().getEntity(id);
    }

    /** el dueño está en el suelo (o casi): el enganchado va arrastrado en vez de colgado */
    public boolean dragging() {
        Entity o = owner();
        if (!(o instanceof Player p)) return false;
        return o.onGround() && !p.getAbilities().flying;
    }

    private static float yawOf(Entity o) { return o instanceof LivingEntity le ? le.yBodyRot : o.getYRot(); }

    /** dónde está la punta de la hoja */
    private Vec3 target(Entity o) {
        double r = Math.toRadians(yawOf(o));
        Vec3 fwd = new Vec3(-Math.sin(r), 0, Math.cos(r)), right = new Vec3(-Math.cos(r), 0, -Math.sin(r));
        if (dragging()) return o.position().add(fwd.scale(-1.5)).add(right.scale(0.4)).add(0, 0.05, 0);
        return o.position().add(right.scale(0.42)).add(fwd.scale(-0.2)).add(0, -1.05, 0);
    }

    @Override
    public void tick() {
        super.tick();
        Entity o = owner();
        if (o == null || !o.isAlive() || o.level() != level()) {
            if (!level().isClientSide) { releasing = true; ejectPassengers(); discard(); }
            return;
        }
        if (!level().isClientSide && getPassengers().isEmpty() && tickCount > 10) { releasing = true; discard(); return; }
        CARRIERS.put(o.getId(), level().getGameTime());
        Vec3 t = target(o), cur = position();
        if (cur.distanceToSqr(t) > 64 || tickCount < 2) { setPos(t); setDeltaMovement(Vec3.ZERO); }
        else {
            // resorte con algo de retraso: el colgado se balancea y llega un poco después que el dueño
            Vec3 v = getDeltaMovement().scale(0.55).add(t.subtract(cur).scale(0.42));
            setDeltaMovement(v);
            setPos(cur.add(v));
        }
        if (level().isClientSide) {
            // péndulo: los pies se quedan atrás cuando el gancho acelera
            Vec3 v = getDeltaMovement();
            float tx = (float) Math.max(-0.9, Math.min(0.9, v.z * 2.4)), tz = (float) Math.max(-0.9, Math.min(0.9, -v.x * 2.4));
            swingVX += (tx - swingX) * 0.18F; swingVZ += (tz - swingZ) * 0.18F;
            swingVX *= 0.86F; swingVZ *= 0.86F;
            swingX += swingVX; swingZ += swingVZ;
        }
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        Entity o = owner();
        if (o == null) { move.accept(passenger, getX(), getY() - 1.45, getZ()); return; }
        if (dragging()) {
            // la cabeza en el gancho y el cuerpo tirado hacia atrás, en la dirección opuesta al dueño
            Vec3 d = new Vec3(o.getX() - getX(), 0, o.getZ() - getZ());
            d = d.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : d.normalize();
            move.accept(passenger, getX() - d.x * 1.55, getY(), getZ() - d.z * 1.55);
            if (passenger instanceof LivingEntity le) {
                float yaw = (float) Math.toDegrees(Math.atan2(d.x, -d.z));   // mirando para el lado contrario al dueño (boca arriba)
                le.setYBodyRot(yaw); le.yBodyRotO = yaw;
            }
        } else {
            move.accept(passenger, getX(), getY() - 1.45, getZ());
            if (passenger instanceof LivingEntity le) { float yaw = yawOf(o); le.setYBodyRot(yaw); le.yBodyRotO = yaw; }
        }
    }

    @Override public boolean shouldRiderSit() { return false; }

    @Override public boolean isPickable() { return false; }

    @Override public boolean isPushable() { return false; }

    @Override public boolean canBeCollidedWith() { return false; }

    @Override public boolean shouldBeSaved() { return false; }

    @Override
    public void lerpTo(double x, double y, double z, float yr, float xr, int steps, boolean teleport) {
        // en el cliente la posición se calcula sola a partir del dueño: se ignoran las correcciones del servidor
        Entity o = owner();
        if (o == null) super.lerpTo(x, y, z, yr, xr, steps, teleport);
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket() {
        return new net.minecraft.network.protocol.game.ClientboundAddEntityPacket(this);
    }

    @Override protected void readAdditionalSaveData(CompoundTag t) { }

    @Override protected void addAdditionalSaveData(CompoundTag t) { }
}
