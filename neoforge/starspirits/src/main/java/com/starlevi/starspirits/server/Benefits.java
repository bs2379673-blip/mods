package com.starlevi.starspirits.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.starlevi.starspirits.SpiritData;
import com.starlevi.starspirits.SpiritDef;
import com.starlevi.starspirits.StarSpirits;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Beneficios del espiritu activo. Cada espiritu trae una lista "beneficios" en su json:
 * efecto, curar, crecer, caida, brillo_monstruos, espinas, escudo, salvar (ver LEEME.txt).
 */
public final class Benefits {
    private static final ResourceLocation SHIELD = ResourceLocation.fromNamespaceAndPath(StarSpirits.MOD_ID, "escudo");
    private static final Map<UUID, Integer> LAST_HURT = new HashMap<>();
    private static int ticks;

    private Benefits() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(Benefits::onFall);
        NeoForge.EVENT_BUS.addListener(Benefits::onDamage);
        NeoForge.EVENT_BUS.addListener(Benefits::onDeath);
    }

    private static SpiritDef activeDef(ServerPlayer p) {
        SpiritData.Entry e = Caravan.data(p).activeEntry();
        return e == null ? null : SpiritLibrary.get(e.type());
    }

    private static JsonObject find(SpiritDef d, String type) {
        if (d == null) {
            return null;
        }
        for (JsonElement el : d.benefits) {
            if (el.isJsonObject() && type.equals(str(el.getAsJsonObject(), "tipo", ""))) {
                return el.getAsJsonObject();
            }
        }
        return null;
    }

    private static String str(JsonObject o, String k, String def) {
        return o.has(k) ? o.get(k).getAsString() : def;
    }

    private static double num(JsonObject o, String k, double def) {
        try {
            return o.has(k) ? o.get(k).getAsDouble() : def;
        } catch (Exception e) {
            return def;
        }
    }

    /** Cambio el activo o la lista: se reacomoda el escudo. */
    public static void changed(ServerPlayer p) {
        ticks = (ticks / 20) * 20 + 19; // el proximo tick reaplica
    }

    static void tick(MinecraftServer server) {
        ticks++;
        int now = server.getTickCount();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            SpiritDef d = p.isSpectator() ? null : activeDef(p);
            shield(p, d == null ? null : find(d, "escudo"), now);
            if (d == null || ticks % 20 != 0) {
                continue;
            }
            for (JsonElement el : d.benefits) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject b = el.getAsJsonObject();
                switch (str(b, "tipo", "")) {
                    case "efecto" -> effect(p, b);
                    case "curar" -> {
                        int every = Math.max(1, (int) num(b, "cada_segundos", 8));
                        if ((ticks / 20) % every == 0 && p.getHealth() < p.getMaxHealth()) {
                            p.heal((float) num(b, "cantidad", 0.5) * 2f);
                            p.serverLevel().sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 2.1, p.getZ(), 1, 0.2, 0.1, 0.2, 0);
                        }
                    }
                    case "crecer" -> {
                        int every = Math.max(1, (int) num(b, "cada_segundos", 5));
                        if ((ticks / 20) % every == 0) {
                            grow(p, (int) num(b, "radio", 6));
                        }
                    }
                    case "brillo_monstruos" -> {
                        double r = num(b, "radio", 16);
                        for (LivingEntity m : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r),
                                x -> x instanceof Enemy && x.isAlive())) {
                            m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, true, false, false));
                        }
                    }
                    default -> {
                    }
                }
            }
        }
    }

    private static void effect(ServerPlayer p, JsonObject b) {
        try {
            ResourceLocation id = ResourceLocation.parse(str(b, "efecto", "minecraft:speed"));
            Holder<MobEffect> h = BuiltInRegistries.MOB_EFFECT.getHolder(id).orElse(null);
            if (h == null) {
                return;
            }
            int amp = Math.max(0, (int) num(b, "nivel", 1) - 1);
            // vision nocturna: duracion larga para que no parpadee la pantalla
            int dur = h.is(MobEffects.NIGHT_VISION) ? 260 : 60;
            p.addEffect(new MobEffectInstance(h, dur, amp, true, false, true));
        } catch (Exception ignored) {
            // efecto mal escrito
        }
    }

    private static void grow(ServerPlayer p, int radius) {
        ServerLevel level = p.serverLevel();
        BlockPos c = p.blockPosition();
        for (int i = 0; i < 8; i++) {
            BlockPos at = c.offset(level.random.nextInt(radius * 2 + 1) - radius, level.random.nextInt(5) - 2,
                    level.random.nextInt(radius * 2 + 1) - radius);
            BlockState st = level.getBlockState(at);
            boolean plant = st.is(BlockTags.CROPS) || st.is(BlockTags.SAPLINGS) || st.getBlock() instanceof StemBlock
                    || st.getBlock() instanceof SweetBerryBushBlock || st.getBlock() instanceof CocoaBlock;
            if (plant && st.getBlock() instanceof BonemealableBlock bm && bm.isValidBonemealTarget(level, at, st)
                    && level.random.nextFloat() < 0.5f) {
                bm.performBonemeal(level, level.random, at, st);
                level.levelEvent(1505, at, 0); // particulas verdes de crecimiento
            }
        }
    }

    private static void shield(ServerPlayer p, JsonObject b, int now) {
        AttributeInstance max = p.getAttribute(Attributes.MAX_ABSORPTION);
        if (max == null) {
            return;
        }
        if (b == null) {
            if (max.getModifier(SHIELD) != null) {
                max.removeModifier(SHIELD);
                p.setAbsorptionAmount(Math.min(p.getAbsorptionAmount(), p.getMaxAbsorption()));
            }
            return;
        }
        double hearts = num(b, "corazones", 4) * 2;
        AttributeModifier mod = max.getModifier(SHIELD);
        if (mod == null || mod.amount() != hearts) {
            max.removeModifier(SHIELD);
            max.addTransientModifier(new AttributeModifier(SHIELD, hearts, AttributeModifier.Operation.ADD_VALUE));
        }
        int wait = (int) (num(b, "espera_segundos", 10) * 20);
        if (now % 40 == 0 && now - LAST_HURT.getOrDefault(p.getUUID(), -100000) > wait && p.getAbsorptionAmount() < hearts) {
            p.setAbsorptionAmount((float) Math.min(hearts, p.getAbsorptionAmount() + 2));
            p.serverLevel().sendParticles(ParticleTypes.WAX_ON, p.getX(), p.getY() + 1.0, p.getZ(), 4, 0.3, 0.4, 0.3, 0.0);
        }
    }

    // ------------------------------------------------------------------ eventos

    private static void onFall(LivingFallEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            JsonObject b = find(activeDef(p), "caida");
            if (b != null) {
                e.setDistance(Math.max(0, e.getDistance() - (float) num(b, "bloques", 4)));
            }
        }
    }

    private static void onDamage(LivingDamageEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        LAST_HURT.put(p.getUUID(), p.server.getTickCount());
        JsonObject b = find(activeDef(p), "espinas");
        if (b != null && !e.getSource().is(DamageTypes.THORNS) && e.getSource().getEntity() instanceof LivingEntity attacker
                && attacker != p) {
            float back = Math.max(1f, e.getNewDamage() * (float) num(b, "porcentaje", 30) / 100f);
            attacker.hurt(p.damageSources().thorns(p), back);
            p.serverLevel().sendParticles(ParticleTypes.CRIMSON_SPORE, attacker.getX(), attacker.getY() + 1, attacker.getZ(), 8, 0.3, 0.4, 0.3, 0);
        }
    }

    private static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        JsonObject b = find(activeDef(p), "salvar");
        SpiritData d = Caravan.data(p);
        long now = p.serverLevel().getGameTime();
        if (b == null || now < d.saveReadyAt) {
            return;
        }
        e.setCanceled(true);
        d.saveReadyAt = now + (long) (num(b, "recarga_minutos", 10) * 1200);
        p.setHealth(Math.max(2f, p.getMaxHealth() * 0.3f));
        p.removeAllEffects();
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
        p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
        p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 400, 0));
        p.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1, p.getZ(), 60, 0.5, 0.8, 0.5, 0.4);
        p.serverLevel().sendParticles(ParticleTypes.CHERRY_LEAVES, p.getX(), p.getY() + 1, p.getZ(), 40, 0.8, 0.8, 0.8, 0.05);
        p.level().playSound(null, p.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1f, 1.2f);
        SpiritDef def = activeDef(p);
        p.sendSystemMessage(Component.literal("🌳 " + (def == null ? "Tu espíritu" : def.name) + " te salvó de morir"));
    }
}
