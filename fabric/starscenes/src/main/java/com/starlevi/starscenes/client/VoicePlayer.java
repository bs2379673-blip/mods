package com.starlevi.starscenes.client;

import com.starlevi.starscenes.net.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

import java.util.Locale;
import java.util.Random;

/**
 * La "voz" de los dialogos: mientras se escribe el texto suena un sonidito por letra, como en
 * Undertale (un bip) o Animal Crossing (vocales con tono). Cada letra elige la vocal que suena y
 * varia un poco el tono; las preguntas suben al final.
 */
public final class VoicePlayer {
    private static final SoundEvent[] VOWELS = new SoundEvent[5];
    private static final SoundEvent BLIP = sound("voz.blip"), ROBOT = sound("voz.robot");
    private static final Random RND = new Random();

    static {
        String[] v = {"a", "e", "i", "o", "u"};
        for (int i = 0; i < 5; i++) {
            VOWELS[i] = sound("voz." + v[i]);
        }
    }

    private long lastAt;

    private static SoundEvent sound(String id) {
        return SoundEvent.createVariableRangeEvent(Net.id(id));
    }

    /** Tono base de cada voz. */
    public static float base(String voice) {
        return switch (voice == null ? "" : voice.toLowerCase(Locale.ROOT)) {
            case "masculina" -> 0.78f;
            case "femenina" -> 1.28f;
            case "grave" -> 0.6f;
            case "aguda" -> 1.6f;
            default -> 1f;
        };
    }

    /** Suena la letra c (si corresponde); question = la frase termina en "?" y estamos cerca del final. */
    public void letter(char c, String voice, float pitch, boolean rising) {
        if (voice == null || voice.equals("ninguna") || !Character.isLetterOrDigit(c)) {
            return;
        }
        long now = System.currentTimeMillis();
        // no mas de ~22 por segundo, para que no sea un zumbido
        if (now - this.lastAt < 45) {
            return;
        }
        this.lastAt = now;
        SoundEvent ev;
        float p = base(voice) * pitch;
        switch (voice) {
            case "simple" -> {
                ev = BLIP;
                p *= 1 + (RND.nextFloat() - 0.5f) * 0.04f;
            }
            case "robot" -> {
                ev = ROBOT;
                p *= 1 + (RND.nextInt(3) - 1) * 0.08f;
            }
            default -> {
                // cada letra suena como una vocal (las consonantes eligen una fija), con tono que cambia un poco
                char l = Character.toLowerCase(c);
                int i = switch (l) {
                    case 'a', 'á' -> 0;
                    case 'e', 'é' -> 1;
                    case 'i', 'í', 'y' -> 2;
                    case 'o', 'ó' -> 3;
                    case 'u', 'ú', 'ü', 'w' -> 4;
                    default -> Math.floorMod(l * 7, 5);
                };
                ev = VOWELS[i];
                p *= 1 + ((l * 31) % 9 - 4) * 0.018f + (RND.nextFloat() - 0.5f) * 0.05f;
            }
        }
        if (rising) {
            p *= 1.18f;
        }
        p = Math.max(0.5f, Math.min(2f, p));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ev, p, 0.32f));
    }

    /** Una frase corta de muestra (para probar la voz desde el menu). */
    public static void sample(String voice, float pitch) {
        VoicePlayer vp = new VoicePlayer();
        String s = "hola";
        new Thread(() -> {
            for (char c : s.toCharArray()) {
                Minecraft.getInstance().execute(() -> {
                    vp.lastAt = 0;
                    vp.letter(c, voice, pitch, false);
                });
                try {
                    Thread.sleep(70);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "StarScenes voz").start();
    }
}
