package com.tunombre.cruorfight;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.ForgeConfigSpec;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;

/** Acceso por reflexion a las partes internas de Cruor (sus clases son privadas del paquete). */
final class CruorAccess {
    record SkinInfo(ResourceLocation location, int copies) { }

    private static final String BODY = "com.raiiiden.cruor.client.body.";
    private static boolean tried, ok;
    private static Object bodies, coverSkin;
    private static Method mBody, mNow, mSkinFor, mPending, mSettle, mFade, mUpdate, mMaskGet, mMaskUnion, mBegin, mFinish, mInWorld, mPaint, mPassBegin, mPassEnd;
    private static Field fWounds, fLayers, fSkin, fBlood, fLayout, fPainted, fLimb, fTries, fArmourChecked, fLoc, fCopies, fCount, fPainterCopies;
    private static Class<?> targetIface;
    private static ForgeConfigSpec.ConfigValue<?> cfgWounds, cfgDry;

    private CruorAccess() { }

    private static Method method(Class<?> c, String name, int params) throws Exception {
        for (Method m : c.getDeclaredMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == params) { m.setAccessible(true); return m; }
        }
        throw new NoSuchMethodException(c.getName() + "." + name + "/" + params);
    }

    private static Field field(Class<?> c, String name) throws Exception {
        Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static void init() {
        if (tried) return;
        tried = true;
        try {
            Class<?> blood = Class.forName(BODY + "BodyBlood");
            Class<?> bodyC = Class.forName(BODY + "BodyBlood$Body");
            Class<?> woundC = Class.forName(BODY + "BodyBlood$Wound");
            Class<?> skinC = Class.forName(BODY + "WoundSkin");
            Class<?> masks = Class.forName(BODY + "SkinMasks");
            Class<?> painter = Class.forName(BODY + "WoundPainter");
            Class<?> pass = Class.forName(BODY + "WoundPass");
            Field b = Class.forName("com.raiiiden.cruor.client.ClientBlood").getDeclaredField("BODIES");
            b.setAccessible(true);
            bodies = b.get(null);
            mBody = method(blood, "body", 1);
            mNow = method(blood, "now", 0);
            mSkinFor = method(blood, "skinFor", 3);
            mPending = method(bodyC, "pending", 0);
            mSettle = method(bodyC, "settle", 0);
            mFade = method(bodyC, "fade", 1);
            fWounds = field(bodyC, "wounds");
            fLayers = field(bodyC, "layers");
            fSkin = field(bodyC, "skin");
            fBlood = field(bodyC, "blood");
            fLayout = field(bodyC, "layout");
            fPainted = field(woundC, "painted");
            fLimb = field(woundC, "limb");
            fTries = field(woundC, "tries");
            fArmourChecked = field(woundC, "armourChecked");
            mUpdate = method(skinC, "update", 3);
            fLoc = field(skinC, "location");
            fCopies = field(skinC, "copies");
            fCount = field(skinC, "count");
            mMaskGet = method(masks, "get", 1);
            mMaskUnion = method(masks, "union", 2);
            mBegin = method(painter, "begin", 1);
            mFinish = method(painter, "finish", 0);
            mInWorld = method(painter, "inWorld", 2);
            mPaint = method(painter, "paint", 8);
            fPainterCopies = field(painter, "copies");
            mPassBegin = method(pass, "begin", 0);
            mPassEnd = method(pass, "end", 0);
            targetIface = Class.forName(BODY + "WoundPainter$WoundTarget");
            for (Object c : Class.forName(BODY + "WoundPainter$Cover").getEnumConstants()) {
                if (c.toString().equals("SKIN")) coverSkin = c;
            }
            if (coverSkin == null) throw new IllegalStateException("Cover.SKIN");
            Class<?> cfg = Class.forName("com.raiiiden.cruor.CruorClientConfig");
            cfgWounds = (ForgeConfigSpec.ConfigValue<?>) field(cfg, "WOUNDS").get(null);
            cfgDry = (ForgeConfigSpec.ConfigValue<?>) field(cfg, "DRY_TIME").get(null);
            ok = true;
        } catch (Throwable t) {
            CruorFight.LOGGER.error("CruorFight: no pude enganchar Cruor (version distinta?)", t);
        }
    }

    static boolean available() {
        init();
        return ok;
    }

    static boolean woundsEnabled() {
        init();
        try { return cfgWounds == null || Boolean.TRUE.equals(cfgWounds.get()); } catch (Throwable t) { return true; }
    }

    /** estado de sangre del cuerpo de esta entidad (null si no tiene) */
    static Object body(LivingEntity e) {
        try { return mBody.invoke(bodies, e.getId()); } catch (Throwable t) { disable(t); return null; }
    }

    private static void disable(Throwable t) {
        if (ok) CruorFight.LOGGER.error("CruorFight: fallo hablando con Cruor, me desactivo", t);
        ok = false;
    }

    /** equivalente a WoundLayer.place(): pinta las heridas pendientes usando la geometria que da 'capture'. Devuelve true si termino. */
    static boolean place(Object body, LivingEntity e, ResourceLocation texture, float pt, Runnable capture) {
        try {
            Object mask = mMaskGet.invoke(null, texture);
            for (Object l : (Set<?>) fLayers.get(body)) mask = mMaskUnion.invoke(null, mask, mMaskGet.invoke(null, l));
            List<?> wounds = (List<?>) fWounds.get(body);
            if (mask == null) {
                for (Object w : wounds) {
                    if (fLimb.getBoolean(w)) continue;
                    int t = fTries.getInt(w) + 1;
                    fTries.setInt(w, t);
                    if (t > 40) fPainted.setBoolean(w, true);
                }
                return true;
            }
            mPassBegin.invoke(null);
            try {
                lastCapture = mBegin.invoke(null, fLayout.get(body));
                capture.run();
                mFinish.invoke(null);
            } finally {
                mPassEnd.invoke(null);
            }
            // todo en la copia 0 de la textura (el dibujo de este addon tambien usa la copia 0)
            int[] copies = (int[]) fPainterCopies.get(null);
            java.util.Arrays.fill(copies, 0);
            if (!(boolean) mInWorld.invoke(null, e, pt)) return false;
            long now = (long) mNow.invoke(bodies);
            Object target = Proxy.newProxyInstance(targetIface.getClassLoader(), new Class<?>[]{targetIface},
                    (InvocationHandler) (proxy, m, a) -> m.getDeclaringClass() == Object.class ? null : mSkinFor.invoke(bodies, body, a[0], a[1]));
            for (Object w : wounds) {
                if (fPainted.getBoolean(w) || fLimb.getBoolean(w)) continue;
                fPainted.setBoolean(w, true);
                mPaint.invoke(null, target, mask, w, fBlood.get(body), e, pt, now, coverSkin);
            }
            return true;
        } catch (Throwable t) {
            disable(t);
            return true;
        }
    }

    /** el capturador de modelo de Cruor (un VertexConsumer) que dejo preparado WoundPainter.begin() */
    static Object captureConsumer() {
        return lastCapture;
    }

    private static Object lastCapture;

    static boolean pending(Object body) {
        try { return (boolean) mPending.invoke(body); } catch (Throwable t) { disable(t); return false; }
    }

    /** tras pintar: marca las heridas como revisadas y limpia las ya resueltas */
    static void settle(Object body) {
        try {
            for (Object w : (List<?>) fWounds.get(body)) fArmourChecked.setBoolean(w, true);
            mSettle.invoke(body);
        } catch (Throwable t) { disable(t); }
    }

    /** actualiza la textura de heridas (secado, desvanecido) y devuelve su info, o null si no hay nada que dibujar */
    static SkinInfo skin(Object body) {
        try {
            ((Set<?>) fLayers.get(body)).clear();
            Object s = fSkin.get(body);
            if (s == null || fCount.getInt(s) == 0) return null;
            long now = (long) mNow.invoke(bodies);
            long dry = Math.max(1L, ((Number) cfgDry.get()).longValue() * 20L);
            mUpdate.invoke(s, now, dry, mFade.invoke(body, now));
            return new SkinInfo((ResourceLocation) fLoc.get(s), Math.max(1, fCopies.getInt(s)));
        } catch (Throwable t) {
            disable(t);
            return null;
        }
    }
}
