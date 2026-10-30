package com.annis.npc.entity;

import com.annis.npc.ModEntities;
import com.annis.npc.net.NpcNetworking;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * NPC con cuerpo de jugador. El servidor solo guarda datos (skin, emote, quieto);
 * el cliente se encarga de dibujar y animar.
 */
public class AnnisNpcEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> SKIN =
            SynchedEntityData.defineId(AnnisNpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> SLIM =
            SynchedEntityData.defineId(AnnisNpcEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> EMOTE =
            SynchedEntityData.defineId(AnnisNpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> EMOTE_SERIAL =
            SynchedEntityData.defineId(AnnisNpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> STATIONARY =
            SynchedEntityData.defineId(AnnisNpcEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> PROTECTED =
            SynchedEntityData.defineId(AnnisNpcEntity.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<String> DIALOG =
            SynchedEntityData.defineId(AnnisNpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> FIGURA =
            SynchedEntityData.defineId(AnnisNpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> MODE =
            SynchedEntityData.defineId(AnnisNpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> ROUTINE =
            SynchedEntityData.defineId(AnnisNpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> ROUTINE_ON =
            SynchedEntityData.defineId(AnnisNpcEntity.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<Integer> EMOTE_START =
            SynchedEntityData.defineId(AnnisNpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> SCENE =
            SynchedEntityData.defineId(AnnisNpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> ACCESSORIES =
            SynchedEntityData.defineId(AnnisNpcEntity.class, EntityDataSerializers.STRING);
    public static final int MAX_ACCESSORIES = 12;
    private static final EntityDataAccessor<Boolean> JOINTED =
            SynchedEntityData.defineId(AnnisNpcEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> MUSIC =
            SynchedEntityData.defineId(AnnisNpcEntity.class, EntityDataSerializers.STRING);

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

    public AnnisNpcEntity(EntityType<? extends PathfinderMob> type, Level world) {
        super(type, world);
        this.setPersistenceRequired();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            this.setDropChance(slot, 0.0f);
        }
    }

    public static AttributeSupplier.Builder createNpcAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.ATTACK_DAMAGE, 3.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        SynchedEntityData builder = this.entityData;
        builder.define(SKIN, "");
        builder.define(SLIM, false);
        builder.define(EMOTE, "");
        builder.define(EMOTE_SERIAL, 0);
        builder.define(STATIONARY, false);
        builder.define(PROTECTED, true);
        builder.define(DIALOG, "");
        builder.define(FIGURA, "");
        builder.define(MODE, 0);
        builder.define(ROUTINE, "");
        builder.define(ROUTINE_ON, true);
        builder.define(EMOTE_START, 0);
        builder.define(SCENE, "");
        builder.define(ACCESSORIES, "");
        builder.define(MUSIC, "");
        builder.define(JOINTED, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.1, true) {
            @Override
            public boolean canUse() {
                return AnnisNpcEntity.this.getMode() == MODE_GUARD && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return AnnisNpcEntity.this.getMode() == MODE_GUARD && super.canContinueToUse();
            }
        });
        this.goalSelector.addGoal(2, new FollowOwnerGoal(this));
        this.goalSelector.addGoal(2, new RoutineGoal(this));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(4, new NpcWanderGoal(this, 0.6));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<Monster>(this, Monster.class, true) {
            @Override
            public boolean canUse() {
                return AnnisNpcEntity.this.getMode() == MODE_GUARD && super.canUse();
            }
        });
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            if (this.holdTicks > 0) {
                this.holdTicks--;
            }
            if (this.talkTicks > 0) {
                this.talkTicks--;
            }
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceSquared) {
        return false;
    }

    // ---- datos ----

    public String getSkin() {
        return this.entityData.get(SKIN);
    }

    public void setSkin(String skin) {
        this.entityData.set(SKIN, skin == null ? "" : skin);
    }

    public boolean isSlim() {
        return this.entityData.get(SLIM);
    }

    public void setSlim(boolean slim) {
        this.entityData.set(SLIM, slim);
    }

    public String getEmote() {
        return this.entityData.get(EMOTE);
    }

    public int getEmoteSerial() {
        return this.entityData.get(EMOTE_SERIAL);
    }

    public boolean isStationary() {
        return this.entityData.get(STATIONARY);
    }

    public void setStationary(boolean value) {
        this.entityData.set(STATIONARY, value);
    }

    /** Invencible: no recibe dano. Se guarda en el dato sincronizado para que el editor lo vea. */
    public boolean isProtected() {
        return this.entityData.get(PROTECTED);
    }

    public void setProtected(boolean value) {
        this.entityData.set(PROTECTED, value);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource damageSource) {
        return isProtected() || super.isInvulnerableTo(damageSource);
    }

    public boolean isBusy() {
        return isStationary() || this.holdTicks > 0 || this.talkTicks > 0
                || getMode() == MODE_FOLLOW || (isRoutineOn() && !getStops().isEmpty());
    }

    /** Tick del mundo en que empezo el emote actual: el reloj comun que sincroniza a todos los NPC. */
    /** Solo cliente: copia una animacion recibida para otra entidad (aldeano con aspecto Annis). */
    public void clientSetAnim(String name, int start, int serial) {
        if (serial != getEmoteSerial()) {
            this.entityData.set(EMOTE, name == null ? "" : name);
            this.entityData.set(EMOTE_START, start);
            this.entityData.set(EMOTE_SERIAL, serial);
        }
    }

    public int getEmoteStart() {
        return this.entityData.get(EMOTE_START);
    }

    /** Reinicia el emote actual en un tick concreto (para arrancar un grupo entero a la vez). */
    public void restartEmote(int worldTick) {
        if (getEmote().isEmpty()) {
            return;
        }
        this.entityData.set(EMOTE_START, worldTick);
        this.entityData.set(EMOTE_SERIAL, getEmoteSerial() + 1);
    }

    public String getScene() {
        return this.entityData.get(SCENE);
    }

    public void setScene(String group) {
        String g = group == null ? "" : group.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        this.entityData.set(SCENE, g.length() > 32 ? g.substring(0, 32) : g);
    }

    /** Modelo articulado (codos y rodillas) en vez del rigido de Minecraft. */
    public boolean isJointed() {
        return this.entityData.get(JOINTED);
    }

    public void setJointed(boolean value) {
        this.entityData.set(JOINTED, value);
    }

    /** Id del sonido (resource pack) que suena al sincronizar la escena, ej: annisnpc:musica.mi_cancion. */
    public String getMusic() {
        return this.entityData.get(MUSIC);
    }

    public void setMusic(String id) {
        String m = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        if (!m.isEmpty() && net.minecraft.resources.ResourceLocation.tryParse(m) == null) {
            return;
        }
        this.entityData.set(MUSIC, m.length() > 100 ? "" : m);
    }

    public String getAccessories() {
        return this.entityData.get(ACCESSORIES);
    }

    public void setAccessories(String raw) {
        String r = raw == null ? "" : raw;
        if (r.length() > 3000) {
            return;
        }
        String[] entries = r.isEmpty() ? new String[0] : r.split(";");
        if (entries.length > MAX_ACCESSORIES) {
            return;
        }
        this.entityData.set(ACCESSORIES, r);
    }

    /** Pone la entrada index (o la anade si index < 0 o fuera de rango). Entrada vacia = quitar. */
    public void setAccessory(int index, String entry) {
        List<String> list = new ArrayList<>();
        for (String e : getAccessories().split(";")) {
            if (!e.isEmpty()) {
                list.add(e);
            }
        }
        String clean = entry == null ? "" : entry.replace(";", "");
        if (index >= 0 && index < list.size()) {
            if (clean.isEmpty()) {
                list.remove(index);
            } else {
                list.set(index, clean);
            }
        } else if (!clean.isEmpty() && list.size() < MAX_ACCESSORIES) {
            list.add(clean);
        }
        setAccessories(String.join(";", list));
    }

    /** Copia el aspecto y la configuracion de otro NPC (no copia la rutina ni la posicion). */
    public void copyFrom(AnnisNpcEntity other) {
        setSkin(other.getSkin());
        setSlim(other.isSlim());
        setDialog(other.getDialog());
        setFigura(other.getFigura());
        setScene(other.getScene());
        setMusic(other.getMusic());
        setJointed(other.isJointed());
        setAccessories(other.getAccessories());
        setProtected(other.isProtected());
        setStationary(other.isStationary());
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            setItemSlot(slot, other.getItemBySlot(slot).copy());
        }
        if (!other.getEmote().isEmpty()) {
            playEmote(other.getEmote(), 3600 * 20);
            restartEmote(other.getEmoteStart());
        }
    }

    public boolean isTalking() {
        return this.talkTicks > 0;
    }

    // ---- extension: modo, dialogo, figura, rutina ----

    /** Dueno (quien lo puso en seguir/guardia), o null. */
    public java.util.UUID getOwnerUuid() {
        return this.owner;
    }

    public int getMode() {
        return this.entityData.get(MODE);
    }

    public void setMode(int mode, UUID who) {
        int m = Math.max(0, Math.min(2, mode));
        this.entityData.set(MODE, m);
        if (m == MODE_FOLLOW && who != null) {
            this.owner = who;
        }
        this.getNavigation().stop();
        if (m != MODE_GUARD) {
            this.setTarget(null);
        }
    }

    public String getDialog() {
        return this.entityData.get(DIALOG);
    }

    public void setDialog(String text) {
        String t = text == null ? "" : text.trim();
        this.entityData.set(DIALOG, t.length() > 1500 ? t.substring(0, 1500) : t);
    }

    public String getFigura() {
        return this.entityData.get(FIGURA);
    }

    public void setFigura(String name) {
        String n = name == null ? "" : name.trim();
        if (n.length() > 64 || n.contains("..") || n.contains("/") || n.contains("\\") || n.contains(":")) {
            n = "";
        }
        this.entityData.set(FIGURA, n);
    }

    public String getRoutine() {
        return this.entityData.get(ROUTINE);
    }

    public boolean isRoutineOn() {
        return this.entityData.get(ROUTINE_ON);
    }

    public void setRoutineOn(boolean on) {
        this.entityData.set(ROUTINE_ON, on);
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
        this.entityData.set(ROUTINE, sb.toString());
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
        this.entityData.set(ROUTINE, "");
        this.getNavigation().stop();
    }

    /** Devuelve el equipo al jugador (o lo suelta) y deja al NPC sin nada puesto. */
    public void clearEquipment(Player to) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = getItemBySlot(slot);
            if (!stack.isEmpty()) {
                if (to != null) {
                    to.getInventory().placeItemBackInInventory(stack.copy());
                }
                setItemSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer sp) || hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && !held.isEmpty() && sp.hasPermissions(2) && !held.is(ModEntities.NPC_WAND)) {
            EquipmentSlot slot = getEquipmentSlotForItem(held);
            if (slot.getType() != EquipmentSlot.Type.ARMOR && slot != EquipmentSlot.OFFHAND) {
                slot = EquipmentSlot.MAINHAND;
            }
            ItemStack old = getItemBySlot(slot);
            setItemSlot(slot, held.copyWithCount(1));
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
            if (!old.isEmpty()) {
                player.getInventory().placeItemBackInInventory(old);
            }
            return InteractionResult.SUCCESS;
        }
        if (!getDialog().isEmpty()) {
            this.talkTicks = 200;
            this.getNavigation().stop();
            this.getLookControl().setLookAt(player);
            NpcNetworking.openDialog(sp, this);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /** Lanza un emote en los clientes. El NPC se queda quieto holdTicks ticks. */
    public void playEmote(String name, int holdTicks) {
        this.entityData.set(EMOTE_START, (int) this.level().getGameTime());
        this.entityData.set(EMOTE, name == null ? "" : name);
        this.entityData.set(EMOTE_SERIAL, getEmoteSerial() + 1);
        this.holdTicks = Math.max(0, holdTicks);
        this.getNavigation().stop();
    }

    public void stopEmote() {
        this.entityData.set(EMOTE, "");
        this.entityData.set(EMOTE_SERIAL, getEmoteSerial() + 1);
        this.holdTicks = 0;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putString("Skin", getSkin());
        nbt.putBoolean("Slim", isSlim());
        nbt.putBoolean("Stationary", isStationary());
        nbt.putBoolean("Protected", isProtected());
        nbt.putString("Dialog", getDialog());
        nbt.putString("Figura", getFigura());
        nbt.putInt("Mode", getMode());
        nbt.putString("Routine", getRoutine());
        nbt.putBoolean("RoutineOn", isRoutineOn());
        nbt.putString("Scene", getScene());
        nbt.putString("Accessories", getAccessories());
        nbt.putString("Music", getMusic());
        nbt.putBoolean("Jointed", isJointed());
        nbt.putString("Emote", getEmote());
        if (this.owner != null) {
            nbt.putUUID("Owner", this.owner);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        setSkin(nbt.getString("Skin"));
        setSlim(nbt.getBoolean("Slim"));
        setStationary(nbt.getBoolean("Stationary"));
        setProtected(!nbt.contains("Protected") || nbt.getBoolean("Protected"));
        setDialog(nbt.getString("Dialog"));
        setFigura(nbt.getString("Figura"));
        this.entityData.set(MODE, Math.max(0, Math.min(2, nbt.getInt("Mode"))));
        this.entityData.set(ROUTINE, nbt.getString("Routine"));
        this.entityData.set(ROUTINE_ON, !nbt.contains("RoutineOn") || nbt.getBoolean("RoutineOn"));
        this.owner = nbt.hasUUID("Owner") ? nbt.getUUID("Owner") : null;
        setScene(nbt.getString("Scene"));
        setAccessories(nbt.getString("Accessories"));
        setMusic(nbt.getString("Music"));
        setJointed(nbt.getBoolean("Jointed"));
        // las actividades en bucle (bailar, tocar...) sobreviven al reinicio del servidor
        if (!nbt.getString("Emote").isEmpty()) {
            this.entityData.set(EMOTE, nbt.getString("Emote"));
            this.entityData.set(EMOTE_SERIAL, getEmoteSerial() + 1);
            this.holdTicks = 20 * 60 * 60;
        }
    }

    /** Seguir al jugador que puso el modo "seguirme". */
    private static class FollowOwnerGoal extends Goal {
        private final AnnisNpcEntity npc;
        private Player target;
        private int recalc;

        FollowOwnerGoal(AnnisNpcEntity npc) {
            this.npc = npc;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        private Player find() {
            if (npc.owner == null) {
                return null;
            }
            Player p = npc.level().getPlayerByUUID(npc.owner);
            return p != null && !p.isSpectator() ? p : null;
        }

        @Override
        public boolean canUse() {
            if (npc.getMode() != MODE_FOLLOW || npc.isTalking()) {
                return false;
            }
            Player p = find();
            if (p == null || npc.distanceToSqr(p) < 16.0) {
                return false;
            }
            this.target = p;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return npc.getMode() == MODE_FOLLOW && !npc.isTalking() && target != null && target.isAlive()
                    && npc.distanceToSqr(target) > 6.0;
        }

        @Override
        public void tick() {
            npc.getLookControl().setLookAt(target, 10.0f, npc.getMaxHeadXRot());
            if (npc.distanceToSqr(target) > 24 * 24) {
                npc.moveTo(target.getX(), target.getY(), target.getZ(), npc.getYRot(), 0.0f);
                npc.getNavigation().stop();
                return;
            }
            if (--recalc <= 0) {
                recalc = 10;
                npc.getNavigation().moveTo(target, 1.1);
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
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return npc.isRoutineOn() && npc.getMode() != MODE_FOLLOW && !npc.isTalking()
                    && npc.getMode() != MODE_GUARD && !npc.isStationary() && !npc.getStops().isEmpty();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
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
                npc.getNavigation().moveTo(s.x(), s.y(), s.z(), 0.9);
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
                boolean stuck = walkTicks > 40 && npc.getNavigation().isDone();
                if (near || stuck || walkTicks > 900) {
                    npc.getNavigation().stop();
                    doing = true;
                    timer = s.seconds() * 20;
                    if (!s.activity().isEmpty()) {
                        npc.playEmote(s.activity(), timer);
                    }
                } else if (walkTicks % 40 == 0 && npc.getNavigation().isDone()) {
                    npc.getNavigation().moveTo(s.x(), s.y(), s.z(), 0.9);
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
    private static class NpcWanderGoal extends WaterAvoidingRandomStrollGoal {
        private final AnnisNpcEntity npc;

        NpcWanderGoal(AnnisNpcEntity npc, double speed) {
            super(npc, speed);
            this.npc = npc;
        }

        @Override
        public boolean canUse() {
            return !npc.isBusy() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return !npc.isBusy() && super.canContinueToUse();
        }
    }
}
