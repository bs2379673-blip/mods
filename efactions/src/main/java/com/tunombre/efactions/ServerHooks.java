package com.tunombre.efactions;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lado servidor: detecta cada accion y manda la animacion a Epic Fight (se sincroniza a todos los jugadores cercanos).
 * Todo lo de otros mods (Survivors Arsenal, Cuffed) se hace por reflexion: si falta un mod, esa parte se desactiva.
 */
public class ServerHooks {
    private static final Logger LOG = LoggerFactory.getLogger("efactions");
    private static final float BLEND = 0.0F;

    enum Action { NONE, INJECT, BANDAGE, MEDKIT, SPRAY, GRENADE }

    private static final class St {
        Action act = Action.NONE;
        long holdAt = -1;
        boolean searching = false;
        boolean trapped = false;
        boolean pillory = false;
        boolean lockpicking = false;
        int prone = 0;               // 0 no, 1/2 arma larga quieto/moviendose, 3/4 arma chica quieto/moviendose
        double lx, lz;
        long lastMove = -100;
        long lastCrawl = -100;      // ultimo tick con pose de arrastre (anti parpadeo)
        long lastShot = 0;
        long reassertAt = -1;
        int reassertLeft = 0;
        long airSince = -1;
        net.minecraft.world.item.Item plateItem = null;
        boolean plateCd = false;
        boolean nail = false;
        String rk = null;            // arma a distancia en mano: "nail" | "bow" | "xbow" | null
        int rMag = -1;
        boolean rReload = false, rLoaded = false, rCharge = false;
        long rOvEnd = -1;
        int walkie = 0;              // 0 nada, 1 mano derecha, 2 mano izquierda
        long walkieAt = 0;           // ultimo tick en que se (re)impuso la animacion del walkie
        int walkieBurst = 0;         // reimposiciones rapidas pendientes tras encender
        boolean everProne = false;   // ya estuvo a tierra alguna vez (para la red de seguridad)
        long proneStopAt = -1;       // tick de la proxima orden de parar el arrastre tras levantarse
        int proneStopLeft = 0;
        long rHoldAt = 0;            // ultimo tick en que se (re)impuso la postura del arma a distancia
        int flash = 0;               // 0 nada, 1 brazo derecho, 2 brazo izquierdo
        String cuffPlaying = null;   // "front" | "back" | null
    }

    private final Map<UUID, St> states = new HashMap<>();
    private final Map<Class<?>, Action> classCache = new ConcurrentHashMap<>();

    // ---- reflexion sa_decor ----
    private Method mSearching;
    private boolean searchInit;
    // ---- reflexion Cuffed ----
    private Method mCuffCap, mArmsRestrained, mArmId, mArmFlag, mIsRestraintItem, mHeadId;
    // ---- reflexion sa_decor ganzuas ----
    private java.lang.reflect.Field fSessions;
    private boolean lockInit;
    private final Map<Class<?>, Boolean> flashCache = new ConcurrentHashMap<>();
    private boolean cuffInit;
    private java.lang.reflect.Field fShootTs;

    // ============================================================ utilidades de animacion
    private static void play(ServerPlayerPatch pp, AnimationManager.AnimationAccessor<StaticAnimation> a) {
        if (a != null) pp.playAnimationSynchronized(a, BLEND);
    }

    private static void stop(ServerPlayerPatch pp, AnimationManager.AnimationAccessor<StaticAnimation> a) {
        if (a != null) pp.stopPlaying(a);
    }

    private static AnimationManager.AnimationAccessor<StaticAnimation> accFor(Action a) {
        switch (a) {
            case INJECT: return EfActions.INJECT;
            case BANDAGE: return EfActions.BANDAGE;
            case MEDKIT: return EfActions.MEDKIT;
            case SPRAY: return EfActions.SPRAY;
            case GRENADE: return EfActions.GRENADE_PULL;
            default: return null;
        }
    }

