package com.tunombre.obscurenpc;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

/**
 * El NPC. Una sola clase para los dos "motores": el tipo obscurenpc:npc se anima con los huesos de Epic Fight y
 * obscurenpc:npc_vanilla con los huesos normales de Minecraft (modelo de jugador).
 */
public class Actor extends PathfinderMob {
    public static final EntityDataAccessor<String> SKIN = SynchedEntityData.defineId(Actor.class, EntityDataSerializers.STRING);
    public static final EntityDataAccessor<Boolean> SLIM = SynchedEntityData.defineId(Actor.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(Actor.class, EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Integer> STANCE = SynchedEntityData.defineId(Actor.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<String> BUBBLE = SynchedEntityData.defineId(Actor.class, EntityDataSerializers.STRING);
    public static final EntityDataAccessor<String> NAME = SynchedEntityData.defineId(Actor.class, EntityDataSerializers.STRING);
    public static final EntityDataAccessor<Integer> NAME_COLOR = SynchedEntityData.defineId(Actor.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Boolean> SHOW_NAME = SynchedEntityData.defineId(Actor.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<String> GESTURE = SynchedEntityData.defineId(Actor.class, EntityDataSerializers.STRING);
    public static final EntityDataAccessor<Integer> GESTURE_AT = SynchedEntityData.defineId(Actor.class, EntityDataSerializers.INT);

    private static final Random RND = new Random();

    private NpcData d = new NpcData();
    private CompoundTag memory = new CompoundTag();
    private BlockPos home;
    private BlockPos spawner;
    private int bubbleLeft;
    public UUID talkingTo;                 // jugador con el que conversa (mira hacia el)
    public int talkUntil;                  // tickCount hasta el que se considera "hablando"
    private Entity lookTarget;
    private int lookScan;
    private int targetScan;
    private int idleSoundIn = 100;
    public int chatterCooldown = 400;
    private int greetCooldown;
    private int shootCooldown;
    private final Routine routine = new Routine();
    private float appliedScale = -1F;

    public Actor(EntityType<? extends Actor> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 0.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    public boolean isEf() { return this.getType() == ObscureNpc.NPC_EF.get(); }

    public NpcData data() { return d; }

    public Routine routine() { return routine; }

    public BlockPos home() { return home != null ? home : blockPosition(); }

    public void setSpawner(BlockPos p) { this.spawner = p; }

    public BlockPos spawner() { return spawner; }

    // ------------------------------------------------------------------ datos sincronizados ----
    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(SKIN, "");
        this.entityData.define(SLIM, false);
        this.entityData.define(SCALE, 1.0F);
        this.entityData.define(STANCE, 0);
        this.entityData.define(BUBBLE, "");
        this.entityData.define(NAME, "");
        this.entityData.define(NAME_COLOR, 0xFFFFFF);
        this.entityData.define(SHOW_NAME, true);
        this.entityData.define(GESTURE, "");
        this.entityData.define(GESTURE_AT, 0);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key == SCALE) this.refreshDimensions();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        float s = this.entityData.get(SCALE);
        return super.getDimensions(pose).scale(s <= 0 ? 1F : s);
    }

    public float scale() { return this.entityData.get(SCALE); }

    public int stance() { return this.entityData.get(STANCE); }

    public String bubble() { return this.entityData.get(BUBBLE); }

    public String gesture() { return this.entityData.get(GESTURE); }

    public int gestureAt() { return this.entityData.get(GESTURE_AT); }

    // ------------------------------------------------------------------ configuracion ----
    public void setData(NpcData nd) {
        this.d = nd;
        applyData();
    }

    /** vuelca la configuracion a la entidad (atributos, nombre, tamano, postura...) */
    public void applyData() {
        if (level().isClientSide) return;
        entityData.set(SKIN, d.skin == null ? "" : d.skin);
        entityData.set(SLIM, d.brazos_finos);
        entityData.set(SCALE, Math.max(0.2F, Math.min(4F, d.tamano)));
        entityData.set(STANCE, d.postura);
        entityData.set(NAME, d.nombre == null ? "" : d.nombre);
        int col = 0xFFFFFF;
        try { col = Integer.parseInt(d.color_nombre.replace("#", ""), 16); } catch (RuntimeException ignored) { }
        entityData.set(NAME_COLOR, col);
        entityData.set(SHOW_NAME, d.mostrar_nombre);
        setCustomName(Component.literal(d.nombre == null ? "" : d.nombre));
        setCustomNameVisible(false);          // el nombre lo dibuja el mod (sirve igual con Epic Fight)
        setPersistenceRequired();
        setBase(Attributes.MAX_HEALTH, Math.max(1, d.vida));
        setBase(Attributes.MOVEMENT_SPEED, Math.max(0.0, d.velocidad));
        setBase(Attributes.ATTACK_DAMAGE, Math.max(0.0, d.dano));
        setBase(Attributes.ARMOR, Math.max(0.0, d.armadura));
        setBase(Attributes.KNOCKBACK_RESISTANCE, Math.max(0.0, Math.min(1.0, d.empuje_res)));
        setBase(Attributes.FOLLOW_RANGE, Math.max(4.0, d.rango_vision));
        if (getHealth() > getMaxHealth()) setHealth(getMaxHealth());
        float chance = d.suelta_equipo ? 1.0F : 0.0F;
        for (EquipmentSlot s : EquipmentSlot.values()) setDropChance(s, chance);
        setInvulnerable(false);
        if (!d.fijar_giro || d.quieto) { /* nada */ }
        // la postura "durmiendo" tumba al modelo normal; Epic Fight lo hace con su animacion
        if (!isEf()) setForcedPose(d.postura == 2 ? Pose.SLEEPING : null);
        applyEquipment();
        if (appliedScale != scale()) { appliedScale = scale(); refreshDimensions(); }
    }

    private void applyEquipment() {
        eq(EquipmentSlot.MAINHAND, d.eq_mano);
        eq(EquipmentSlot.OFFHAND, d.eq_mano2);
        eq(EquipmentSlot.HEAD, d.eq_cabeza);
        eq(EquipmentSlot.CHEST, d.eq_pecho);
        eq(EquipmentSlot.LEGS, d.eq_piernas);
        eq(EquipmentSlot.FEET, d.eq_pies);
    }

    private void eq(EquipmentSlot slot, String snbt) {
        if (snbt == null || snbt.isBlank()) return;
        try {
            ItemStack st = ItemStack.of(net.minecraft.nbt.TagParser.parseTag(snbt));
            if (!ItemStack.matches(getItemBySlot(slot), st)) setItemSlot(slot, st);
        } catch (Exception ignored) { }
    }

    public static String stackToSnbt(ItemStack st) { return st.isEmpty() ? "" : st.save(new CompoundTag()).toString(); }

    private void setBase(net.minecraft.world.entity.ai.attributes.Attribute a, double v) {
        AttributeInstance i = getAttribute(a);
        if (i != null && i.getBaseValue() != v) i.setBaseValue(v);
    }

    // ------------------------------------------------------------------ IA ----
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(-1, new GunGoal(this));
        this.goalSelector.addGoal(0, new FloatGoal(this));
        if (!isEf()) this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15D, true));
        this.goalSelector.addGoal(6, new WanderGoal(this));
    }

    /** vagar cerca del "hogar" */
    private static final class WanderGoal extends Goal {
        private final Actor a;

        WanderGoal(Actor a) {
            this.a = a;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            NpcData d = a.d;
            return d.vagar && !d.quieto && a.getTarget() == null && !a.isTalking() && !a.routine.active() && a.getRandom().nextInt(90) == 0;
        }

        @Override
        public boolean canContinueToUse() { return !a.getNavigation().isDone() && a.getTarget() == null && !a.isTalking(); }

        @Override
        public void start() {
            for (int i = 0; i < 6; i++) {
                Vec3 p = LandRandomPos.getPos(a, 6, 3);
                if (p == null) continue;
                if (Math.sqrt(a.home().distSqr(BlockPos.containing(p))) > a.d.vagar_radio) continue;
                a.getNavigation().moveTo(p.x, p.y, p.z, 0.8D);
                return;
            }
        }
    }

    /** si lleva un arma TACZ en la mano: apunta y dispara al objetivo */
    private static final class GunGoal extends Goal {
        private final Actor a;

        GunGoal(Actor a) {
            this.a = a;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = a.getTarget();
            return t != null && t.isAlive() && TaczBridge.holdsGun(a);
        }

        @Override
        public boolean canContinueToUse() { return canUse(); }

        @Override
        public void stop() { TaczBridge.aim(a, false); a.getNavigation().stop(); }

        @Override
        public void tick() {
            LivingEntity t = a.getTarget();
            if (t == null) return;
            a.getLookControl().setLookAt(t, 40F, 40F);
            double dist = a.distanceTo(t);
            boolean los = a.hasLineOfSight(t);
            double range = Math.max(4, a.d.arma_alcance);
            if (dist > range * 0.85 || !los) a.getNavigation().moveTo(t, 1.0D);
            else a.getNavigation().stop();
            if (los && dist <= range) {
                TaczBridge.aim(a, true);
                if (a.shootCooldown > 0) a.shootCooldown--;
                else {
                    TaczBridge.shoot(a, t, a.d.arma_dispersion, a.d.arma_infinita);
                    a.shootCooldown = Math.max(1, a.d.arma_cadencia);
                }
            }
        }
    }

    public boolean isTalking() { return tickCount < talkUntil; }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (tickCount % 20 == 0 && d.regeneracion > 0 && getHealth() < getMaxHealth()) heal((float) d.regeneracion);
        if (bubbleLeft > 0 && --bubbleLeft == 0) entityData.set(BUBBLE, "");
        if (home == null) home = blockPosition();
        if (d.fijar_giro && !isTalking() && lookTarget == null) {
            setYRot(d.giro);
            setYBodyRot(d.giro);
            setYHeadRot(d.giro);
        }
        targetTick();
        lookTick();
        routine.tick(this);
        idleSound();
        if (d.saludo_auto && !d.saludo.isBlank() && greetCooldown-- <= 0) greetTick();
        if (d.charla_npc && chatterCooldown-- <= 0) Chatter.tick(this);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide && (d.quieto || isTalking() && talkingTo != null) && !routine.moving()) {
            getNavigation().stop();
            Vec3 m = getDeltaMovement();
            setDeltaMovement(0, m.y, 0);
        }
    }

    private void lookTick() {
        Entity speaker = null;
        if (talkingTo != null && level() instanceof ServerLevel sl) speaker = sl.getPlayerByUUID(talkingTo);
        if (speaker == null && routine.lookPos() != null) {
            Vec3 p = routine.lookPos();
            getLookControl().setLookAt(p.x, p.y, p.z, 30F, 30F);
            return;
        }
        if (speaker != null && d.mirar_al_hablar) {
            getLookControl().setLookAt(speaker, 40F, 40F);
            lookTarget = speaker;
            return;
        }
        if (d.mirar.equals("no") || d.postura == 2) { lookTarget = null; return; }
        if (lookScan-- <= 0) {
            lookScan = 8;
            lookTarget = null;
            double r = Math.max(1, d.mirar_rango);
            double best = r * r;
            List<Entity> near = level().getEntities(this, getBoundingBox().inflate(r), e -> e != this && e instanceof LivingEntity && e.isAlive()
                    && (e instanceof Player p ? !p.isSpectator() : d.mirar.equals("todos")));
            for (Entity e : near) {
                double dd = distanceToSqr(e);
                if (dd < best) { best = dd; lookTarget = e; }
            }
        }
        if (lookTarget != null && lookTarget.isAlive()) getLookControl().setLookAt(lookTarget, 30F, 30F);
    }

    private void targetTick() {
        LivingEntity t = getTarget();
        if (t != null && (!t.isAlive() || distanceToSqr(t) > Math.pow(d.rango_vision * 1.6, 2) || (t instanceof Player p && (p.isCreative() || p.isSpectator())))) setTarget(null);
        if (d.hostil.equals("ninguno") || getTarget() != null || targetScan-- > 0) return;
        targetScan = 12;
        boolean pl = d.hostil.equals("jugadores") || d.hostil.equals("ambos");
        boolean mo = d.hostil.equals("mobs") || d.hostil.equals("ambos");
        double r = d.rango_vision;
        LivingEntity best = null;
        double bd = r * r;
        for (Entity e : level().getEntities(this, getBoundingBox().inflate(r), x -> x instanceof LivingEntity && x.isAlive())) {
            LivingEntity le = (LivingEntity) e;
            boolean ok = (pl && le instanceof Player p && !p.isCreative() && !p.isSpectator()) || (mo && le instanceof Enemy && !(le instanceof Actor));
            if (!ok || sameFaction(le) || !hasLineOfSight(le)) continue;
            double dd = distanceToSqr(le);
            if (dd < bd) { bd = dd; best = le; }
        }
        if (best != null) setTarget(best);
    }

    public boolean sameFaction(Entity e) {
        return e instanceof Actor o && !d.faccion.isBlank() && d.faccion.equalsIgnoreCase(o.d.faccion);
    }

    private void greetTick() {
        greetCooldown = 200;
        if (isTalking()) return;
        Player p = level().getNearestPlayer(this, Math.max(2, d.saludo_rango));
        if (p instanceof ServerPlayer sp && !sp.isSpectator() && hasLineOfSight(sp) && Dialogs.markGreeted(this, sp)) {
            speak(d.saludo, sp, true);
        }
    }

    private void idleSound() {
        if (d.snd_idle.isBlank() || d.snd_idle_cada <= 0 || isTalking()) return;
        if (--idleSoundIn > 0) return;
        idleSoundIn = d.snd_idle_cada / 2 + RND.nextInt(Math.max(1, d.snd_idle_cada));
        playCustom(d.snd_idle);
    }

    public void playCustom(String id) {
        SoundEvent ev = sound(id);
        if (ev != null) level().playSound(null, getX(), getY(), getZ(), ev, SoundSource.NEUTRAL, d.snd_vol, d.snd_tono);
    }

    public static SoundEvent sound(String id) {
        if (id == null || id.isBlank()) return null;
        try {
            ResourceLocation rl = new ResourceLocation(id.trim());
            SoundEvent ev = ForgeRegistries.SOUND_EVENTS.getValue(rl);
            return ev != null ? ev : SoundEvent.createVariableRangeEvent(rl);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource src) {
        SoundEvent ev = sound(d.snd_hurt);
        return ev != null ? ev : super.getHurtSound(src);
    }

    @Override
    protected SoundEvent getDeathSound() {
        SoundEvent ev = sound(d.snd_death);
        return ev != null ? ev : super.getDeathSound();
    }

    // ------------------------------------------------------------------ hablar, gestos, burbuja ----
    /** el NPC dice algo: burbuja, chat a los jugadores cercanos, gesto y sonido. Devuelve cuantos ticks dura. */
    public int speak(String raw, ServerPlayer listener, boolean toChat) {
        String text = Fmt.process(this, raw, listener);
        if (text.isBlank()) return 10;
        int speed = Math.max(20, d.velocidad_texto);
        int ticks = Math.max(30, (int) (text.length() * 3.2 * 100.0 / speed) + 16);
        if (d.burbuja) {
            entityData.set(BUBBLE, text);
            bubbleLeft = ticks + 20;
        }
        talkUntil = tickCount + ticks;
        if (d.chat && toChat) {
            Component line = Component.literal("<" + d.nombre + "> ").withStyle(s -> s.withColor(entityData.get(NAME_COLOR))).append(Component.literal(text).withStyle(net.minecraft.ChatFormatting.WHITE));
            for (Player p : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(18))) p.sendSystemMessage(line);
        }
        if (!d.snd_talk.isBlank()) playCustom(d.snd_talk);
        if (!Fmt.hadGesture()) gesture(randomGesture());
        return ticks;
    }

    public String randomGesture() {
        String[] g = d.gestos == null ? new String[0] : d.gestos.split(",");
        if (g.length == 0 || g[0].isBlank()) return "";
        return g[RND.nextInt(g.length)].trim();
    }

    /** reproduce un gesto: animacion de Epic Fight (propia o de otro mod por su nombre) o gesto procedural en el modelo normal */
    public void gesture(String id) {
        if (level().isClientSide || id == null || id.isBlank()) return;
        if (isEf()) {
            EfBridge.play(this, id);
        }
        entityData.set(GESTURE, id.toLowerCase(Locale.ROOT));
        entityData.set(GESTURE_AT, tickCount);
    }

    public void setStance(int s) {
        d.postura = s;
        entityData.set(STANCE, s);
        if (!isEf()) setForcedPose(s == 2 ? Pose.SLEEPING : null);
    }

    public void goTo(double x, double y, double z, double speed) {
        getNavigation().moveTo(x, y, z, speed);
        setAggressive(speed >= 1.4D);
    }

    // ------------------------------------------------------------------ interaccion y dano ----
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof NpcItems.EditorItem) return held.getItem().interactLivingEntity(held, player, this, hand);
        if (held.getItem() instanceof NpcItems.CreatorItem) return InteractionResult.PASS;
        Dialogs.interact(this, sp);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        boolean r = super.hurt(src, amount);
        if (r && !level().isClientSide) {
            Entity e = src.getEntity();
            if (e instanceof LivingEntity le && e != this && d.represalia && !sameFaction(le) && !(le instanceof Player p && (p.isCreative() || p.isSpectator()))) {
                setTarget(le);
                for (Actor o : level().getEntitiesOfClass(Actor.class, getBoundingBox().inflate(16)))
                    if (o != this && !o.d.faccion.isBlank() && o.d.faccion.equalsIgnoreCase(d.faccion) && o.getTarget() == null) o.setTarget(le);
            }
            if (e instanceof ServerPlayer sp) Dialogs.end(sp.getUUID());
            routine.interrupt();
        }
        return r;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource s) {
        if (d.invulnerable && !s.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return true;
        return super.isInvulnerableTo(s);
    }

    @Override
    public boolean isPushable() { return d.empujable && super.isPushable(); }

    @Override
    public boolean removeWhenFarAway(double dist) { return !d.persistente && super.removeWhenFarAway(dist); }

    @Override
    public boolean canPickUpLoot() { return false; }

    @Override
    public void die(DamageSource s) {
        super.die(s);
        if (!level().isClientSide) {
            Dialogs.endAllWith(this);
            if (spawner != null) SpawnerEntity.notifyDeath(level(), spawner);
        }
    }

    // ------------------------------------------------------------------ memoria por jugador ----
    public CompoundTag mem(UUID p) {
        String k = p.toString();
        if (!memory.contains(k, Tag.TAG_COMPOUND)) memory.put(k, new CompoundTag());
        return memory.getCompound(k);
    }

    public boolean hasFlag(UUID p, String f) {
        ListTag l = mem(p).getList("f", Tag.TAG_STRING);
        for (int i = 0; i < l.size(); i++) if (l.getString(i).equalsIgnoreCase(f)) return true;
        return false;
    }

    public void setFlag(UUID p, String f) {
        if (hasFlag(p, f)) return;
        CompoundTag m = mem(p);
        ListTag l = m.getList("f", Tag.TAG_STRING);
        l.add(StringTag.valueOf(f.toLowerCase(Locale.ROOT)));
        m.put("f", l);
    }

    public void clearFlag(UUID p, String f) {
        CompoundTag m = mem(p);
        ListTag l = m.getList("f", Tag.TAG_STRING);
        ListTag n = new ListTag();
        for (int i = 0; i < l.size(); i++) if (!l.getString(i).equalsIgnoreCase(f)) n.add(l.get(i));
        m.put("f", n);
    }

    public int counter(UUID p, String key) { return mem(p).getInt("n_" + key); }

    public void addCounter(UUID p, String key, int by) { mem(p).putInt("n_" + key, counter(p, key) + by); }

    public void setCounter(UUID p, String key, int v) { mem(p).putInt("n_" + key, v); }

    // ------------------------------------------------------------------ guardado ----
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("ObscureData", d.toJson());
        tag.put("ObscureMem", memory);
        if (home != null) tag.putLong("ObscureHome", home.asLong());
        if (spawner != null) tag.putLong("ObscureSpawner", spawner.asLong());
        tag.put("ObscureRoutine", routine.save());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("ObscureData")) d = NpcData.fromJson(tag.getString("ObscureData"));
        memory = tag.contains("ObscureMem", Tag.TAG_COMPOUND) ? tag.getCompound("ObscureMem") : new CompoundTag();
        if (tag.contains("ObscureHome")) home = BlockPos.of(tag.getLong("ObscureHome"));
        if (tag.contains("ObscureSpawner")) spawner = BlockPos.of(tag.getLong("ObscureSpawner"));
        if (tag.contains("ObscureRoutine")) routine.load(tag.getCompound("ObscureRoutine"));
        applyData();
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (!level().isClientSide) {
            if (home == null) home = blockPosition();
            applyData();
            if (d.rutina_auto && !d.rutina.isBlank() && !routine.active()) routine.start(d.rutina);
        }
    }

    public static boolean hasTacz() { return ModList.get().isLoaded("tacz"); }
}
