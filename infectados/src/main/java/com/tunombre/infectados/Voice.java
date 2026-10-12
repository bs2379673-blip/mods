package com.tunombre.infectados;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.RegistryObject;

import java.util.*;

/**
 * Frases y sonidos de los infectados. Cada clave es un evento de sonido "infectados:voz.<grupo>.<clave>" con su
 * subtitulo; por defecto suenan con sonidos de Minecraft, y con un resource pack se ponen los .ogg propios
 * (ver la plantilla "infectados_voces_plantilla.zip" y su LEEME).
 */
public enum Voice {
    // ---- aldeanos (Resident Evil 4) ----
    AHI_ESTA("ganado", "ahi_esta", "¡Ahí está!"),
    ALLI_ESTA("ganado", "alli_esta", "¡Allí está!"),
    UN_FORASTERO("ganado", "un_forastero", "¡Un forastero!"),
    COGEDLO("ganado", "cogedlo", "¡Cogedlo!"),
    AGARRADLO("ganado", "agarradlo", "¡Agarradlo!"),
    MATALO("ganado", "matalo", "¡Mátalo!"),
    PICADILLO("ganado", "picadillo", "¡Te voy a hacer picadillo!"),
    DETRAS_DE_TI("ganado", "detras_de_ti", "¡Detrás de ti, imbécil!"),
    MUERETE("ganado", "muerete", "¡Muérete!"),
    SE_ESCAPA("ganado", "se_escapa", "¡Se está escapando!"),
    LO_TENGO("ganado", "lo_tengo", "¡Lo tengo!"),
    MORIR_ES_VIVIR("ganado", "morir_es_vivir", "¡Morir es vivir!"),
    TE_VOY_A_MATAR("ganado", "te_voy_a_matar", "¡Te voy a matar!"),
    CERDO("ganado", "cerdo", "¡Cerdo!"),
    MURMULLO("ganado", "murmullo", "Aldeano murmura"),
    DOLOR("ganado", "dolor", "Aldeano: dolor"),
    MUERTE("ganado", "muerte", "Aldeano muere"),
    CABEZA("ganado", "cabeza", "La cabeza revienta"),
    PLAGA("ganado", "plaga", "¡Sale la plaga!"),
    PLAGA_TAJO("ganado", "plaga_tajo", "Tajo de la plaga"),
    // ---- el de la motosierra ----
    MS_RUGIDO("motosierra", "rugido", "Motosierra: rugido"),
    MS_RISA("motosierra", "risa", "Motosierra: risa"),
    MS_MOTOR("motosierra", "motor", "Motosierra en marcha"),
    MS_ACELERA("motosierra", "acelera", "Motosierra acelera"),
    MS_CORTE("motosierra", "corte", "¡Decapitado!"),
    MS_DOLOR("motosierra", "dolor", "Motosierra: dolor"),
    MS_MUERTE("motosierra", "muerte", "Motosierra muere"),
    // ---- Left 4 Dead ----
    SM_TOS("smoker", "tos", "Smoker tose"),
    SM_LENGUA("smoker", "lengua", "¡Lengua!"),
    SM_MUERTE("smoker", "muerte", "Smoker muere"),
    BO_GORGOTEO("boomer", "gorgoteo", "Boomer gorgotea"),
    BO_VOMITO("boomer", "vomito", "¡Vómito!"),
    BO_EXPLOSION("boomer", "explosion", "¡El Boomer revienta!"),
    WI_LLANTO("witch", "llanto", "Witch llorando"),
    WI_GRUNIDO("witch", "grunido", "Witch gruñe"),
    WI_GRITO("witch", "grito", "¡GRITO DE LA WITCH!");

    public final String group, key, text;
    private RegistryObject<SoundEvent> event;

    Voice(String group, String key, String text) {
        this.group = group;
        this.key = key;
        this.text = text;
    }

    public String id() { return "voz." + group + "." + key; }

    static void register() {
        for (Voice v : values()) {
            float range = v == WI_GRITO || v == MS_RUGIDO || v == BO_EXPLOSION ? 64F : 32F;
            v.event = Infectados.SOUNDS.register(v.id(), () -> SoundEvent.createFixedRangeEvent(new ResourceLocation(Infectados.MODID, v.id()), range));
        }
    }

    public SoundEvent sound() { return event.get(); }

    // ---- reproducir sin que todos hablen a la vez ----
    private static final Map<ResourceKey<Level>, Deque<long[]>> RECENT = new HashMap<>();

    /** dice la frase si nadie mas hablo cerca hace poco (las frases importantes pueden forzar). */
    public boolean say(Entity e, boolean force) {
        Level lv = e.level();
        if (lv.isClientSide) return false;
        long now = lv.getGameTime();
        Deque<long[]> q = RECENT.computeIfAbsent(lv.dimension(), k -> new ArrayDeque<>());
        while (!q.isEmpty() && now - q.peekFirst()[0] > 60) q.pollFirst();
        BlockPos p = e.blockPosition();
        if (!force) {
            for (long[] r : q) {
                if (now - r[0] < 45 && BlockPos.of(r[1]).distSqr(p) < 14 * 14) return false;
            }
        }
        q.addLast(new long[]{now, p.asLong()});
        float vol = group.equals("witch") && this == WI_GRITO ? 4F : 1.6F;
        lv.playSound(null, e.getX(), e.getY(), e.getZ(), sound(), SoundSource.HOSTILE, vol, 0.95F + lv.random.nextFloat() * 0.1F);
        return true;
    }

    public static Voice pick(net.minecraft.util.RandomSource r, Voice... vs) { return vs[r.nextInt(vs.length)]; }
}