    // ============================================================ clasificar items por clase (sin depender de ids)
    private Action classify(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Action.NONE;
        Class<?> cls = stack.getItem().getClass();
        Action cached = classCache.get(cls);
        if (cached != null) return cached;
        Action res = Action.NONE;
        for (Class<?> c = cls; c != null && c != Object.class && res == Action.NONE; c = c.getSuperclass()) {
            switch (c.getName()) {
                case "com.ogaba.sa_survival.item.common.AnimatedInjectorItem": res = Action.INJECT; break;
                case "com.ogaba.sa_survival.item.common.AnimatedBandageItem": res = Action.BANDAGE; break;
                case "com.ogaba.sa_survival.item.common.AnimatedFirstAidKitItem": res = Action.MEDKIT; break;
                case "com.ogaba.sa_survival.item.common.AnimatedFirstAidSprayItem": res = Action.SPRAY; break;
                case "com.ogaba.sacore.content.item.custom.ThrowableItem":
                case "com.ogaba.sa_combat.item.SpecialGrenadeItem":
                case "com.ogaba.sa_combat.item.MolotovItem": res = Action.GRENADE; break;
                default: break;
            }
        }
        classCache.put(cls, res);
        return res;
    }

    // ============================================================ sa_decor: ¿esta buscando loot?
    private boolean searching(ServerPlayer sp) {
        if (!searchInit) {
            searchInit = true;
            try {
                Class<?> c = Class.forName("com.ogaba.sa_decor.common.loot.DecorLootSearchHoldManager");
                mSearching = c.getMethod("isSearching", ServerPlayer.class);
            } catch (Throwable t) {
                LOG.info("sa_decor no encontrado: animacion de lootear desactivada ({})", t.toString());
            }
        }
        if (mSearching == null) return false;
        try {
            return (boolean) mSearching.invoke(null, sp);
        } catch (Throwable t) {
            mSearching = null;
            LOG.warn("Fallo al consultar sa_decor, desactivo la animacion de lootear", t);
            return false;
        }
    }

    // ============================================================ Cuffed
    private void initCuffed() {
        cuffInit = true;
        try {
            Class<?> caps = Class.forName("com.lazrproductions.cuffed.api.CuffedAPI$Capabilities");
            Class<?> iface = Class.forName("com.lazrproductions.cuffed.cap.base.IRestrainableCapability");
            Class<?> rapi = Class.forName("com.lazrproductions.cuffed.restraints.RestraintAPI");
            mCuffCap = caps.getMethod("getRestrainableCapability", Player.class);
            mArmsRestrained = iface.getMethod("armsRestrained");
            mArmId = iface.getMethod("getArmRestraintId");
            mArmFlag = rapi.getMethod("getArmAnimationFlagByKey", ResourceLocation.class);
            mHeadId = iface.getMethod("getHeadRestraintId");
            mIsRestraintItem = rapi.getMethod("isRestraintItem", ItemStack.class);
        } catch (Throwable t) {
            mCuffCap = null;
            mIsRestraintItem = null;
            LOG.info("Cuffed no encontrado: animaciones de esposas desactivadas ({})", t.toString());
        }
    }

    /** "front", "back" o null. */
    private String cuffedPose(ServerPlayer sp) {
        if (!cuffInit) initCuffed();
        if (mCuffCap == null) return null;
        try {
            Object cap = mCuffCap.invoke(null, sp);
            if (cap == null || !(boolean) mArmsRestrained.invoke(cap)) return null;
            Object id = mArmId.invoke(cap);
            if (id == null) return null;
            Object flag = mArmFlag.invoke(null, id);
            if (flag == null) return null;
            String n = flag.toString();
            if (n.contains("BEHIND") || n.contains("BACK")) return "back";
            if (n.contains("FRONT")) return "front";
            return null;
        } catch (Throwable t) {
            mCuffCap = null;
            LOG.warn("Fallo al consultar Cuffed, desactivo las animaciones de esposas", t);
            return null;
        }
    }

    private java.lang.reflect.Method mDetained;
    private boolean detainInit;

