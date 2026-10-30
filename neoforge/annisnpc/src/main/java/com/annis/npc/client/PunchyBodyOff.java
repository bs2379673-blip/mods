package com.annis.npc.client;

import com.annis.npc.compat.FabricLoader;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Punchy Moves "solo cara": Punchy deja sus expresiones de la cara, pero no toca el cuerpo del
 * jugador. Se guarda la pose justo antes de Punchy (con lo que pusieron el juego y los otros mods:
 * comer, beber, Not Enough Animations, EMF Compat...) y se repone justo despues de Punchy.
 * Las animaciones de Annis (interacciones, emotes compartidos) van despues y se ven igual.
 *
 * Se apaga en config/annisnpc/compat.properties con punchy_cuerpo=true.
 */
public final class PunchyBodyOff {
    public static final boolean ACTIVE = FabricLoader.getInstance().isModLoaded("punchy_moves") && !readBodyOn();
    private static final float[] SNAP = new float[12 * 9];
    private static Object snapModel;

    private PunchyBodyOff() {
    }

    private static boolean readBodyOn() {
        try {
            Path dir = FabricLoader.getInstance().getConfigDir().resolve("annisnpc");
            Path file = dir.resolve("compat.properties");
            Properties p = new Properties();
            if (Files.isRegularFile(file)) {
                try (var in = Files.newInputStream(file)) {
                    p.load(in);
                }
            } else {
                Files.createDirectories(dir);
                Files.writeString(file, "# Punchy Moves: true = Punchy anima el cuerpo del jugador; false = solo la cara\n"
                        + "punchy_cuerpo=false\n");
            }
            return Boolean.parseBoolean(p.getProperty("punchy_cuerpo", "false").trim());
        } catch (Exception e) {
            return false;
        }
    }

    private static ModelPart[] parts(PlayerModel<?> m) {
        return new ModelPart[]{m.head, m.hat, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg,
                m.jacket, m.rightSleeve, m.leftSleeve, m.rightPants, m.leftPants};
    }

    public static void capture(PlayerModel<?> m) {
        if (!ACTIVE) {
            return;
        }
        ModelPart[] ps = parts(m);
        for (int i = 0; i < ps.length; i++) {
            ModelPart p = ps[i];
            int o = i * 9;
            SNAP[o] = p.x;
            SNAP[o + 1] = p.y;
            SNAP[o + 2] = p.z;
            SNAP[o + 3] = p.xRot;
            SNAP[o + 4] = p.yRot;
            SNAP[o + 5] = p.zRot;
            SNAP[o + 6] = p.xScale;
            SNAP[o + 7] = p.yScale;
            SNAP[o + 8] = p.zScale;
        }
        snapModel = m;
    }

    public static void restore(PlayerModel<?> m) {
        if (!ACTIVE || snapModel != m) {
            return;
        }
        snapModel = null;
        ModelPart[] ps = parts(m);
        for (int i = 0; i < ps.length; i++) {
            ModelPart p = ps[i];
            int o = i * 9;
            p.x = SNAP[o];
            p.y = SNAP[o + 1];
            p.z = SNAP[o + 2];
            p.xRot = SNAP[o + 3];
            p.yRot = SNAP[o + 4];
            p.zRot = SNAP[o + 5];
            p.xScale = SNAP[o + 6];
            p.yScale = SNAP[o + 7];
            p.zScale = SNAP[o + 8];
        }
    }
}
