package com.starlevi.starframes.test;

import com.starlevi.starframes.block.ScreenBlockEntity;
import com.starlevi.starframes.channel.Channels;
import com.starlevi.starframes.client.ClientChannels;
import com.starlevi.starframes.client.RemoteMenu;
import com.starlevi.starframes.client.ScreenMenu;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.util.Arrays;

/**
 * Prueba visual de StarFrames: dos pantallas en el mismo canal con un video generado (con marcas de
 * arriba e izquierda), una imagen en otro canal, pausa, la lista y los menus, con capturas.
 */
public class StarFramesTest implements FabricClientGameTest {
    private static final BlockPos A = new BlockPos(0, -60, 0), B = new BlockPos(5, -60, 0), C = new BlockPos(-5, -60, 0);

    @Override
    public void runTest(ClientGameTestContext ctx) {
        String dir = System.getenv("STARFRAMES_PRUEBA");
        String video = dir + "/video.mp4", image = dir + "/imagen.png";
        // poca distancia: el mundo carga rapido en la maquina de GitHub
        ctx.runOnClient(mc -> {
            mc.options.renderDistance().set(4);
            mc.options.simulationDistance().set(5);
            // pocos cuadros por segundo: el dibujado por software de la maquina de GitHub le roba
            // el procesador a la generacion del mundo
            mc.options.framerateLimit().set(20);
            mc.options.enableVsync().set(false);
        });
        try (TestSingleplayerContext sp = ctx.worldBuilder().adjustSettings(st -> {
            st.getNormalPresetList().stream().filter(w -> w.preset().is(WorldPresets.FLAT)).findFirst()
                    .or(() -> st.getAltPresetList().stream().filter(w -> w.preset().is(WorldPresets.FLAT)).findFirst())
                    .ifPresent(st::setWorldType);
            st.setGenerateStructures(false);
            st.setAllowCommands(true);
        }).create()) {
            sp.getClientWorld().waitForChunksRender();
            ctx.runOnClient(mc -> mc.options.hideGui = true);
            TestServerContext server = sp.getServer();
            server.runCommand("time set noon");
            server.runCommand("gamerule doDaylightCycle false");
            server.runCommand("weather clear");
            // tres pantallas mirando al sur; A y B en el canal "cine" (B mas grande), C en "foto"
            server.runCommand("setblock 0 -60 0 starframes:pantalla[facing=south]");
            server.runCommand("setblock 5 -60 0 starframes:pantalla[facing=south]");
            server.runCommand("setblock -5 -60 0 starframes:pantalla[facing=south]");
            server.runOnServer(s -> {
                ServerPlayer p = s.getPlayerList().getPlayers().get(0);
                var level = s.overworld();
                ScreenBlockEntity a = (ScreenBlockEntity) level.getBlockEntity(A), b = (ScreenBlockEntity) level.getBlockEntity(B),
                        c = (ScreenBlockEntity) level.getBlockEntity(C);
                Channels.getOrCreate("cine");
                Channels.getOrCreate("foto");
                a.set("canal", "cine");
                b.set("canal", "cine");
                b.set("ancho", "4");
                b.set("alto", "2.25");
                c.set("canal", "foto");
                c.set("ancho", "2");
                c.set("alto", "2");
                Channels.control(p, "cine", "agregar", video);
                Channels.control(p, "foto", "agregar", image);
            });
            server.runCommand("tp @p 0.5 -59 8.5 180 5");
            ctx.waitTicks(100);
            log(ctx, "cine");
            ctx.takeScreenshot("s01_dos_pantallas_mismo_canal");
            ctx.waitTicks(60);
            log(ctx, "cine");
            ctx.takeScreenshot("s02_tres_segundos_despues");
            // pausa desde el "control"
            server.runOnServer(s -> Channels.control(s.getPlayerList().getPlayers().get(0), "cine", "pausa", ""));
            ctx.waitTicks(40);
            log(ctx, "cine");
            ctx.takeScreenshot("s03_en_pausa");
            server.runOnServer(s -> Channels.control(s.getPlayerList().getPlayers().get(0), "cine", "pausa", ""));
            // de cerca, para ver la orientacion de la imagen
            server.runCommand("tp @p -5 -59 3.5 180 5");
            ctx.waitTicks(40);
            log(ctx, "foto");
            ctx.takeScreenshot("s04_imagen");
            // la lista y los menus
            server.runOnServer(s -> {
                ServerPlayer p = s.getPlayerList().getPlayers().get(0);
                Channels.control(p, "cine", "agregar", image);
                Channels.control(p, "cine", "agregar", "https://www.youtube.com/watch?v=jNQXAC9IVRw");
            });
            ctx.waitTicks(10);
            ctx.runOnClient(mc -> mc.options.hideGui = false);
            ctx.setScreen(() -> new RemoteMenu("cine"));
            ctx.waitTicks(20);
            ctx.takeScreenshot("s05_control_remoto");
            ctx.setScreen(() -> new ScreenMenu(A));
            ctx.waitTicks(20);
            ctx.takeScreenshot("s06_menu_pantalla");
            ctx.setScreen(() -> null);
            // siguiente de la lista: la imagen en el canal cine
            server.runOnServer(s -> Channels.control(s.getPlayerList().getPlayers().get(0), "cine", "siguiente", ""));
            ctx.runOnClient(mc -> mc.options.hideGui = true);
            server.runCommand("tp @p 0.5 -59 8.5 180 5");
            ctx.waitTicks(60);
            log(ctx, "cine");
            ctx.takeScreenshot("s07_siguiente_imagen");

            // links de internet, puestos como lo hace un jugador (menu de la pantalla > Reproducir)
            String[][] web = {{"web_mp4", "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_1MB.mp4"},
                    {"web_img", "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a9/Example.jpg/320px-Example.jpg"},
                    {"web_yt", "https://www.youtube.com/watch?v=jNQXAC9IVRw"}};
            for (int i = 0; i < web.length; i++) {
                String ch = web[i][0], url = web[i][1];
                server.runOnServer(s -> {
                    ServerPlayer p = s.getPlayerList().getPlayers().get(0);
                    Channels.getOrCreate(ch);
                    ((ScreenBlockEntity) s.overworld().getBlockEntity(B)).set("canal", ch);
                    Channels.editScreen(p, B, "url", url);
                });
                server.runCommand("tp @p 5.5 -59 8.5 180 5");
                for (int k = 0; k < 6; k++) {
                    ctx.waitTicks(40);
                    log(ctx, ch);
                }
                ctx.takeScreenshot("s" + (8 + i) + "_" + ch);
            }
        }
    }

    private static void log(ClientGameTestContext ctx, String ch) {
        String s = ctx.computeOnClient(mc -> ClientChannels.status(ch) + " " + Arrays.toString(ClientChannels.time(ch)));
        System.out.println("StarFrames prueba [" + ch + "]: " + s);
    }
}
