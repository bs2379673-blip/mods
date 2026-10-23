package com.tunombre.jefes.client;

import com.tunombre.jefes.Jefes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Musica del minijuego de las paginas. Los cuatro temas (paginas 1-2, 3-4, 5-6 y 7) arrancan a la vez, con el mismo
 * largo, y solo cambia cual se oye: asi siempre van al mismo compas y el paso de una fase a otra es un crossfade
 * en vez de un corte. Al juntar la ultima pagina (o parar el juego) todo se apaga de a poco.
 */
public final class SlenderMusic {
    private SlenderMusic() { }

    /** volumen maximo de cada tema (el primero es mas suave de por si) */
    private static final float[] GAIN = {1.0F, 0.9F, 0.85F, 0.85F};
    /** segundos que tarda en entrar/salir un tema */
    private static final float FADE_IN = 3.5F, FADE_OUT = 5F;

    private static final Layer[] layers = new Layer[4];
    private static int phase;          // 0 = silencio, 1..4 = tema activo
    private static boolean wanted;     // el juego esta en marcha

    static final class Layer extends AbstractTickableSoundInstance {
        float level, target;
        final float gain;
        boolean done;

        Layer(int i) {
            super(Jefes.SLENDER_THEMES.get(i).get(), SoundSource.MUSIC, RandomSource.create());
            this.gain = GAIN[i];
            this.looping = true;
            this.delay = 0;
            this.volume = 0.0001F;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        @Override
        public void tick() {
            float step = 1F / 20F / (target > level ? FADE_IN : FADE_OUT);
            if (level < target) level = Math.min(target, level + step);
            else if (level > target) level = Math.max(target, level - step);
            // curva suave para que el cruce no se note como una rampa
            float e = level * level * (3F - 2F * level);
            this.volume = Math.max(0.0001F, e * gain);
        }

        @Override
        public boolean canStartSilent() { return true; }

        @Override
        public boolean isStopped() { return done || super.isStopped(); }
    }

    /** el servidor avisa el estado del minijuego */
    public static void game(int collected, int total, boolean active) {
        wanted = active && total > 0;
        int p = 0;
        if (wanted && collected > 0) {
            float f = collected / (float) total;
            p = f <= 0.25F ? 1 : f <= 0.5F ? 2 : f <= 0.75F ? 3 : f < 1F ? 4 : 0;
        }
        if (p != phase && p > 0) {
            // la musica de fondo del juego se calla mientras suena la del minijuego
            try { Minecraft.getInstance().getMusicManager().stopPlaying(); } catch (Throwable ignored) { }
        }
        phase = p;
        if (p > 0) ensure();
        for (int i = 0; i < 4; i++) if (layers[i] != null) layers[i].target = (i + 1 == phase) ? 1F : 0F;
    }

    private static void ensure() {
        var sm = Minecraft.getInstance().getSoundManager();
        // si hace falta arrancar, arrancan los cuatro juntos para que queden alineados
        if (layers[0] != null) return;
        for (int i = 0; i < 4; i++) {
            layers[i] = new Layer(i);
            sm.play(layers[i]);
        }
    }

    public static void tick() {
        if (phase == 0) {
            // todo apagado y ya sin volumen: se liberan los streams
            boolean silent = true;
            for (Layer l : layers) if (l != null && l.level > 0F) silent = false;
            if (silent) stopAll();
        }
        var mc = Minecraft.getInstance();
        if (phase > 0 && mc.level == null) { stopAll(); phase = 0; }
    }

    public static void stopAll() {
        var sm = Minecraft.getInstance().getSoundManager();
        for (int i = 0; i < 4; i++) {
            if (layers[i] != null) { layers[i].done = true; sm.stop(layers[i]); layers[i] = null; }
        }
    }
}
