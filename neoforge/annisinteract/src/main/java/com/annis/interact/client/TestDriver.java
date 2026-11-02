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
                        new net.minecraft.client.multiplayer.ServerData("test", "localhost:25565", net.minecraft.client.multiplayer.ServerData.Type.OTHER), false, null);
            }
        });
        com.annis.npc.compat.ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (!Float.isNaN(bodyOffset) && mc.player != null) {
                // cuerpo girado respecto de la camara: para ver al jugador de costado en tercera persona
                mc.player.yBodyRot = mc.player.getYRot() + bodyOffset;
                mc.player.yBodyRotO = mc.player.yBodyRot;
            }
            if (walking && mc.player != null) {
                mc.options.keyUp.setDown(true);
                if (++walkTicks % 10 == 0) {
                    AnnisInteract.LOGGER.info("[TEST] caminar: tecla={} impulso={} pos={} paso={} pantalla={}", mc.options.keyUp.isDown(),
                            mc.player.input.forwardImpulse, mc.player.position(), mc.player.walkAnimation.speed(), mc.screen);
                }
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
                case "golpe" -> {
                    if (mc.player != null) {
                        mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                    }
                }
                case "estacion" -> fakeStation(mc, cmd.length > 1 ? cmd[1] : "ninguna");
                case "cuerpo" -> bodyOffset = cmd.length > 1 && !cmd[1].equals("libre") ? Float.parseFloat(cmd[1]) : Float.NaN;
                case "caminar" -> {
                    walking = cmd.length > 1 && cmd[1].equals("si");
                    if (!walking) {
                        mc.options.keyUp.setDown(false);
                    }
                }
                case "figura" -> figura(mc, cmd);
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

    private static float bodyOffset = Float.NaN;
    private static boolean walking;
    private static int walkTicks;

    /** Prueba con Figura: "figura cargar <carpeta>" o "figura anim <nombre> si|no". */
    private static void figura(Minecraft mc, String[] cmd) {
        try {
            ClassLoader cl = TestDriver.class.getClassLoader();
            Class<?> manager = Class.forName("org.figuramc.figura.avatar.AvatarManager", true, cl);
            if (cmd.length < 2) {
                return;
            }
            if (cmd.length > 2 && cmd[1].equals("cargar")) {
                java.nio.file.Path dir = mc.gameDirectory.toPath().resolve("figura").resolve("avatars").resolve(cmd[2]);
                manager.getMethod("loadLocalAvatar", java.nio.file.Path.class).invoke(null, dir);
                AnnisInteract.LOGGER.info("[TEST] figura: cargo {}", dir);
            } else if (cmd[1].equals("quitar") && mc.player != null) {
                manager.getMethod("clearAvatars", java.util.UUID.class).invoke(null, mc.player.getUUID());
            } else if (cmd.length > 3 && cmd[1].equals("anim") && mc.player != null) {
                Object avatar = manager.getMethod("getAvatar", net.minecraft.world.entity.Entity.class).invoke(null, mc.player);
                if (avatar == null) {
                    AnnisInteract.LOGGER.warn("[TEST] figura: sin avatar");
                    return;
                }
                java.util.Map<?, ?> anims = (java.util.Map<?, ?>) avatar.getClass().getField("animations").get(avatar);
                int n = 0;
                for (Object a : anims.values()) {
                    String name = (String) a.getClass().getField("name").get(a);
                    if (name.equals(cmd[2])) {
                        a.getClass().getMethod(cmd[3].equals("si") ? "play" : "stop").invoke(a);
                        n++;
                    }
                }
                AnnisInteract.LOGGER.info("[TEST] figura: anim {} {} ({} de {})", cmd[2], cmd[3], n, anims.size());
            }
        } catch (Throwable t) {
            AnnisInteract.LOGGER.warn("[TEST] figura: {}", t.toString());
        }
    }

    /** Prueba: Cinematic Interactions cree que el jugador local esta en una estacion (sin abrir la escena). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void fakeStation(Minecraft mc, String kind) {
        if (mc.player == null) {
            return;
        }
        try {
            Class<?> kindC = Class.forName("dev.dyslexic.cinematic.station.StationKind");
            boolean none = kind.equals("ninguna");
            Object k = Enum.valueOf((Class) kindC, none ? "TABLE" : kind.toUpperCase(java.util.Locale.ROOT));
            Class<?> payC = Class.forName("dev.dyslexic.cinematic.net.AtStationPayload");
            Object pay = payC.getConstructor(int.class, java.util.Optional.class, kindC).newInstance(mc.player.getId(),
                    none ? java.util.Optional.empty() : java.util.Optional.of(mc.player.blockPosition()), k);
            Class.forName("dev.dyslexic.cinematic.client.ClientPayloads").getMethod("atStation", payC).invoke(null, pay);
            AnnisInteract.LOGGER.info("[TEST] estacion de Cinematic: {}", kind);
        } catch (Throwable t) {
            AnnisInteract.LOGGER.warn("[TEST] estacion: {}", t.toString());
        }
    }
}