    /**
     * true si el jugador esta enganchado a un Pillory de Cuffed. Cuffed lo guarda como "detenido" en el jugador
     * (IDetainableEntity.getDetained(): -1 = libre, 0 = pillory; es lo mismo que mira su HumanoidModelMixin),
     * y como respaldo tambien miramos la restriccion de cabeza "pillory".
     */
    private boolean inPillory(ServerPlayer sp) {
        if (!detainInit) {
            detainInit = true;
            try {
                Class<?> c = Class.forName("com.lazrproductions.cuffed.entity.base.IDetainableEntity");
                mDetained = c.getMethod("getDetained");
            } catch (Throwable t) {
                LOG.info("Cuffed no encontrado: Pillory por 'detenido' desactivado ({})", t.toString());
            }
        }
        if (mDetained != null) {
            try {
                if (mDetained.getDeclaringClass().isInstance(sp) && ((Integer) mDetained.invoke(sp)) == 0) return true;
            } catch (Throwable t) {
                mDetained = null;
                LOG.warn("Fallo al consultar el estado de detenido de Cuffed", t);
            }
        }
        if (!cuffInit) initCuffed();
        if (mCuffCap == null || mHeadId == null) return false;
        try {
            Object cap = mCuffCap.invoke(null, sp);
            if (cap == null) return false;
            Object id = mHeadId.invoke(cap);
            return id != null && id.toString().contains("pillory");
        } catch (Throwable t) {
            mHeadId = null;
            return false;
        }
    }

    /** sa_decor: ¿tiene el jugador una sesion de ganzua (minijuego) abierta? */
    @SuppressWarnings("unchecked")
    private boolean lockpicking(ServerPlayer sp) {
        if (!lockInit) {
            lockInit = true;
            try {
                Class<?> c = Class.forName("com.ogaba.sa_decor.common.loot.DecorLockpickManager");
                fSessions = c.getDeclaredField("SESSIONS");
                fSessions.setAccessible(true);
            } catch (Throwable t) {
                LOG.info("sa_decor no encontrado: animacion de ganzuas desactivada ({})", t.toString());
            }
        }
        if (fSessions == null) return false;
        try {
            Object m = fSessions.get(null);
            return m instanceof Map && ((Map<Object, Object>) m).containsKey(sp.getUUID());
        } catch (Throwable t) {
            fSessions = null;
            LOG.warn("Fallo al consultar las ganzuas de sa_decor, desactivo su animacion", t);
            return false;
        }
    }

    // ============================================================ TACZ: cuerpo a tierra (arrastrarse)
    private boolean taczInit;
    private java.lang.reflect.Method mFromLiving, mDataHolder, mGetGunId, mGetCommonGunIndex, mGetType;
    private java.lang.reflect.Field fIsCrawling;
    private Class<?> cIGun;
    private final Map<String, Boolean> pistolCache = new ConcurrentHashMap<>();

    private void initTacz() {
        taczInit = true;
        try {
            Class<?> op = Class.forName("com.tacz.guns.api.entity.IGunOperator");
            mFromLiving = op.getMethod("fromLivingEntity", net.minecraft.world.entity.LivingEntity.class);
            mDataHolder = op.getMethod("getDataHolder");
            fIsCrawling = Class.forName("com.tacz.guns.entity.shooter.ShooterDataHolder").getField("isCrawling");
            cIGun = Class.forName("com.tacz.guns.api.item.IGun");
            mGetGunId = cIGun.getMethod("getGunId", ItemStack.class);
            Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
            mGetCommonGunIndex = api.getMethod("getCommonGunIndex", ResourceLocation.class);
            mGetType = Class.forName("com.tacz.guns.resource.index.CommonGunIndex").getMethod("getType");
        } catch (Throwable t) {
            mFromLiving = null;
            LOG.info("TACZ no encontrado: animacion de cuerpo a tierra desactivada ({})", t.toString());
        }
    }

    /** ¿esta el jugador arrastrandose (cuerpo a tierra de TACZ)? */
    private boolean crawling(ServerPlayer sp) {
        // La verdad es la pose: TACZ fuerza Pose.SWIMMING fuera del agua (y Epic Fight la toma por nado).
        // isCrawling solo se usa como apoyo; puede quedar desfasado (salto) o parpadear al disparar.
        if (sp.getPose() != net.minecraft.world.entity.Pose.SWIMMING) return false;
        if (sp.isInWater() || sp.isUnderWater() || sp.isFallFlying() || sp.isPassenger()) return false;
        return true;
    }

