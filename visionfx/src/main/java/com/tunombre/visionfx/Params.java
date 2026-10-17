package com.tunombre.visionfx;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/** parametros del filtro (los decide el servidor y se sincronizan a los clientes) */
public final class Params {
    public boolean enabled = true;
    /** intensidad general 0..1 (multiplica todo) */
    public float strength = 1F;
    /** contraste: lo claro mas claro, lo oscuro mas oscuro (el 'spread' del mod de cordura) */
    public float contrast = 0.5F;
    public float desaturate = 0.2F;
    /** multiplicador de brillo final (0.91 en el mod original) */
    public float brightness = 0.95F;
    /** aberracion cromatica (rojo/azul desplazados hacia los bordes) */
    public float chromatic = 0F;
    public float vignette = 0F;
    /** si es true la intensidad sube cuanto mas oscuro es el lugar donde esta el jugador */
    public boolean dark = false;

    public Params copy() {
        Params p = new Params();
        p.enabled = enabled; p.strength = strength; p.contrast = contrast; p.desaturate = desaturate;
        p.brightness = brightness; p.chromatic = chromatic; p.vignette = vignette; p.dark = dark;
        return p;
    }

    public void write(FriendlyByteBuf b) {
        b.writeBoolean(enabled); b.writeBoolean(dark);
        b.writeFloat(strength); b.writeFloat(contrast); b.writeFloat(desaturate);
        b.writeFloat(brightness); b.writeFloat(chromatic); b.writeFloat(vignette);
    }

    public static Params read(FriendlyByteBuf b) {
        Params p = new Params();
        p.enabled = b.readBoolean(); p.dark = b.readBoolean();
        p.strength = b.readFloat(); p.contrast = b.readFloat(); p.desaturate = b.readFloat();
        p.brightness = b.readFloat(); p.chromatic = b.readFloat(); p.vignette = b.readFloat();
        return p;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putBoolean("enabled", enabled); t.putBoolean("dark", dark);
        t.putFloat("strength", strength); t.putFloat("contrast", contrast); t.putFloat("desaturate", desaturate);
        t.putFloat("brightness", brightness); t.putFloat("chromatic", chromatic); t.putFloat("vignette", vignette);
        return t;
    }

    public static Params load(CompoundTag t) {
        Params p = new Params();
        if (t.contains("enabled")) p.enabled = t.getBoolean("enabled");
        if (t.contains("dark")) p.dark = t.getBoolean("dark");
        if (t.contains("strength")) p.strength = t.getFloat("strength");
        if (t.contains("contrast")) p.contrast = t.getFloat("contrast");
        if (t.contains("desaturate")) p.desaturate = t.getFloat("desaturate");
        if (t.contains("brightness")) p.brightness = t.getFloat("brightness");
        if (t.contains("chromatic")) p.chromatic = t.getFloat("chromatic");
        if (t.contains("vignette")) p.vignette = t.getFloat("vignette");
        return p;
    }
}
