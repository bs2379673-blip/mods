package com.tunombre.ragdollfix;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Mientras un jugador esta en modo ragdoll (Ragdollified) no puede pegar, disparar ni usar items.
 * Todo se consulta por reflexion, asi que no hay dependencia dura con Ragdollified, Epic Fight ni TACZ.
 */
@Mod(RagdollFix.MODID)
public class RagdollFix {
    public static final String MODID = "ragdollfix";
    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private static Method serverIsLive;
    private static Field clientRagdolls;
    private static boolean serverTried, clientTried;

    public RagdollFix() {
        MinecraftForge.EVENT_BUS.register(this);
        if (FMLEnvironment.dist == Dist.CLIENT) MinecraftForge.EVENT_BUS.register(new ClientSide());
        hookTacz();
    }

    // ---------------------------------------------------------------- deteccion
    static boolean serverRagdolled(Entity e) {
        if (!(e instanceof Player p)) return false;
        try {
            if (!serverTried) {
                serverTried = true;
                serverIsLive = Class.forName("com.raiiiden.ragdollified.server.ServerRagdollSyncManager").getMethod("isLive", int.class);
            }
            return serverIsLive != null && (boolean) serverIsLive.invoke(null, p.getId());
        } catch (Throwable t) { serverIsLive = null; return false; }
    }

    @SuppressWarnings("unchecked")
    static boolean clientRagdolled(Player p) {
        if (p == null) return false;
        try {
            if (!clientTried) {
                clientTried = true;
                clientRagdolls = Class.forName("com.raiiiden.ragdollified.client.ClientRagdollManager").getDeclaredField("ragdolls");
                clientRagdolls.setAccessible(true);
            }
            return clientRagdolls != null && ((Map<Integer, ?>) clientRagdolls.get(null)).containsKey(p.getId());
        } catch (Throwable t) { clientRagdolls = null; return false; }
    }

    static boolean ragdolled(Entity e) {
        if (e == null) return false;
        return e.level().isClientSide ? (e instanceof Player p && clientRagdolled(p)) : serverRagdolled(e);
    }

    // ---------------------------------------------------------------- servidor (y cliente local): cancelar acciones
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttack(AttackEntityEvent e) { if (ragdolled(e.getEntity())) e.setCanceled(true); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingAttack(LivingAttackEvent e) { if (ragdolled(e.getSource().getEntity())) e.setCanceled(true); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingHurt(LivingHurtEvent e) { if (ragdolled(e.getSource().getEntity())) e.setCanceled(true); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLeftBlock(PlayerInteractEvent.LeftClickBlock e) { if (ragdolled(e.getEntity())) e.setCanceled(true); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightBlock(PlayerInteractEvent.RightClickBlock e) { if (ragdolled(e.getEntity())) e.setCanceled(true); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightItem(PlayerInteractEvent.RightClickItem e) { if (ragdolled(e.getEntity())) e.setCanceled(true); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityInteract(PlayerInteractEvent.EntityInteract e) { if (ragdolled(e.getEntity())) e.setCanceled(true); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEmptyClick(PlayerInteractEvent.RightClickEmpty e) { if (ragdolled(e.getEntity())) e.setCanceled(true); }

    // ---------------------------------------------------------------- TACZ: disparo (si esta instalado)
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void hookTacz() {
        for (String cn : new String[]{"com.tacz.guns.api.event.common.GunShootEvent", "com.tacz.guns.api.event.common.GunFireEvent"}) {
            try {
                Class<? extends Event> cls = (Class<? extends Event>) Class.forName(cn);
                Method shooter = cls.getMethod("getShooter");
                MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, true, (Class) cls, (java.util.function.Consumer<Event>) ev -> {
                    try {
                        if (ragdolled((Entity) shooter.invoke(ev)) && ev.isCancelable()) ev.setCanceled(true);
                    } catch (Throwable ignored) { }
                });
                LOGGER.info("RagdollFix: enganchado {}", cn);
            } catch (Throwable ignored) { }
        }
    }

    // ---------------------------------------------------------------- cliente: soltar teclas de ataque
    public static final class ClientSide {
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onTick(TickEvent.ClientTickEvent e) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || !clientRagdolled(mc.player)) return;
            KeyMapping[] ks = {mc.options.keyAttack, mc.options.keyUse, mc.options.keyPickItem, epicAttack()};
            for (KeyMapping k : ks) {
                if (k == null) continue;
                while (k.consumeClick()) { }
                k.setDown(false);
            }
        }

        private static KeyMapping epic;
        private static boolean epicTried;

        private static KeyMapping epicAttack() {
            if (!epicTried) {
                epicTried = true;
                try { epic = (KeyMapping) Class.forName("yesman.epicfight.client.input.EpicFightKeyMappings").getField("ATTACK").get(null); } catch (Throwable ignored) { }
            }
            return epic;
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onMouse(InputEvent.MouseButton.Pre e) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null && mc.player != null && clientRagdolled(mc.player) && e.getAction() == 1) e.setCanceled(true);
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onInteractKey(InputEvent.InteractionKeyMappingTriggered e) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && clientRagdolled(mc.player)) { e.setCanceled(true); e.setSwingHand(false); }
        }
    }
}