    private long shootTs(ServerPlayer sp) {
        if (!taczInit) initTacz();
        if (mFromLiving == null) return 0;
        try {
            Object op = mFromLiving.invoke(null, sp);
            if (op == null) return 0;
            Object holder = mDataHolder.invoke(op);
            if (holder == null) return 0;
            if (fShootTs == null) fShootTs = holder.getClass().getField("shootTimestamp");
            return fShootTs.getLong(holder);
        } catch (Throwable t) {
            return 0;
        }
    }

    /** true si el arma en la mano es una pistola (arma chica, se maneja con una mano); el resto, a dos manos. */
    private boolean holdingPistol(ServerPlayer sp) {
        if (mFromLiving == null) return false;
        try {
            ItemStack st = sp.getMainHandItem();
            if (st.isEmpty() || !cIGun.isInstance(st.getItem())) return false;
            Object id = mGetGunId.invoke(st.getItem(), st);
            if (id == null) return false;
            String key = id.toString();
            Boolean c = pistolCache.get(key);
            if (c != null) return c;
            boolean r = false;
            Object opt = mGetCommonGunIndex.invoke(null, id);
            if (opt instanceof java.util.Optional && ((java.util.Optional<?>) opt).isPresent()) {
                Object type = mGetType.invoke(((java.util.Optional<?>) opt).get());
                r = type != null && type.toString().equalsIgnoreCase("pistol");
            }
            pistolCache.put(key, r);
            return r;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void stopAllProne(ServerPlayerPatch pp) {
        stop(pp, EfActions.PRONE_RIFLE_IDLE);
        stop(pp, EfActions.PRONE_RIFLE_MOVE);
        stop(pp, EfActions.PRONE_PISTOL_IDLE);
        stop(pp, EfActions.PRONE_PISTOL_MOVE);
    }

    private static AnimationManager.AnimationAccessor<StaticAnimation> proneAcc(int v) {
        switch (v) {
            case 1: return EfActions.PRONE_RIFLE_IDLE;
            case 2: return EfActions.PRONE_RIFLE_MOVE;
            case 3: return EfActions.PRONE_PISTOL_IDLE;
            case 4: return EfActions.PRONE_PISTOL_MOVE;
            default: return null;
        }
    }

    private boolean isFlashlight(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Class<?> cls = stack.getItem().getClass();
        Boolean c = flashCache.get(cls);
        if (c != null) return c;
        boolean r = false;
        for (Class<?> k = cls; k != null && k != Object.class && !r; k = k.getSuperclass())
            r = k.getName().equals("com.ogaba.sa_survival.item.common.FlashlightItem");
        flashCache.put(cls, r);
        return r;
    }

    /** 0 = sin linterna en mano, 1 = en el brazo derecho, 2 = en el izquierdo. */
    private int flashSide(ServerPlayer sp) {
        boolean rightMain = sp.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT;
        if (isFlashlight(sp.getMainHandItem())) return rightMain ? 1 : 2;
        if (isFlashlight(sp.getOffhandItem())) return rightMain ? 2 : 1;
        return 0;
    }

    private boolean isApplyItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!cuffInit) initCuffed();
        if (stack.getItem().getClass().getName().equals("com.lazrproductions.cuffed.items.BandageItem")) return true;
        if (mIsRestraintItem == null) return false;
        try {
            return (boolean) mIsRestraintItem.invoke(null, stack);
        } catch (Throwable t) {
            mIsRestraintItem = null;
            return false;
        }
    }

