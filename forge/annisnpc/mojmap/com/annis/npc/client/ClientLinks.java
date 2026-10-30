package com.annis.npc.client;

import com.annis.npc.ModEntities;
import com.annis.npc.entity.AnnisNpcEntity;
import com.annis.npc.link.EntityAnimPayload;
import com.annis.npc.link.EntityLookPayload;
import com.annis.npc.mixin.LimbAnimatorAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Cliente: animaciones de otras entidades y entidades con "aspecto Annis".
 * Una entidad con aspecto se dibuja con un NPC fantasma (no existe en el mundo) que copia su
 * posicion, giro, pasos y pose, asi tiene skin, modelo articulado, accesorios y animaciones.
 */
public final class ClientLinks {
    public record Anim(String name, int start, int serial) {
    }

    private static final Map<Integer, Anim> ANIMS = new HashMap<>();
    private static final Map<Integer, CompoundTag> LOOKS = new HashMap<>();
    private static final Map<Integer, AnnisNpcEntity> PROXIES = new HashMap<>();
    private static final boolean MCA = FabricLoader.getInstance().isModLoaded("mca");
    private static boolean rendering;

    private ClientLinks() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(EntityAnimPayload.ID, (p, ctx) -> {
            if (p.name().isEmpty()) {
                ANIMS.put(p.entityId(), new Anim("", p.start(), p.serial()));
            } else {
                ANIMS.put(p.entityId(), new Anim(p.name(), p.start(), p.serial()));
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(EntityLookPayload.ID, (p, ctx) -> {
            if (p.look().isEmpty()) {
                LOOKS.remove(p.entityId());
                PROXIES.remove(p.entityId());
            } else {
                LOOKS.put(p.entityId(), p.look());
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> {
            ANIMS.clear();
            LOOKS.clear();
            PROXIES.clear();
        });
    }

    public static Anim anim(Entity e) {
        return ANIMS.get(e.getId());
    }

    public static boolean hasLook(Entity e) {
        return LOOKS.containsKey(e.getId());
    }

    /** Cada tick de cliente: avanza animaciones de entidades ajenas y de los NPC fantasma. */
    public static void tick(Minecraft client) {
        if (client.level == null) {
            return;
        }
        Iterator<Map.Entry<Integer, Anim>> it = ANIMS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Anim> e = it.next();
            Entity ent = client.level.getEntity(e.getKey());
            if (!(ent instanceof LivingEntity living) || ent instanceof AnnisNpcEntity) {
                if (ent == null && e.getValue().name().isEmpty()) {
                    it.remove();
                }
                continue;
            }
            Anim a = e.getValue();
            if (!LOOKS.containsKey(e.getKey())) {
                NpcAnimState.ofGeneric(living, a.serial() - 1, a.name()).tick(a.name(), a.serial(), a.start());
            }
        }
        Iterator<Map.Entry<Integer, AnnisNpcEntity>> pit = PROXIES.entrySet().iterator();
        while (pit.hasNext()) {
            Map.Entry<Integer, AnnisNpcEntity> e = pit.next();
            Entity ent = client.level.getEntity(e.getKey());
            if (ent == null || ent.isRemoved()) {
                pit.remove();
                continue;
            }
            AnnisNpcEntity proxy = e.getValue();
            Anim a = ANIMS.get(e.getKey());
            if (a != null) {
                proxy.clientSetAnim(a.name(), a.start(), a.serial());
            }
            NpcAnimState.of(proxy).tick(proxy);
            FiguraBridge.sync(proxy);
        }
    }

    private static AnnisNpcEntity proxyFor(LivingEntity e, CompoundTag look) {
        AnnisNpcEntity proxy = PROXIES.get(e.getId());
        if (proxy == null || proxy.level() != e.level()) {
            proxy = ModEntities.NPC.create(e.level());
            if (proxy == null) {
                return null;
            }
            PROXIES.put(e.getId(), proxy);
        }
        proxy.setSkin(look.getString("Skin"));
        proxy.setSlim(look.getBoolean("Slim"));
        proxy.setJointed(look.getBoolean("Jointed"));
        proxy.setAccessories(look.getString("Accessories"));
        proxy.setFigura(look.getString("Figura"));
        return proxy;
    }

    /** Copia el estado visible de la entidad al NPC fantasma. */
    private static void copyState(LivingEntity src, AnnisNpcEntity dst) {
        dst.setPos(src.getX(), src.getY(), src.getZ());
        dst.xo = src.xo;
        dst.yo = src.yo;
        dst.zo = src.zo;
        dst.xOld = src.xOld;
        dst.yOld = src.yOld;
        dst.zOld = src.zOld;
        dst.setYRot(src.getYRot());
        dst.yRotO = src.yRotO;
        dst.setXRot(src.getXRot());
        dst.xRotO = src.xRotO;
        dst.yBodyRot = src.yBodyRot;
        dst.yBodyRotO = src.yBodyRotO;
        dst.yHeadRot = src.yHeadRot;
        dst.yHeadRotO = src.yHeadRotO;
        dst.tickCount = src.tickCount;
        dst.hurtTime = src.hurtTime;
        dst.deathTime = src.deathTime;
        dst.attackAnim = src.attackAnim;
        dst.oAttackAnim = src.oAttackAnim;
        dst.swinging = src.swinging;
        dst.setOnGround(src.onGround());
        dst.setShiftKeyDown(src.isShiftKeyDown());
        dst.setInvisible(src.isInvisible());
        dst.setPose(src.getPose());
        if (src.isSleeping() && src.getSleepingPos().isPresent()) {
            dst.setSleepingPos(src.getSleepingPos().get());
        } else if (dst.isSleeping()) {
            dst.clearSleepingPos();
        }
        LimbAnimatorAccessor from = (LimbAnimatorAccessor) src.walkAnimation;
        LimbAnimatorAccessor to = (LimbAnimatorAccessor) dst.walkAnimation;
        to.annisnpc$setPrevSpeed(from.annisnpc$getPrevSpeed());
        to.annisnpc$setSpeed(from.annisnpc$getSpeed());
        to.annisnpc$setPos(from.annisnpc$getPos());
        dst.setCustomName(src.getCustomName());
        dst.setCustomNameVisible(src.isCustomNameVisible());
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.ANIMAL_ARMOR) {
                continue;
            }
            dst.setItemSlot(slot, src.getItemBySlot(slot));
        }
    }

    /**
     * Desde el mixin del render de entidades: si la entidad tiene aspecto Annis la dibuja el NPC
     * fantasma y devuelve true (el render original se cancela).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean renderAsNpc(LivingEntity e, float yaw, float tickDelta, PoseStack matrices,
                                      MultiBufferSource vcp, int light) {
        if (rendering || e instanceof AnnisNpcEntity) {
            return false;
        }
        CompoundTag look = LOOKS.get(e.getId());
        if (look == null) {
            return false;
        }
        AnnisNpcEntity proxy = proxyFor(e, look);
        if (proxy == null) {
            return false;
        }
        copyState(e, proxy);
        EntityRenderer renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(proxy);
        rendering = true;
        matrices.pushPose();
        try {
            if (MCA) {
                float[] s = com.annis.npc.mca.McaClientHooks.scale(e);
                if (s != null) {
                    matrices.scale(s[0], s[1], s[0]);
                }
            }
            renderer.render(proxy, yaw, tickDelta, matrices, vcp, light);
        } finally {
            matrices.popPose();
            rendering = false;
        }
        return true;
    }
}
