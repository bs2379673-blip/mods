package com.annis.npc.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.text.Text;
import com.annis.npc.ModEntities;
import com.annis.npc.net.NpcNetworking;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

/**
 * NPC con cuerpo de jugador. El servidor solo guarda datos (skin, emote, quieto);
 * el cliente se encarga de dibujar y animar.
 */
public class AnnisNpcEntity extends PathAwareEntity {
    private static final TrackedData<String> SKIN =
            DataTracker.registerData(AnnisNpcEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Boolean> SLIM =
            DataTracker.registerData(AnnisNpcEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<String> EMOTE =
            DataTracker.registerData(AnnisNpcEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Integer> EMOTE_SERIAL =
            DataTracker.registerData(AnnisNpcEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> STATIONARY =
            DataTracker.registerData(AnnisNpcEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> PROTECTED =
            DataTracker.registerData(AnnisNpcEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private static final TrackedData<String> DIALOG =
            DataTracker.registerData(AnnisNpcEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<String> FIGURA =
            DataTracker.registerData(AnnisNpcEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Integer> MODE =
            DataTracker.registerData(AnnisNpcEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<String> ROUTINE =
            DataTracker.registerData(AnnisNpcEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Boolean> ROUTINE_ON =
            DataTracker.registerData(AnnisNpcEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    public static final int MODE_PASSIVE = 0;
    public static final int MODE_FOLLOW = 1;
    public static final int MODE_GUARD = 2;
    public static final int MAX_STOPS = 24;

    /** Una parada de la rutina. */
    public record Stop(double x, double y, double z, String activity, int seconds) {
    }

    private UUID owner;
    /** Ticks que dura una conversacion (el NPC se detiene), solo servidor. */
    private int talkTicks = 0;
    private String parsedFrom = "";
    private List<Stop> parsedStops = List.of();

    /** Ticks que el NPC se queda quieto despues de lanzar un emote (solo servidor). */
    private int holdTicks = 0;

    public AnnisNpcEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.setPersistent();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            this.setEquipmentDropChance(slot, 0.0f);
        }
    }

    public static DefaultAttributeContainer.Builder createNpcAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 24.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 3.0);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(SKIN, "");
        builder.add(SLIM, false);
        builder.add(EMOTE, "");
        builder.add(EMOTE_SERIAL, 0);
        builder.add(STATIONARY, false);
        builder.add(PROTECTED, true);
        builder.add(DIALOG, "");
        builder.add(FIGURA, "");
        builder.add(MODE, 0);
        builder.add(ROUTINE, "");
        builder.add(ROUTINE_ON, true);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new MeleeAttackGoal(this, 1.1, true) {
            @Override
            public boolean canStart() {
                return AnnisNpcEntity.this.getMode() == MODE_GUARD && super.canStart();
            }

            @Override
            public boolean shouldContinue() {
                return AnnisNpcEntity.this.getMode() == MODE_GUARD && super.shouldContinue();
            }
        });
        this.goalSelector.add(2, new FollowOwnerGoal(this));
        this.goalSelector.add(2, new RoutineGoal(this));
        this.goalSelector.add(3, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        this.goalSelector.add(4, new NpcWanderGoal(this, 0.6));
        this.goalSelector.add(5, new LookAroundGoal(this));
        this.targetSelector.add(1, new ActiveTargetGoal<HostileEntity>(this, HostileEntity.class, true) {
            @Override
            public boolean canStart() {
                return AnnisNpcEntity.this.getMode() == MODE_GUARD && super.canStart();
            }
        });
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.getWorld().isClient) {
            if (this.holdTicks > 0) {
                this.holdTicks--;
            }
            if (this.talkTicks > 0) {
                this.talkTicks--;
            }
        }
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    // ---- datos ----

    public String getSkin() {
        return this.dataTracker.get(SKIN);
    }

    public void setSkin(String skin) {
        this.dataTracker.set(SKIN, skin == null ? "" : skin);
    }

    public boolean isSlim() {
        return this.dataTracker.get(SLIM);
    }

    public void setSlim(boolean slim) {
        this.dataTracker.set(SLIM, slim);
    }

    public String getEmote() {
        return this.dataTracker.get(EMOTE);
    }

    public int getEmoteSerial() {
        return this.dataTracker.get(EMOTE_SERIAL);
    }

    public boolean isStationary() {
        return this.dataTracker.get(STATIONARY);
    }

    public void setStationary(boolean value) {
        this.dataTracker.set(STATIONARY, value);
    }

    /** Invencible: no recibe dano. Se guarda en el dato sincronizado para que el editor lo vea. */
    public boolean isProtected() {
        return this.dataTracker.get(PROTECTED);
    }

    public void setProtected(boolean value) {
        this.dataTracker.set(PROTECTED, value);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource damageSource) {
        return isProtected() || super.isInvulnerableTo(damageSource);
    }

    public boolean isBusy() {
        return isStationary() || this.holdTicks > 0 || this.talkTicks > 0
                || getMode() == MODE_FOLLOW || (isRoutineOn() && !getStops().isEmpty());
    }

    public boolean isTalking() {
        return this.talkTicks > 0;
    }

    // ---- extension: modo, dialogo, figura, rutina ----

    public int getMode() {
        return this.dataTracker.get(MODE);
    }

    public void setMode(int mode, UUID who) {
        int m = Math.max(0, Math.min(2, mode));
        this.dataTracker.set(MODE, m);
        if (m == MODE_FOLLOW && who != null) {
            this.owner = who;
        }
        this.getNavigation().stop();
        if (m != MODE_GUARD) {
            this.setTarget(null);
        }
    }

    public String getDialog() {
        return this.dataTracker.get(DIALOG);
    }

    public void setDialog(String text) {
        String t = text == null ? "" : text.trim();
        this.dataTracker.set(DIALOG, t.length() > 1500 ? t.substring(0, 1500) : t);
    }

    public String getFigura() {
        return this.dataTracker.get(FIGURA);
    }

    public void setFigura(String name) {
        String n = name == null ? "" : name.trim();
        if (n.length() > 64 || n.contains("..") || n.contains("/") || n.contains("\\") || n.contains(":")) {
            n = "";
        }
        this.dataTracker.set(FIGURA, n);
    }

    public String getRoutine() {
        return this.dataTracker.get(ROUTINE);
    }

    public boolean isRoutineOn() {
        return this.dataTracker.get(ROUTINE_ON);
    }

    public void setRoutineOn(boolean on) {
        this.dataTracker.set(ROUTINE_ON, on);
        if (!on) {
            this.getNavigation().stop();
        }
    }

    public List<Stop> getStops() {
        String raw = getRoutine();
        if (!raw.equals(this.parsedFrom)) {
            List<Stop> out = new ArrayList<>();
            for (String part : raw.split(";")) {
                String[] f = part.split(",", -1);
                if (f.length < 5) {
                    continue;
                }
                try {
                    out.add(new Stop(Double.parseDouble(f[0]), Double.parseDouble(f[1]), Double.parseDouble(f[2]),
                            f[3], Integer.parseInt(f[4])));
                } catch (NumberFormatException ignored) {
                }
            }
            this.parsedStops = out;
            this.parsedFrom = raw;
        }
        return this.parsedStops;
    }

    private void writeStops(List<Stop> stops) {
        StringBuilder sb = new StringBuilder();
        for (Stop s : stops) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(String.format(Locale.ROOT, "%.2f,%.2f,%.2f,%s,%d", s.x(), s.y(), s.z(), s.activity(), s.seconds()));
        }
        this.dataTracker.set(ROUTINE, sb.toString());
    }

    public boolean addStop(double x, double y, double z, String activity, int seconds) {
        List<Stop> stops = new ArrayList<>(getStops());
        if (stops.size() >= MAX_STOPS) {
            return false;
        }
        String act = activity == null ? "" : activity.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        stops.add(new Stop(x, y, z, act, Math.max(1, Math.min(3600, seconds))));
        writeStops(stops);
        return true;
    }

    public void removeLastStop() {
        List<Stop> stops = new ArrayList<>(getStops());
        if (!stops.isEmpty()) {
            stops.remove(stops.size() - 1);
            writeStops(stops);
        }
    }

    public void clearStops() {
        this.dataTracker.set(ROUTINE, "");
        this.getNavigation().stop();
    }

    /** Devuelve el equipo al jugador (o lo suelta) y deja al NPC sin nada puesto. */
    public void clearEquipment(PlayerEntity to) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = getEquippedStack(slot);
            if (!stack.isEmpty()) {
                if (to != null) {
                    to.getInventory().offerOrDrop(stack.copy());
                }
                equipStack(slot, ItemStack.EMPTY);
            }
        }
    }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (this.getWorld().isClient) {
            return ActionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayerEntity sp) || hand != Hand.MAIN_HAND) {
            return ActionResult.PASS;
        }
        ItemStack held = player.getStackInHand(hand);
        if (player.isSneaking() && !held.isEmpty() && sp.hasPermissionLevel(2) && !held.isOf(ModEntities.NPC_WAND)) {
            EquipmentSlot slot = getPreferredEquipmentSlot(held);
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR && slot != EquipmentSlot.OFFHAND) {
                slot = EquipmentSlot.MAINHAND;
            }
            ItemStack old = getEquippedStack(slot);
            equipStack(slot, held.copyWithCount(1));
            if (!player.getAbilities().creativeMode) {
                held.decrement(1);
            }
            if (!old.isEmpty()) {
                player.getInventory().offerOrDrop(old);
            }
            return ActionResult.SUCCESS;
        }
        if (!getDialog().isEmpty()) {
            this.talkTicks = 200;
            this.getNavigation().stop();
            this.getLookControl().lookAt(player);
            NpcNetworking.openDialog(sp, this);
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    /** Lanza un emote en los clientes. El NPC se queda quieto holdTicks ticks. */
    public void playEmote(String name, int holdTicks) {
        this.dataTracker.set(EMOTE, name == null ? "" : name);
        this.dataTracker.set(EMOTE_SERIAL, getEmoteSerial() + 1);
        this.holdTicks = Math.max(0, holdTicks);
        this.getNavigation().stop();
    }

    public void stopEmote() {
        this.dataTracker.set(EMOTE, "");
        this.dataTracker.set(EMOTE_SERIAL, getEmoteSerial() + 1);
        this.holdTicks = 0;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putString("Skin", getSkin());
        nbt.putBoolean("Slim", isSlim());
        nbt.putBoolean("Stationary", isStationary());
        nbt.putBoolean("Protected", isProtected());
        nbt.putString("Dialog", getDialog());
        nbt.putString("Figura", getFigura());
        nbt.putInt("Mode", getMode());
        nbt.putString("Routine", getRoutine());
        nbt.putBoolean("RoutineOn", isRoutineOn());
        if (this.owner != null) {
            nbt.putUuid("Owner", this.owner);
        }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        setSkin(nbt.getString("Skin"));
        setSlim(nbt.getBoolean("Slim"));
        setStationary(nbt.getBoolean("Stationary"));
        setProtected(!nbt.contains("Protected") || nbt.getBoolean("Protected"));
        setDialog(nbt.getString("Dialog"));
        setFigura(nbt.getString("Figura"));
        this.dataTracker.set(MODE, Math.max(0, Math.min(2, nbt.getInt("Mode"))));
        this.dataTracker.set(ROUTINE, nbt.getString("Routine"));
        this.dataTracker.set(ROUTINE_ON, !nbt.contains("RoutineOn") || nbt.getBoolean("RoutineOn"));
        this.owner = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
    }

    /** Seguir al jugador que puso el modo "seguirme". */
    private static class FollowOwnerGoal extends Goal {
        private final AnnisNpcEntity npc;
        private PlayerEntity target;
        private int recalc;

        FollowOwnerGoal(AnnisNpcEntity npc) {
            this.npc = npc;
            this.setControls(EnumSet.of(Control.MOVE));
        }

        private PlayerEntity find() {
            if (npc.owner == null) {
                return null;
            }
            PlayerEntity p = npc.getWorld().getPlayerByUuid(npc.owner);
            return p != null && !p.isSpectator() ? p : null;
        }

        @Override
        public boolean canStart() {
            if (npc.getMode() != MODE_FOLLOW || npc.isTalking()) {
                return false;
            }
            PlayerEntity p = find();
            if (p == null || npc.squaredDistanceTo(p) < 16.0) {
                return false;
            }
            this.target = p;
            return true;
        }

        @Override
        public boolean shouldContinue() {
            return npc.getMode() == MODE_FOLLOW && !npc.isTalking() && target != null && target.isAlive()
                    && npc.squaredDistanceTo(target) > 6.0;
        }

        @Override
        public void tick() {
            npc.getLookControl().lookAt(target, 10.0f, npc.getMaxLookPitchChange());
            if (npc.squaredDistanceTo(target) > 24 * 24) {
                npc.refreshPositionAndAngles(target.getX(), target.getY(), target.getZ(), npc.getYaw(), 0.0f);
                npc.getNavigation().stop();
                return;
            }
            if (--recalc <= 0) {
                recalc = 10;
                npc.getNavigation().startMovingTo(target, 1.1);
            }
        }

        @Override
        public void stop() {
            npc.getNavigation().stop();
            target = null;
        }
    }

    /** Recorre las paradas de la rutina: caminar, hacer la actividad durante unos segundos, seguir. */
    private static class RoutineGoal extends Goal {
        private final AnnisNpcEntity npc;
        private int index;
        private boolean doing;
        private int timer;
        private int walkTicks;

        RoutineGoal(AnnisNpcEntity npc) {
            this.npc = npc;
            this.setControls(EnumSet.of(Control.MOVE));
        }

        @Override
        public boolean canStart() {
            return npc.isRoutineOn() && npc.getMode() != MODE_FOLLOW && !npc.isTalking()
                    && npc.getMode() != MODE_GUARD && !npc.isStationary() && !npc.getStops().isEmpty();
        }

        @Override
        public boolean shouldContinue() {
            return canStart();
        }

        @Override
        public void start() {
            doing = false;
            walkTicks = 0;
            beginWalk();
        }

        private Stop current() {
            List<Stop> stops = npc.getStops();
            if (stops.isEmpty()) {
                return null;
            }
            index = Math.floorMod(index, stops.size());
            return stops.get(index);
        }

        private void beginWalk() {
            Stop s = current();
            if (s != null) {
                walkTicks = 0;
                doing = false;
                npc.getNavigation().startMovingTo(s.x(), s.y(), s.z(), 0.9);
            }
        }

        @Override
        public void tick() {
            Stop s = current();
            if (s == null) {
                return;
            }
            if (!doing) {
                walkTicks++;
                double dx = npc.getX() - s.x();
                double dz = npc.getZ() - s.z();
                double dy = Math.abs(npc.getY() - s.y());
                boolean near = dx * dx + dz * dz < 2.25 && dy < 2.5;
                boolean stuck = walkTicks > 40 && npc.getNavigation().isIdle();
                if (near || stuck || walkTicks > 900) {
                    npc.getNavigation().stop();
                    doing = true;
                    timer = s.seconds() * 20;
                    if (!s.activity().isEmpty()) {
                        npc.playEmote(s.activity(), timer);
                    }
                } else if (walkTicks % 40 == 0 && npc.getNavigation().isIdle()) {
                    npc.getNavigation().startMovingTo(s.x(), s.y(), s.z(), 0.9);
                }
            } else if (--timer <= 0) {
                if (!s.activity().isEmpty()) {
                    npc.stopEmote();
                }
                index++;
                beginWalk();
            }
        }

        @Override
        public void stop() {
            npc.getNavigation().stop();
        }
    }

    /** Caminar al azar, pero solo si el NPC no esta quieto ni haciendo un emote. */
    private static class NpcWanderGoal extends WanderAroundFarGoal {
        private final AnnisNpcEntity npc;

        NpcWanderGoal(AnnisNpcEntity npc, double speed) {
            super(npc, speed);
            this.npc = npc;
        }

        @Override
        public boolean canStart() {
            return !npc.isBusy() && super.canStart();
        }

        @Override
        public boolean shouldContinue() {
            return !npc.isBusy() && super.shouldContinue();
        }
    }
}
