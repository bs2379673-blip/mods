package com.tunombre.efzombies;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.DrownedModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.ZombieVillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.DrownedRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HuskRenderer;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.client.renderer.entity.ZombieVillagerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombieVillager;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Solo cliente. Animaciones de zombi hechas por codigo (sin Epic Fight, sin EMF): cada zombi tiene su forma de caminar,
 * correr y quedarse quieto (sale de su UUID, igual en todos los clientes) y algunos van a cuatro patas.
 * Se aplican sobre el modelo vanilla despues de su propia animacion, asi que los golpes y la mano con objeto siguen igual.
 */
public final class ZombieAnim {
    private ZombieAnim() { }

    /** armR, armL: inclinacion base de los brazos (negativo = hacia adelante); spread: apertura; lean: grados hacia adelante;
     *  roll/nod: cabeza; limp: cojera; sway: balanceo lateral; leg: amplitud de piernas; freq: ritmo; pump: bombeo de brazos */
    private record Style(float armR, float armL, float spread, float lean, float roll, float nod, float limp, float sway, float leg, float freq, float pump) { }

    private static final Style[] WALK = {
            new Style(-1.45F, -1.45F, 0.05F, 8F, 0.00F, 0.10F, 0.0F, 0.03F, 0.90F, 0.90F, 0.0F),   // clasico, brazos al frente
            new Style(-0.35F, -0.45F, 0.08F, 14F, 0.12F, 0.25F, 0.0F, 0.05F, 0.80F, 0.80F, 0.2F),  // brazos caidos
            new Style(-1.20F, -0.90F, 0.10F, 10F, -0.20F, 0.15F, 0.8F, 0.06F, 0.70F, 0.75F, 0.0F), // cojo
            new Style(-0.90F, -0.90F, 0.15F, 22F, 0.10F, 0.35F, 0.0F, 0.04F, 0.75F, 0.80F, 0.0F),  // encorvado
            new Style(-1.55F, -0.30F, 0.05F, 9F, 0.15F, 0.10F, 0.2F, 0.05F, 0.85F, 0.85F, 0.0F),   // un brazo arriba
            new Style(-1.00F, -1.20F, 0.35F, 6F, -0.15F, 0.20F, 0.0F, 0.12F, 0.80F, 0.65F, 0.1F),  // borracho
            new Style(-1.50F, -1.50F, 0.00F, 3F, 0.00F, 0.00F, 0.0F, 0.02F, 1.00F, 1.10F, 0.0F),   // tieso
    };

    private static final Style[] RUN = {
            new Style(-1.50F, -1.50F, 0.00F, 26F, 0.00F, 0.15F, 0.0F, 0.03F, 1.00F, 1.00F, 0.0F),  // al frente
            new Style(-0.60F, -0.60F, 0.05F, 30F, 0.05F, 0.20F, 0.0F, 0.04F, 1.05F, 1.10F, 1.0F),  // bombeando brazos
            new Style(-1.30F, -1.00F, 0.12F, 24F, -0.18F, 0.25F, 0.5F, 0.07F, 0.95F, 0.95F, 0.3F), // desparejo
            new Style(-1.45F, -1.45F, 0.20F, 36F, 0.10F, 0.40F, 0.0F, 0.05F, 1.05F, 1.15F, 0.0F),  // abalanzado
            new Style(-0.20F, -0.20F, 0.30F, 32F, 0.20F, 0.30F, 0.0F, 0.09F, 1.00F, 1.20F, 0.6F),  // brazos sueltos
            new Style(-1.60F, -0.70F, 0.08F, 28F, 0.15F, 0.15F, 0.2F, 0.06F, 1.00F, 1.05F, 0.4F),  // uno al frente
            new Style(-1.40F, -1.40F, 0.10F, 20F, 0.00F, 0.10F, 0.0F, 0.04F, 1.10F, 1.25F, 0.2F),  // zancada larga
    };

    /** reposo: armR, armL, balanceo, cabeceo, giro de cabeza, velocidad */
    private static final float[][] IDLE = {
            {-0.45F, -0.45F, 0.04F, 0.10F, 0.15F, 0.9F},
            {-1.20F, -1.20F, 0.02F, 0.06F, 0.30F, 0.7F},
            {-0.20F, -0.30F, 0.08F, 0.20F, 0.10F, 1.0F},
            {-0.70F, -0.15F, 0.05F, 0.15F, 0.40F, 0.8F},
            {-0.35F, -0.35F, 0.03F, 0.30F, 0.05F, 0.6F},
            {-1.00F, -0.60F, 0.06F, 0.12F, 0.25F, 1.1F},
    };

    private static final class St { float run; }

    private static final Map<Entity, St> STATE = new WeakHashMap<>();

