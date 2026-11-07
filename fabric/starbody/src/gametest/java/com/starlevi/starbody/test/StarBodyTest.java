package com.starlevi.starbody.test;

import com.starlevi.starbody.Traits;
import com.starlevi.starbody.client.ClientTraits;
import com.starlevi.starbody.client.EditorScreen;
import com.starlevi.starbody.client.HairMesh;
import com.starlevi.starbody.client.SkinLayout;
import com.starlevi.starbody.client.StarBodyClient;
import com.starlevi.starbody.net.Net;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.CameraType;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Prueba de StarBody: marca el pelo de atras de la cabeza y la espalda, camina en tercera persona
 * (captura) y abre el editor (captura). Anota en el registro si se armaron las tiras y la textura.
 */
public class StarBodyTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
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
            var server = sp.getServer();
            server.runCommand("time set noon");
            server.runCommand("gamerule doDaylightCycle false");
            server.runCommand("tp @p 0.5 -60 0.5 0 10");
            // pelo: toda la parte de atras y los costados del gorro y de la cabeza (abajo), y arriba de la espalda
            String info = ctx.computeOnClient(mc -> {
                String skin = StarBodyClient.mySkin().toString();
                Traits t = new Traits();
                for (SkinLayout.Face f : SkinLayout.FACES) {
                    if (f.side() == 1) {
                        continue;
                    }
                    boolean head = f.part() == SkinLayout.Part.HEAD;
                    int from = head ? 2 : 0, to = head ? f.height() : 6;
                    if (!head && f.side() != 3) {
                        continue;
                    }
                    for (int c = 0; c < f.width(); c++) {
                        for (int r = from; r < to; r++) {
                            t.hair(f.u0() + c, f.v0() + r, true);
                        }
                    }
                }
                t.motion = 2;
                ClientTraits.put(skin, t);
                ClientPlayNetworking.send(new Net.SetTraits(skin, t.encode()));
                HairMesh m = ClientTraits.mesh(skin);
                return "skin " + skin + ", tiras " + (m == null ? 0 : m.strips.size()) + ", pixeles " + (m == null ? 0 : m.pixels);
            });
            System.out.println("StarBody prueba: " + info);
            ctx.runOnClient(mc -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                mc.options.hideGui = true;
            });
            ctx.waitTicks(20);
            // caminar y correr un poco: el pelo se levanta hacia atras
            ctx.getInput().holdKey(o -> o.keyUp);
            ctx.getInput().holdKey(o -> o.keySprint);
            ctx.waitTicks(25);
            ctx.takeScreenshot("s01_pelo_corriendo");
            ctx.getInput().releaseKey(o -> o.keySprint);
            ctx.getInput().releaseKey(o -> o.keyUp);
            String tex = ctx.computeOnClient(mc -> String.valueOf(ClientTraits.hidden(StarBodyClient.mySkin())));
            System.out.println("StarBody prueba: textura sin pelo = " + tex);
            ctx.runOnClient(mc -> {
                mc.options.hideGui = false;
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            });
            ctx.setScreen(EditorScreen::new);
            ctx.waitTicks(20);
            ctx.takeScreenshot("s02_editor");
            ctx.setScreen(() -> null);
            ctx.waitTicks(5);
            String synced = ctx.computeOnClient(mc -> String.valueOf(ClientTraits.get(StarBodyClient.mySkin().toString()) != null));
            System.out.println("StarBody prueba: rasgos guardados = " + synced);
            // ojos para Just Expressions: los pixeles extra tienen que ser copia de los ojos de la cara
            String eyes = ctx.computeOnClient(mc -> {
                Traits t = new Traits();
                t.eyes = 1;
                t.eyeRow = 4;
                t.brows = 1;
                t.browRow = 3;
                var img = ClientTraits.exportFor(StarBodyClient.mySkin(), t);
                if (img == null) {
                    return "no se pudo leer la skin";
                }
                try (img) {
                    int[] cols = {1, 2, 5, 6};
                    for (int i = 0; i < 4; i++) {
                        if (img.getPixel(4 + i, 3) != img.getPixel(8 + cols[i], 12) || img.getPixel(60 + i, 3) != img.getPixel(8 + cols[i], 11)
                                || (img.getPixel(4 + i, 2) >>> 24) != 255) {
                            return "MAL en el pixel " + i;
                        }
                    }
                    return "bien";
                }
            });
            System.out.println("StarBody prueba: ojos para Just Expressions = " + eyes);
        }
    }
}
