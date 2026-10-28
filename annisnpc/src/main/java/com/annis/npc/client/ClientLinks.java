package com.annis.npc.client;

import com.annis.npc.ModEntities;
import com.annis.npc.entity.AnnisNpcEntity;
import com.annis.npc.link.EntityAnimPayload;
import com.annis.npc.link.EntityLookPayload;
import com.annis.npc.mixin.LimbAnimatorAccessor;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;

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
    private static final Map<Integer, NbtCompound> LOOKS = new HashMap<>();
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
    public static void tick(MinecraftClient client) {
        if (client.world == null) {
            return;
        }
        Iterator<Map.Entry<Integer, Anim>> it = ANIMS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Anim> e = it.next();
            Entity ent = client.world.getEntityById(e.getKey());
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
            Entity ent = client.world.getEntityById(e.getKey());
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

    private static AnnisNpcEntity proxyFor(LivingEntity e, NbtCompound look) {
        AnnisNpcEntity proxy = PROXIES.get(e.getId());
        if (proxy == null || proxy.getWorld() != e.getWorld()) {
            proxy = ModEntities.NPC.create(e.getWorld());
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
        dst.setPosition(src.getX(), src.getY(), src.getZ());
        dst.prevX = src.prevX;
        dst.prevY = src.prevY;
        dst.prevZ = src.prevZ;
        dst.lastRenderX = src.lastRenderX;
        dst.lastRenderY = src.lastRenderY;
        dst.lastRenderZ = src.lastRenderZ;
        dst.setYaw(src.getYaw());
        dst.prevYaw = src.prevYaw;
        dst.setPitch(src.getPitch());
        dst.prevPitch = src.prevPitch;
        dst.bodyYaw = src.bodyYaw;
        dst.prevBodyYaw = src.prevBodyYaw;
        dst.headYaw = src.headYaw;
        dst.prevHeadYaw = src.prevHeadYaw;
        dst.age = src.age;
        dst.hurtTime = src.hurtTime;
        dst.deathTime = src.deathTime;
        dst.handSwingProgress = src.handSwingProgress;
        dst.lastHandSwingProgress = src.lastHandSwingProgress;
        dst.handSwinging = src.handSwinging;
        dst.setOnGround(src.isOnGround());
        dst.setSneaking(src.isSneaking());
        dst.setInvisible(src.isInvisible());
        dst.setPose(src.getPose());
        if (src.isSleeping() && src.getSleepingPosition().isPresent()) {
            dst.setSleepingPosition(src.getSleepingPosition().get());
        } else if (dst.isSleeping()) {
            dst.clearSleepingPosition();
        }
        LimbAnimatorAccessor from = (LimbAnimatorAccessor) src.limbAnimator;
        LimbAnimatorAccessor to = (LimbAnimatorAccessor) dst.limbAnimator;
        to.annisnpc$setPrevSpeed(from.annisnpc$getPrevSpeed());
        to.annisnpc$setSpeed(from.annisnpc$getSpeed());
        to.annisnpc$setPos(from.annisnpc$getPos());
        dst.setCustomName(src.getCustomName());
        dst.setCustomNameVisible(src.isCustomNameVisible());
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.ANIMAL_ARMOR) {
                continue;
            }
            dst.equipStack(slot, src.getEquippedStack(slot));
        }
    }

    /**
     * Desde el mixin del render de entidades: si la entidad tiene aspecto Annis la dibuja el NPC
     * fantasma y devuelve true (el render original se cancela).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean renderAsNpc(LivingEntity e, float yaw, float tickDelta, MatrixStack matrices,
                                      VertexConsumerProvider vcp, int light) {
        if (rendering || e instanceof AnnisNpcEntity) {
            return false;
        }
        NbtCompound look = LOOKS.get(e.getId());
        if (look == null) {
            return false;
        }
        AnnisNpcEntity proxy = proxyFor(e, look);
        if (proxy == null) {
            return false;
        }
        copyState(e, proxy);
        EntityRenderer renderer = MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(proxy);
        rendering = true;
        matrices.push();
        try {
            if (MCA) {
                float[] s = com.annis.npc.mca.McaClientHooks.scale(e);
                if (s != null) {
                    matrices.scale(s[0], s[1], s[0]);
                }
            }
            renderer.render(proxy, yaw, tickDelta, matrices, vcp, light);
        } finally {
            matrices.pop();
            rendering = false;
        }
        return true;
    }
}