    private static St st(Entity e) { return STATE.computeIfAbsent(e, k -> new St()); }

    private static int pick(Entity e, int salt, int n) {
        UUID u = e.getUUID();
        long h = u.getMostSignificantBits() * 0x9E3779B97F4A7C15L + u.getLeastSignificantBits() + salt * 0xC2B2AE3D27D4EB4FL;
        h ^= (h >>> 29);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 32);
        return (int) Math.floorMod(h, (long) n);
    }

    private static boolean crawls(Zombie z) {
        return !z.isBaby() && z.getMainHandItem().isEmpty() && pick(z, 9, 100) < 18;
    }

    private static final float CRAWL = 1.35F;   // inclinacion del torso a cuatro patas (rad)

    /** se llama despues de la animacion vanilla del modelo */
    static void apply(HumanoidModel<?> m, Zombie z, float ls, float lsa, float age, float headPitchDeg) {
        if (z instanceof Trepador) return;
        if (z.isInWater() && z instanceof Drowned) return;
        St s = st(z);
        boolean aggro = z.isAggressive();
        s.run += ((aggro ? 1F : 0F) - s.run) * 0.08F;
        float moving = Mth.clamp(lsa * 2.2F, 0F, 1F);
        boolean item = !z.getMainHandItem().isEmpty();
        float seedPhase = pick(z, 4, 628) / 100F;

        if (crawls(z)) {
            float ph = ls * 0.6662F * 0.8F;
            float sw = Mth.cos(ph) * 0.7F * Math.min(1F, lsa * 1.6F);
            m.rightArm.xRot = CRAWL + sw;
            m.leftArm.xRot = CRAWL - sw;
            m.rightArm.yRot = 0F; m.leftArm.yRot = 0F;
            m.rightArm.zRot = 0.05F; m.leftArm.zRot = -0.05F;
            m.rightLeg.xRot = CRAWL * 0.9F - sw * 0.8F;
            m.leftLeg.xRot = CRAWL * 0.9F + sw * 0.8F;
            m.head.xRot = -CRAWL * 0.85F + headPitchDeg * Mth.DEG_TO_RAD * 0.6F;
            m.head.zRot = Mth.sin(age * 0.08F + seedPhase) * 0.08F;
            return;
        }

        Style w = WALK[pick(z, 1, WALK.length)];
        Style r = RUN[pick(z, 2, RUN.length)];
        float[] id = IDLE[pick(z, 3, IDLE.length)];
        float k = s.run;

        float freq = Mth.lerp(k, w.freq, r.freq);
        float ph = ls * 0.6662F * freq;
        float legAmp = Mth.lerp(k, w.leg, r.leg);
        float limp = Mth.lerp(k, w.limp, r.limp);
        float cos = Mth.cos(ph);
        // piernas (solo si se mueve; en reposo quedan como las deja vanilla)
        m.rightLeg.xRot = cos * 1.4F * lsa * legAmp;
        m.leftLeg.xRot = -cos * 1.4F * lsa * legAmp * (1F - 0.45F * limp);
        m.rightLeg.yRot = 0F; m.leftLeg.yRot = 0F;

        // brazos: no se tocan si esta golpeando (los mueve vanilla) y la mano con objeto queda como vanilla
        if (m.attackTime <= 0F) {
            float armR = Mth.lerp(k, w.armR, r.armR);
            float armL = Mth.lerp(k, w.armL, r.armL);
            float spread = Mth.lerp(k, w.spread, r.spread);
            float pump = Mth.lerp(k, w.pump, r.pump);
            float breathe = Mth.sin(age * 0.09F * id[5] + seedPhase) * 0.05F;
            // reposo -> movimiento
            float aR = Mth.lerp(moving, id[0] + breathe, armR + cos * pump * lsa);
            float aL = Mth.lerp(moving, id[1] - breathe, armL - cos * pump * lsa);
            if (!item) m.rightArm.xRot = aR;
            m.leftArm.xRot = aL;
            m.rightArm.yRot = -spread * 0.4F;
            m.leftArm.yRot = spread * 0.4F;
            m.rightArm.zRot = spread + breathe;
            m.leftArm.zRot = -spread - breathe;
        }

        // cabeza
        float roll = Mth.lerp(moving, Mth.sin(age * 0.05F * id[5] + seedPhase) * id[4] * 0.5F, Mth.lerp(k, w.roll, r.roll));
        m.head.zRot = roll + Mth.sin(age * 0.11F + seedPhase) * 0.03F;
        m.head.xRot += Mth.lerp(moving, id[3] + Mth.sin(age * 0.07F + seedPhase) * 0.05F, Mth.lerp(k, w.nod, r.nod));
        m.head.yRot += Mth.lerp(moving, Mth.sin(age * 0.035F * id[5] + seedPhase) * id[4], 0F);
    }

    /** inclinacion del cuerpo entero (se llama desde setupRotations de los render) */
    static void rotate(Zombie z, PoseStack ps, float partial) {
        if (z instanceof Trepador) return;
        if (z.isInWater() && z instanceof Drowned) return;
        St s = st(z);
        float lsa = Mth.lerp(partial, z.animationSpeedOld, z.animationSpeed);
        float lp = z.animationPosition - z.animationSpeed * (1F - partial);
        float moving = Mth.clamp(lsa * 2.2F, 0F, 1F);
        if (crawls(z)) {
            ps.translate(0.0D, 0.75D, 0.0D);
            ps.mulPose(Axis.XP.rotation(-CRAWL));
            ps.translate(0.0D, -0.75D, 0.0D);
            return;
        }
        Style w = WALK[pick(z, 1, WALK.length)];
        Style r = RUN[pick(z, 2, RUN.length)];
        float k = s.run;
        float lean = Mth.lerp(k, w.lean, r.lean) * moving;
        float limp = Mth.lerp(k, w.limp, r.limp);
        float sway = Mth.lerp(k, w.sway, r.sway) * moving;
        float freq = Mth.lerp(k, w.freq, r.freq);
        float ph = lp * 0.6662F * freq;
        float roll = Mth.sin(ph) * sway + limp * 0.07F * Mth.cos(ph) * moving;
        ps.translate(0.0D, 0.75D, 0.0D);
        ps.mulPose(Axis.XP.rotationDegrees(-lean));
        ps.mulPose(Axis.ZP.rotation(roll));
        ps.translate(0.0D, -0.75D, 0.0D);
    }

    // ---- modelos ----
    public static class ZModel<T extends Zombie> extends ZombieModel<T> {
        public ZModel(ModelPart p) { super(p); }

        @Override
        public void setupAnim(T e, float ls, float lsa, float age, float yaw, float pitch) {
            super.setupAnim(e, ls, lsa, age, yaw, pitch);
            apply(this, e, ls, lsa, age, pitch);
        }
    }

    public static class DModel<T extends Zombie> extends DrownedModel<T> {
        public DModel(ModelPart p) { super(p); }

        @Override
        public void setupAnim(T e, float ls, float lsa, float age, float yaw, float pitch) {
            super.setupAnim(e, ls, lsa, age, yaw, pitch);
            apply(this, e, ls, lsa, age, pitch);
        }
    }

    public static class VModel<T extends Zombie> extends ZombieVillagerModel<T> {
        public VModel(ModelPart p) { super(p); }

        @Override
        public void setupAnim(T e, float ls, float lsa, float age, float yaw, float pitch) {
            super.setupAnim(e, ls, lsa, age, yaw, pitch);
            apply(this, e, ls, lsa, age, pitch);
        }
    }

    // ---- renders ----
    public static class ZRenderer extends ZombieRenderer {
        public ZRenderer(EntityRendererProvider.Context c) {
            super(c);
            this.model = new ZModel<>(c.bakeLayer(ModelLayers.ZOMBIE));
        }

        @Override
        protected void setupRotations(Zombie z, PoseStack ps, float age, float yaw, float partial) {
            super.setupRotations(z, ps, age, yaw, partial);
            rotate(z, ps, partial);
        }
    }

    public static class HRenderer extends HuskRenderer {
        public HRenderer(EntityRendererProvider.Context c) {
            super(c);
            this.model = new ZModel<>(c.bakeLayer(ModelLayers.HUSK));
        }

        @Override
        protected void setupRotations(Zombie z, PoseStack ps, float age, float yaw, float partial) {
            super.setupRotations(z, ps, age, yaw, partial);
            rotate(z, ps, partial);
        }
    }

    public static class DRenderer extends DrownedRenderer {
        public DRenderer(EntityRendererProvider.Context c) {
            super(c);
            this.model = new DModel<>(c.bakeLayer(ModelLayers.DROWNED));
        }

        @Override
        protected void setupRotations(Drowned z, PoseStack ps, float age, float yaw, float partial) {
            super.setupRotations(z, ps, age, yaw, partial);
            rotate(z, ps, partial);
        }
    }

    public static class VRenderer extends ZombieVillagerRenderer {
        public VRenderer(EntityRendererProvider.Context c) {
            super(c);
            this.model = new VModel<>(c.bakeLayer(ModelLayers.ZOMBIE_VILLAGER));
        }

        @Override
        protected void setupRotations(ZombieVillager z, PoseStack ps, float age, float yaw, float partial) {
            super.setupRotations(z, ps, age, yaw, partial);
            rotate(z, ps, partial);
        }
    }
}
