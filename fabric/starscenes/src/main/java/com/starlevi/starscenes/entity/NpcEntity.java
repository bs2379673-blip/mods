package com.starlevi.starscenes.entity;

import com.starlevi.starscenes.api.SceneActor;
import com.starlevi.starscenes.api.StarScenesAPI;
import com.starlevi.starscenes.net.Net;
import com.starlevi.starscenes.talk.Dialogues;
import com.starlevi.starscenes.server.SceneLibrary;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityAttachments;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Un NPC de StarScenes: no se mueve solo, no recibe daño y no empuja. Lo que hace (reposar, mirarte,
 * reaccionar al verte, su animacion al tocarlo) lo dibuja cada cliente con su modelo de Blockbench.
 */
public class NpcEntity extends Entity implements SceneActor {
    public static final EntityDataAccessor<String> MODEL = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.STRING);
    public static final EntityDataAccessor<String> SKIN = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.STRING);
    /** Variante de animaciones (idle1/seeplayer1/interact1...); 0 = sin numero o la primera que haya. */
    public static final EntityDataAccessor<Integer> STYLE = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Boolean> LOOK = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.BOOLEAN);
    /** A cuantos bloques reacciona al verte. */
    public static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.FLOAT);
    /** Caja de golpe y altura del nombre (en bloques, sin la escala), sacadas del modelo. */
    public static final EntityDataAccessor<Float> HIT_W = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Float> HIT_H = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<Float> TAG_Y = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<ItemStack> MAIN = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.ITEM_STACK);
    public static final EntityDataAccessor<ItemStack> OFF = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.ITEM_STACK);
    public static final EntityDataAccessor<String> VOICE = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.STRING);
    public static final EntityDataAccessor<Float> VOICE_PITCH = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.FLOAT);
    public static final EntityDataAccessor<ItemStack> HEAD = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.ITEM_STACK);

    /** Dialogo que abre al tocarlo ("" = solo hace su animacion). Solo lo sabe el servidor. */
    private String dialogue = "";

    public NpcEntity(EntityType<? extends NpcEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(MODEL, "");
        b.define(SKIN, "");
        b.define(STYLE, 1);
        b.define(SCALE, 1f);
        b.define(LOOK, true);
        b.define(RADIUS, 5f);
        b.define(HIT_W, 0.6f);
        b.define(HIT_H, 1.8f);
        b.define(TAG_Y, 2.1f);
        b.define(MAIN, ItemStack.EMPTY);
        b.define(OFF, ItemStack.EMPTY);
        b.define(HEAD, ItemStack.EMPTY);
        b.define(VOICE, "simple");
        b.define(VOICE_PITCH, 1f);
    }

    public String model() {
        return this.entityData.get(MODEL);
    }

    public String skin() {
        return this.entityData.get(SKIN);
    }

    public int style() {
        return this.entityData.get(STYLE);
    }

    public float scale() {
        return this.entityData.get(SCALE);
    }

    public boolean look() {
        return this.entityData.get(LOOK);
    }

    public float radius() {
        return this.entityData.get(RADIUS);
    }

    public String dialogue() {
        return this.dialogue;
    }

    public void setDialogue(String id) {
        this.dialogue = id == null ? "" : id;
    }

    @Override
    public String sceneModel() {
        return model();
    }

    @Override
    public String sceneSkin() {
        return skin();
    }

    @Override
    public int sceneStyle() {
        return style();
    }

    @Override
    public float sceneScale() {
        return scale();
    }

    @Override
    public boolean sceneLooks() {
        return look();
    }

    @Override
    public float sceneRadius() {
        return radius();
    }

    @Override
    public String sceneVoice() {
        return this.entityData.get(VOICE);
    }

    @Override
    public float sceneVoicePitch() {
        return this.entityData.get(VOICE_PITCH);
    }

    @Override
    public ItemStack sceneItem(int slot) {
        return item(slot == MAIN_HAND ? MAIN : slot == OFF_HAND ? OFF : HEAD);
    }

    public ItemStack item(EntityDataAccessor<ItemStack> slot) {
        return this.entityData.get(slot);
    }

    /** Cambia el modelo y toma del modelo su caja de golpe y la altura del nombre. */
    public void setModel(String id) {
        this.entityData.set(MODEL, id);
        SceneLibrary.Info info = SceneLibrary.info(id);
        if (info != null) {
            this.entityData.set(HIT_W, info.hitWidth());
            this.entityData.set(HIT_H, info.hitHeight());
            this.entityData.set(TAG_Y, info.tagY());
        }
    }

    public void set(EntityDataAccessor<?> key, Object value) {
        @SuppressWarnings("unchecked")
        EntityDataAccessor<Object> k = (EntityDataAccessor<Object>) key;
        this.entityData.set(k, value);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key == HIT_W || key == HIT_H || key == SCALE || key == TAG_Y) {
            refreshDimensions();
        }
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        float s = scale();
        return EntityDimensions.scalable(this.entityData.get(HIT_W) * s, this.entityData.get(HIT_H) * s)
                .withAttachments(EntityAttachments.builder().attach(EntityAttachment.NAME_TAG, 0, this.entityData.get(TAG_Y) * s - 0.3f, 0));
    }

    @Override
    public void tick() {
        super.tick();
        // no se mueve solo: queda donde lo pusieron
        this.setDeltaMovement(0, 0, 0);
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer sp) {
            if (sp.isShiftKeyDown() && sp.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
                // admin agachado: el menu para editarlo
                ServerPlayNetworking.send(sp, Net.OpenMenu.of(this));
            } else if (!StarScenesAPI.interacted(sp, this)) {
                Net.Play msg = new Net.Play(this.getId(), "interact");
                for (ServerPlayer p : PlayerLookup.tracking(this)) {
                    ServerPlayNetworking.send(p, msg);
                }
                if (!PlayerLookup.tracking(this).contains(sp)) {
                    ServerPlayNetworking.send(sp, msg);
                }
                if (!this.dialogue.isEmpty() && !Dialogues.talking(sp)) {
                    Dialogues.open(sp, this, this.dialogue);
                }
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        this.entityData.set(MODEL, in.getStringOr("modelo", ""));
        this.entityData.set(SKIN, in.getStringOr("skin", ""));
        this.entityData.set(STYLE, in.getIntOr("estilo", 1));
        this.entityData.set(SCALE, in.getFloatOr("escala", 1f));
        this.entityData.set(LOOK, in.getBooleanOr("mirar", true));
        this.entityData.set(RADIUS, in.getFloatOr("radio", 5f));
        this.entityData.set(HIT_W, in.getFloatOr("caja_ancho", 0.6f));
        this.entityData.set(HIT_H, in.getFloatOr("caja_alto", 1.8f));
        this.entityData.set(TAG_Y, in.getFloatOr("nombre_alto", 2.1f));
        this.entityData.set(MAIN, in.read("item_mano", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
        this.entityData.set(OFF, in.read("item_otra_mano", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
        this.entityData.set(HEAD, in.read("item_cabeza", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
        this.dialogue = in.getStringOr("dialogo", "");
        this.entityData.set(VOICE, in.getStringOr("voz", "simple"));
        this.entityData.set(VOICE_PITCH, in.getFloatOr("tono", 1f));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        out.putString("modelo", model());
        out.putString("skin", skin());
        out.putInt("estilo", style());
        out.putFloat("escala", scale());
        out.putBoolean("mirar", look());
        out.putFloat("radio", radius());
        out.putFloat("caja_ancho", this.entityData.get(HIT_W));
        out.putFloat("caja_alto", this.entityData.get(HIT_H));
        out.putFloat("nombre_alto", this.entityData.get(TAG_Y));
        out.store("item_mano", ItemStack.OPTIONAL_CODEC, item(MAIN));
        out.store("item_otra_mano", ItemStack.OPTIONAL_CODEC, item(OFF));
        out.store("item_cabeza", ItemStack.OPTIONAL_CODEC, item(HEAD));
        out.putString("dialogo", this.dialogue);
        out.putString("voz", sceneVoice());
        out.putFloat("tono", sceneVoicePitch());
    }
}
