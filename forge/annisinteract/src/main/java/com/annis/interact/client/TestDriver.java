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
    private static int waitTicks;
    private static boolean joinTried;

    private TestDriver() {
    }

    public static void init() {
        if (!"1".equals(System.getProperty("annis.test"))) {
            return;
        }
        AnnisInteract.LOGGER.info("Modo prueba visual activado");
        // si a los 25 s no entro solo al servidor (alguna pantalla en el medio), entra a mano
        com.annis.npc.compat.ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            // la guia de bienvenida de Punchy Moves tapa las fotos: se cierra sola en la prueba
            if (mc.screen != null && mc.screen.getClass().getName().startsWith("punchymoves.")) {
                AnnisInteract.LOGGER.info("[TEST] cierro pantalla de Punchy: {}", mc.screen.getClass().getName());
                mc.setScreen(null);
            }
            if (mc.level != null || joinTried) {
                return;
            }
            if (++waitTicks == 500) {
                joinTried = true;
                AnnisInteract.LOGGER.info("[TEST] sin entrar al servidor; pantalla actual: {}", mc.screen);
                Screenshot.grab(mc.gameDirectory, "zz_antes_de_entrar.png", mc.getMainRenderTarget(), t -> { });
                net.minecraft.client.gui.screens.ConnectScreen.startConnecting(
                        new net.minecraft.client.gui.screens.TitleScreen(), mc,
                        net.minecraft.client.multiplayer.resolver.ServerAddress.parseString("localhost:25565"),
                        new net.minecraft.client.multiplayer.ServerData("test", "localhost:25565", false), false);
            }
        });
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
