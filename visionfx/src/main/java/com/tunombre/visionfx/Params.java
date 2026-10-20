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

    // ---- color (plantillas)
    /** saturacion: 1 = igual, 0 = gris, 2 = muy intensa */
    public float saturation = 1F;
    /** gamma: >1 aclara las sombras, <1 las oscurece */
    public float gamma = 1F;
    /** tonos divididos: color de las sombras y de las luces (0.5 = neutro) y cuanto se aplica */
    public float toneAmt = 0F;
    public float shR = 0.5F, shG = 0.5F, shB = 0.5F;
    public float hiR = 0.5F, hiG = 0.5F, hiB = 0.5F;

    // ---- vision PlayStation 1 (efecto aparte, lo activa un admin)
    public boolean ps1 = false;
    /** resolucion vertical interna (la PS1 usaba ~240 lineas) */
    public float ps1Res = 240F;
    /** distancia (bloques) a la que todo se pierde en la niebla; 0 = sin niebla */
    public float ps1Fog = 64F;
    /** fuerza del tramado (dither) de 15 bits, 0..1 */
    public float ps1Dither = 1F;

    /** modo de evento (efecto de pantalla aparte): 0 = ninguno, ver Modes */
    public int mode = 0;

    public Params copy() {
        Params p = new Params();
        p.enabled = enabled; p.strength = strength; p.contrast = contrast; p.desaturate = desaturate;
        p.brightness = brightness; p.chromatic = chromatic; p.vignette = vignette; p.dark = dark;
        p.saturation = saturation; p.gamma = gamma; p.toneAmt = toneAmt;
        p.shR = shR; p.shG = shG; p.shB = shB; p.hiR = hiR; p.hiG = hiG; p.hiB = hiB;
        p.ps1 = ps1; p.ps1Res = ps1Res; p.ps1Fog = ps1Fog; p.ps1Dither = ps1Dither;
        p.mode = mode;
        return p;
    }

    /** copia los ajustes de color de una plantilla (no toca el encendido, la vision PS1 ni el modo de evento) */
    public void applyColors(Params t) {
        strength = t.strength; contrast = t.contrast; desaturate = t.desaturate; brightness = t.brightness;
        chromatic = t.chromatic; vignette = t.vignette; dark = t.dark;
        saturation = t.saturation; gamma = t.gamma; toneAmt = t.toneAmt;
        shR = t.shR; shG = t.shG; shB = t.shB; hiR = t.hiR; hiG = t.hiG; hiB = t.hiB;
    }

    public void write(FriendlyByteBuf b) {
        b.writeBoolean(enabled); b.writeBoolean(dark);
        b.writeFloat(strength); b.writeFloat(contrast); b.writeFloat(desaturate);
        b.writeFloat(brightness); b.writeFloat(chromatic); b.writeFloat(vignette);
        b.writeFloat(saturation); b.writeFloat(gamma); b.writeFloat(toneAmt);
        b.writeFloat(shR); b.writeFloat(shG); b.writeFloat(shB); b.writeFloat(hiR); b.writeFloat(hiG); b.writeFloat(hiB);
        b.writeBoolean(ps1); b.writeFloat(ps1Res); b.writeFloat(ps1Fog); b.writeFloat(ps1Dither);
        b.writeVarInt(mode);
    }

    public static Params read(FriendlyByteBuf b) {
        Params p = new Params();
        p.enabled = b.readBoolean(); p.dark = b.readBoolean();
        p.strength = b.readFloat(); p.contrast = b.readFloat(); p.desaturate = b.readFloat();
        p.brightness = b.readFloat(); p.chromatic = b.readFloat(); p.vignette = b.readFloat();
        p.saturation = b.readFloat(); p.gamma = b.readFloat(); p.toneAmt = b.readFloat();
        p.shR = b.readFloat(); p.shG = b.readFloat(); p.shB = b.readFloat(); p.hiR = b.readFloat(); p.hiG = b.readFloat(); p.hiB = b.readFloat();
        p.ps1 = b.readBoolean(); p.ps1Res = b.readFloat(); p.ps1Fog = b.readFloat(); p.ps1Dither = b.readFloat();
        p.mode = b.readVarInt();
        return p;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putBoolean("enabled", enabled); t.putBoolean("dark", dark);
        t.putFloat("strength", strength); t.putFloat("contrast", contrast); t.putFloat("desaturate", desaturate);
        t.putFloat("brightness", brightness); t.putFloat("chromatic", chromatic); t.putFloat("vignette", vignette);
        t.putFloat("saturation", saturation); t.putFloat("gamma", gamma); t.putFloat("toneAmt", toneAmt);
        t.putFloat("shR", shR); t.putFloat("shG", shG); t.putFloat("shB", shB);
        t.putFloat("hiR", hiR); t.putFloat("hiG", hiG); t.putFloat("hiB", hiB);
        t.putBoolean("ps1", ps1); t.putFloat("ps1Res", ps1Res); t.putFloat("ps1Fog", ps1Fog); t.putFloat("ps1Dither", ps1Dither);
        t.putInt("mode", mode);
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
        if (t.contains("saturation")) p.saturation = t.getFloat("saturation");
        if (t.contains("gamma")) p.gamma = t.getFloat("gamma");
        if (t.contains("toneAmt")) p.toneAmt = t.getFloat("toneAmt");
        if (t.contains("shR")) { p.shR = t.getFloat("shR"); p.shG = t.getFloat("shG"); p.shB = t.getFloat("shB"); }
        if (t.contains("hiR")) { p.hiR = t.getFloat("hiR"); p.hiG = t.getFloat("hiG"); p.hiB = t.getFloat("hiB"); }
        if (t.contains("ps1")) p.ps1 = t.getBoolean("ps1");
        if (t.contains("ps1Res")) p.ps1Res = t.getFloat("ps1Res");
        if (t.contains("ps1Fog")) p.ps1Fog = t.getFloat("ps1Fog");
        if (t.contains("ps1Dither")) p.ps1Dither = t.getFloat("ps1Dither");
        if (t.contains("mode")) p.mode = t.getInt("mode");
        return p;
    }
}