    // ============================================================ eventos
    /** Esposar a otro jugador / ponerle una venda de Cuffed: el que lo hace extiende los brazos. */
    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract e) {
        if (e.getLevel().isClientSide() || e.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(e.getEntity() instanceof ServerPlayer sp) || !(e.getTarget() instanceof Player)) return;
        if (!isApplyItem(e.getItemStack())) return;
        ServerPlayerPatch pp = EpicFightCapabilities.getEntityPatch(sp, ServerPlayerPatch.class);
        if (pp != null) play(pp, EfActions.RESTRAIN);
    }

    private static boolean isItemClass(ItemStack stack, String suffix) {
        if (stack == null || stack.isEmpty()) return false;
        for (Class<?> c = stack.getItem().getClass(); c != null; c = c.getSuperclass())
            if (c.getName().endsWith(suffix)) return true;
        return false;
    }

    private static boolean walkieOn(ItemStack stack) {
        return isItemClass(stack, ".WalkieTalkieItem") && stack.hasTag() && stack.getTag().getBoolean("walkietalkie.activate");
    }

    private static boolean isPlate(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        for (Class<?> c = stack.getItem().getClass(); c != null; c = c.getSuperclass())
            if (c.getName().endsWith(".ArmorPlateItem")) return true;
        return false;
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        states.remove(e.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        long now = server.getTickCount();
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            ServerPlayerPatch pp = EpicFightCapabilities.getEntityPatch(sp, ServerPlayerPatch.class);
            if (pp == null) continue;
            try {
                tickPlayer(sp, pp, now);
            } catch (Throwable t) {
                LOG.warn("Error animando a " + sp.getGameProfile().getName(), t);
            }
        }
    }

    private void tickPlayer(ServerPlayer sp, ServerPlayerPatch pp, long now) {
        St st = states.computeIfAbsent(sp.getUUID(), k -> new St());

        // --- 1) uso de items (inyeccion, venda, botiquin, spray, granada)
        Action cur = sp.isUsingItem() ? classify(sp.getUseItem()) : Action.NONE;
        if (cur != st.act) {
            if (st.act != Action.NONE) endAction(pp, st);
            st.act = cur;
            if (cur != Action.NONE) {
                st.cuffPlaying = null;               // la nueva animacion tapa la de esposas
                play(pp, accFor(cur));
                st.holdAt = cur == Action.GRENADE ? now + 18 : -1;   // pull_pin dura 0.92 s
            }
        }
        if (st.act == Action.GRENADE && st.holdAt >= 0 && now >= st.holdAt) {
            st.holdAt = -1;
            play(pp, EfActions.GRENADE_HOLD);
        }

        // --- 2) lootear muebles de sa_decor
        boolean s = searching(sp);
        if (s != st.searching) {
            st.searching = s;
            if (s) {
                if (st.act == Action.NONE) { st.cuffPlaying = null; play(pp, EfActions.LOOT); }
            } else {
                stop(pp, EfActions.LOOT);
            }
        }

        // --- 2b) atrapado en trampa de oso (sa_decor): forcejeo en bucle
        boolean tr = sp.getPersistentData().getBoolean("SA_Decor_BearTrapTrapped");
        if (tr != st.trapped) {
            st.trapped = tr;
            LOG.info("efactions: {} {} la trampa de oso", sp.getGameProfile().getName(), tr ? "queda atrapado en" : "se libera de");
            if (tr) {
                if (st.cuffPlaying != null) stop(pp, "front".equals(st.cuffPlaying) ? EfActions.CUFFED_FRONT : EfActions.CUFFED_BACK);
                st.cuffPlaying = null;
                play(pp, EfActions.TRAPPED);
            } else stop(pp, EfActions.TRAPPED);
        }

        // --- 2c) Pillory de Cuffed: cuerpo inclinado, cabeza y manos en el tablon
        boolean pil = inPillory(sp);
        if (pil != st.pillory) {
            st.pillory = pil;
            LOG.info("efactions: {} {} el pillory", sp.getGameProfile().getName(), pil ? "queda en" : "sale del");
            if (pil) {
                if (st.cuffPlaying != null) stop(pp, "front".equals(st.cuffPlaying) ? EfActions.CUFFED_FRONT : EfActions.CUFFED_BACK);
                st.cuffPlaying = null;
                play(pp, EfActions.PILLORY);
            } else stop(pp, EfActions.PILLORY);
        }

        // --- 2d) ganzuas (minijuego de sa_decor)
        boolean lp = lockpicking(sp);
        if (lp != st.lockpicking) {
            st.lockpicking = lp;
            if (lp) {
                if (st.cuffPlaying != null) stop(pp, "front".equals(st.cuffPlaying) ? EfActions.CUFFED_FRONT : EfActions.CUFFED_BACK);
                st.cuffPlaying = null;
                play(pp, EfActions.LOCKPICK);
            } else stop(pp, EfActions.LOCKPICK);
        }

        // --- 2e) linterna en la mano: brazo separado del cuerpo, apuntando hacia delante
        int fl = flashSide(sp);
        if (fl != st.flash) {
            if (st.flash == 1) stop(pp, EfActions.FLASH_R);
            else if (st.flash == 2) stop(pp, EfActions.FLASH_L);
            st.flash = fl;
            if (fl == 1) play(pp, EfActions.FLASH_R);
            else if (fl == 2) play(pp, EfActions.FLASH_L);
        }

        // --- 2g) Armor Plate de Survivors Arsenal: al aplicarla (cooldown nuevo sobre la placa) se anima meterla al chaleco
        ItemStack mh = sp.getMainHandItem();
        if (isPlate(mh)) st.plateItem = mh.getItem();
        if (st.plateItem != null) {
            boolean cd = sp.getCooldowns().isOnCooldown(st.plateItem);
            if (cd && !st.plateCd) {
                st.cuffPlaying = null;
                play(pp, EfActions.ARMOR_PLATE);
            }
            st.plateCd = cd;
            if (!cd && !isPlate(mh)) st.plateItem = null;
        }

        // --- 2h) pistola de clavos (SA) en la mano: postura de arma a dos manos
        boolean freeNow = st.act == Action.NONE && !st.searching && !st.trapped && !st.pillory && !st.lockpicking && st.prone == 0
                && st.cuffPlaying == null && !sp.isSleeping() && !sp.isPassenger();
        ranged(sp, pp, st, freeNow, now);

        // --- 2i) walkie-talkie activado: se lleva al rostro como si hablara
        int wk = 0;
        if (!sp.isPassenger()) {
            if (walkieOn(sp.getMainHandItem())) wk = 1;
            else if (walkieOn(sp.getOffhandItem())) wk = 2;
        }
        if (wk != st.walkie) {
            if (st.walkie == 1) stop(pp, EfActions.WALKIE_R);
            else if (st.walkie == 2) stop(pp, EfActions.WALKIE_L);
            st.walkie = wk;
            if (wk == 1) play(pp, EfActions.WALKIE_R);
            else if (wk == 2) play(pp, EfActions.WALKIE_L);
            st.walkieAt = now;
            st.walkieBurst = wk != 0 ? 3 : 0;
        } else if (wk != 0) {
            // Al encender el walkie (clic derecho) Epic Fight puede reiniciar las animaciones de brazo justo despues:
            // se vuelve a imponer unos instantes despues y luego de vez en cuando (la pose es casi estatica, no se nota).
            long gap = st.walkieBurst > 0 ? 4 : 40;
            if (now - st.walkieAt >= gap) {
                if (st.walkieBurst > 0) st.walkieBurst--;
                st.walkieAt = now;
                play(pp, wk == 1 ? EfActions.WALKIE_R : EfActions.WALKIE_L);
            }
        }

        // --- 2f) cuerpo a tierra de TACZ: Epic Fight lo toma por nado; ponemos nuestra animacion de arrastre
        double dx = sp.getX() - st.lx, dz = sp.getZ() - st.lz;
        st.lx = sp.getX(); st.lz = sp.getZ();
        if (dx * dx + dz * dz > 0.0004) st.lastMove = now;
        int pv = 0;
        boolean cr = crawling(sp);
        if (sp.onGround()) st.airSince = -1; else if (st.airSince < 0) st.airSince = now;
        boolean jumped = st.airSince >= 0 && now - st.airSince >= 3;     // salto/caida: fuera del arrastre
        if (cr && !jumped) st.lastCrawl = now;
        // aguanta 4 ticks sin pose para no cortar la animacion por un parpadeo (disparo con pistola)
        boolean keep = (cr && !jumped) || (!jumped && !sp.isSprinting() && st.prone != 0 && now - st.lastCrawl < 4);
        if (keep) {
            boolean mv = now - st.lastMove < 5;
            pv = holdingPistol(sp) ? (mv ? 4 : 3) : (mv ? 2 : 1);
        }
        // al disparar, Epic Fight puede pisar la animacion: se vuelve a imponer un instante despues
        if (st.prone != 0 && pv != 0) {
            long ts = shootTs(sp);
            if (ts != st.lastShot) { st.lastShot = ts; st.reassertAt = now + 2; st.reassertLeft = 2; }
            if (st.reassertLeft > 0 && now >= st.reassertAt) {
                st.reassertLeft--;
                st.reassertAt = now + 3;
                if (st.prone == pv) play(pp, proneAcc(pv));
            }
        }
        if (pv != st.prone) {
            if (pv == 0) {
                stopAllProne(pp);
                st.proneStopAt = now + 3;      // el stop de Epic Fight solo vale si la animacion actual ES la de arrastre:
                st.proneStopLeft = 3;          // se repite unos ticks por si algo (disparo, cambio de item) la habia pisado
            } else {
                st.everProne = true;
                if (st.prone == 0) {
                    if (st.cuffPlaying != null) stop(pp, "front".equals(st.cuffPlaying) ? EfActions.CUFFED_FRONT : EfActions.CUFFED_BACK);
                    st.cuffPlaying = null;
                }
                play(pp, proneAcc(pv));
            }
            st.prone = pv;
        }
        if (st.prone == 0 && st.everProne) {
            if (st.proneStopLeft > 0 && now >= st.proneStopAt) {
                st.proneStopLeft--;
                st.proneStopAt = now + 5;
                stopAllProne(pp);
            } else if (now % 20 == 0 && !crawling(sp)) {
                stopAllProne(pp);              // red de seguridad: de pie y sin arrastre nunca debe quedar la animacion
            }
        }

        // --- 3) esposado (Cuffed): pose en bucle mientras no haya otra accion
        boolean busy = st.act != Action.NONE || st.searching || st.trapped || st.pillory || st.lockpicking || st.prone != 0;
        String want = busy ? st.cuffPlaying : cuffedPose(sp);
        if (!busy && !java.util.Objects.equals(want, st.cuffPlaying)) {
            if (st.cuffPlaying != null) stop(pp, "front".equals(st.cuffPlaying) ? EfActions.CUFFED_FRONT : EfActions.CUFFED_BACK);
            if (want != null) play(pp, "front".equals(want) ? EfActions.CUFFED_FRONT : EfActions.CUFFED_BACK);
            st.cuffPlaying = want;
        }
    }


    // ============================================================ armas a distancia de SA (pistola de clavos, compound bow, ballestas)
    private java.lang.reflect.Field fBowStates, fBowCharge;
    private boolean bowInit;

    private boolean bowCharging(ServerPlayer sp) {
        try {
            if (!bowInit) {
                bowInit = true;
                Class<?> c = Class.forName("com.ogaba.sa_combat.combat.weapon.CompoundBowServerState");
                fBowStates = c.getDeclaredField("STATES");
                fBowStates.setAccessible(true);
                Class<?> sc = Class.forName("com.ogaba.sa_combat.combat.weapon.CompoundBowServerState$State");
                fBowCharge = sc.getDeclaredField("chargeStartTick");
                fBowCharge.setAccessible(true);
            }
            if (fBowStates == null) return false;
            Object stt = ((Map<?, ?>) fBowStates.get(null)).get(sp.getUUID());
            return stt != null && fBowCharge.getLong(stt) >= 0L;
        } catch (Throwable t) {
            fBowStates = null;
            return false;
        }
    }

    private static AnimationManager.AnimationAccessor<StaticAnimation> holdFor(String k) {
        if (k == null) return null;
        switch (k) {
            case "nail": return EfActions.NAIL_HOLD;
            case "bow": return EfActions.BOW_HOLD;
            default: return EfActions.XBOW_HOLD;
        }
    }

    private void ranged(ServerPlayer sp, ServerPlayerPatch pp, St st, boolean freeNow, long now) {
        ItemStack mh = sp.getMainHandItem();
        String kind = null;
        if (freeNow) {
            if (isItemClass(mh, ".NailGunItem")) kind = "nail";
            else if (isItemClass(mh, ".CompoundBowItem")) kind = "bow";
            else if (isItemClass(mh, ".TacticalCrossbowItem")) kind = "xbow";
        }
        if (!java.util.Objects.equals(kind, st.rk)) {
            AnimationManager.AnimationAccessor<StaticAnimation> oldHold = holdFor(st.rk);
            if (oldHold != null) {
                stop(pp, oldHold);
                stop(pp, EfActions.NAIL_SHOOT); stop(pp, EfActions.NAIL_RELOAD);
                stop(pp, EfActions.BOW_DRAW); stop(pp, EfActions.BOW_SHOOT);
                stop(pp, EfActions.XBOW_SHOOT); stop(pp, EfActions.XBOW_RELOAD);
            }
            st.rk = kind;
            st.rHoldAt = now;
            st.rMag = -1; st.rReload = false; st.rLoaded = false; st.rCharge = false; st.rOvEnd = -1;
            st.nail = kind != null;
            if (kind != null) play(pp, holdFor(kind));
        }
        if (kind == null) return;
        CompoundTag tag = mh.getTag();
        if (st.rOvEnd < 0 && !st.rReload && !st.rCharge && now - st.rHoldAt >= 60) {   // postura de arma: se reimpone cada 3 s (por si algo la piso)
            st.rHoldAt = now;
            play(pp, holdFor(kind));
        }
        if (st.rOvEnd >= 0 && now >= st.rOvEnd) {          // termino un disparo: vuelve la postura de sostener
            st.rOvEnd = -1;
            play(pp, holdFor(kind));
        }
        switch (kind) {
            case "nail": {
                boolean rl = tag != null && tag.getBoolean("SACombatNailGunReloading");
                int mag = tag != null ? tag.getInt("SACombatNailGunMagazine") : 0;
                if (rl && !st.rReload) { play(pp, EfActions.NAIL_RELOAD); st.rOvEnd = -1; }
                else if (!rl && st.rReload) { stop(pp, EfActions.NAIL_RELOAD); play(pp, holdFor(kind)); }
                else if (!rl && st.rMag >= 0 && mag < st.rMag) { play(pp, EfActions.NAIL_SHOOT); st.rOvEnd = now + 6; }
                st.rReload = rl; st.rMag = mag;
                break;
            }
            case "xbow": {
                boolean loading = tag != null && tag.getBoolean("SACombatCrossbowLoading");
                boolean loaded = tag != null && tag.getBoolean("SACombatCrossbowLoaded");
                if (loading && !st.rReload) { play(pp, EfActions.XBOW_RELOAD); st.rOvEnd = -1; }
                else if (!loading && st.rReload) { stop(pp, EfActions.XBOW_RELOAD); play(pp, holdFor(kind)); }
                else if (!loading && st.rLoaded && !loaded) { play(pp, EfActions.XBOW_SHOOT); st.rOvEnd = now + 8; }
                st.rReload = loading; st.rLoaded = loaded;
                break;
            }
            default: {
                boolean ch = bowCharging(sp);
                if (ch && !st.rCharge) { play(pp, EfActions.BOW_DRAW); st.rOvEnd = -1; }
                else if (!ch && st.rCharge) { stop(pp, EfActions.BOW_DRAW); play(pp, EfActions.BOW_SHOOT); st.rOvEnd = now + 11; }
                st.rCharge = ch;
            }
        }
    }

    private void endAction(ServerPlayerPatch pp, St st) {
        if (st.act == Action.GRENADE) {
            stop(pp, EfActions.GRENADE_PULL);
            stop(pp, EfActions.GRENADE_HOLD);
            play(pp, EfActions.GRENADE_THROW);
        } else {
            stop(pp, accFor(st.act));
        }
        st.holdAt = -1;
    }
}
