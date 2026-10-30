package com.annis.interact.client;

import com.annis.interact.AnnisInteract;
import com.annis.npc.compat.ClientReceiveMessageEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/**
 * Solo para la prueba automatica (se activa con -Dannis.test=1): el servidor manda mensajes
 * "[TEST] foto nombre" o "[TEST] camara frente|atras|primera" y el cliente obedece.
 */
public final class TestDriver {
    private TestDriver() {
    }

    public static void init() {
        if (!"1".equals(System.getProperty("annis.test"))) {
            return;
        }
        AnnisInteract.LOGGER.info("Modo prueba visual activado");
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            String s = message.getString();
            int i = s.indexOf("[TEST] ");
            if (i < 0) {
                return;
            }
            String[] cmd = s.substring(i + 7).trim().split(" ");
            Minecraft mc = Minecraft.getInstance();
            switch (cmd[0]) {
                case "foto" -> {
                    String name = cmd.length > 1 ? cmd[1] : "foto";
                    Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(),
                            t -> AnnisInteract.LOGGER.info("[TEST] {}", t.getString()));
                }
                case "camara" -> mc.options.setCameraType(switch (cmd.length > 1 ? cmd[1] : "") {
                    case "frente" -> CameraType.THIRD_PERSON_FRONT;
                    case "atras" -> CameraType.THIRD_PERSON_BACK;
                    default -> CameraType.FIRST_PERSON;
                });
                case "hud" -> mc.options.hideGui = cmd.length > 1 && cmd[1].equals("no");
                case "tocar" -> {
                    if (mc.screen instanceof TouchScreen ts && cmd.length >= 5) {
                        ts.testTouch(Integer.parseInt(cmd[1]), Float.parseFloat(cmd[2]), Float.parseFloat(cmd[3]), Float.parseFloat(cmd[4]));
                    } else {
                        AnnisInteract.LOGGER.warn("[TEST] tocar: la pantalla de mimos no esta abierta ({})", mc.screen);
                    }
                }
                case "vista" -> {
                    if (mc.screen instanceof TouchScreen ts) {
                        ts.testView(cmd.length > 1 && cmd[1].equals("primera"));
                    }
                }
                case "soltar" -> {
                    if (mc.screen instanceof TouchScreen ts) {
                        ts.testRelease();
                    }
                }
                default -> {
                }
            }
        });
    }
}
