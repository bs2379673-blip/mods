package com.starlevi.starvillagers.test;

import com.starlevi.starscenes.api.StarScenesAPI;
import com.starlevi.starscenes.client.ClientScenes;
import com.starlevi.starscenes.client.TalkScreen;
import com.starlevi.starscenes.talk.Dialogues;
import com.starlevi.starvillagers.Identity;
import com.starlevi.starvillagers.StarVillagers;
import com.starlevi.starvillagers.client.VillagerTextures;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Prueba de StarVillagers: aparecen aldeanos de distintos oficios (y un nene), se arman sus
 * identidades y texturas (captura), se charla con uno (captura) y se abre su comercio desde el dialogo.
 */
public class StarVillagersTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> {
            mc.options.renderDistance().set(4);
            mc.options.simulationDistance().set(5);
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
            server.runCommand("gamerule doMobSpawning false");
            String[] prof = {"farmer", "librarian", "armorer", "cleric", "nitwit"};
            for (int i = 0; i < prof.length; i++) {
                server.runCommand("summon villager " + (i * 1.4 - 3) + " -60 0 {NoAI:1b,Rotation:[0f,0f],VillagerData:{profession:\"minecraft:" + prof[i]
                        + "\",level:2,type:\"minecraft:plains\"}}");
            }
            server.runCommand("summon villager 4.2 -60 0 {NoAI:1b,Age:-24000,Rotation:[0f,0f]}");
            server.runCommand("tp @p 0.5 -59 5 180 10");
            ctx.waitTicks(60);
            server.runOnServer(s -> {
                List<Villager> vs = s.overworld().getEntitiesOfClass(Villager.class, new AABB(-10, -70, -10, 10, -50, 10));
                for (Villager v : vs) {
                    Identity id = StarVillagers.identity(v);
                    System.out.println("StarVillagers prueba: " + (id == null ? "SIN IDENTIDAD" : id.name() + (id.female() ? " (mujer)" : " (hombre)")
                            + ", ropa " + id.clothes() + ", pelo " + id.hair()));
                }
            });
            String client = ctx.computeOnClient(mc -> {
                var jem = com.starlevi.starscenes.emf.EmfModels.get("player");
                return "modelo humano " + (ClientScenes.model("humano") != null ? "cargado" : "NO") + ", texturas armadas " + VillagerTextures.count()
                        + ", modelo de EMF " + (jem == null ? "NO" : jem.parts.size() + " piezas y " + jem.lines.size() + " formulas")
                        + ", funciones desconocidas " + com.starlevi.starscenes.emf.Expr.UNKNOWN;
            });
            System.out.println("StarVillagers prueba: " + client);
            // pasar al lado: algun aldeano tiene que saludar con un globito
            server.runCommand("tp @p 0.5 -60 1.5 180 10");
            ctx.waitTicks(120);
            System.out.println("StarVillagers prueba: globitos " + ctx.computeOnClient(mc -> {
                int n = 0;
                for (var e : mc.level.entitiesForRendering()) {
                    String b = com.starlevi.starscenes.client.Bubbles.get(e.getId());
                    if (b != null) {
                        n++;
                        System.out.println("StarVillagers prueba: dice \"" + b + "\"");
                    }
                }
                return n;
            }));
            server.runCommand("tp @p 0.5 -59 5 180 10");
            ctx.waitTicks(10);
            ctx.runOnClient(mc -> mc.options.hideGui = true);
            ctx.waitTicks(5);
            ctx.takeScreenshot("s01_aldeanos");
            ctx.runOnClient(mc -> mc.options.hideGui = false);
            // charlar con el primero
            server.runOnServer(s -> {
                ServerPlayer p = s.getPlayerList().getPlayers().get(0);
                Villager v = s.overworld().getEntitiesOfClass(Villager.class, new AABB(-10, -70, -10, 10, -50, 10)).get(0);
                System.out.println("StarVillagers prueba: dialogo " + (StarScenesAPI.talk(p, v, "aldeano") ? "abierto" : "NO se abrio"));
            });
            ctx.waitTicks(70);
            System.out.println("StarVillagers prueba: pantalla " + ctx.computeOnClient(mc -> mc.screen instanceof TalkScreen ? "dialogo" : String.valueOf(mc.screen)));
            ctx.takeScreenshot("s02_charla");
            server.runOnServer(s -> Dialogues.choose(s.getPlayerList().getPlayers().get(0), 2)); // "¿Que tenes para cambiar?"
            ctx.waitTicks(20);
            System.out.println("StarVillagers prueba: comercio " + ctx.computeOnClient(mc -> mc.screen instanceof MerchantScreen ? "abierto" : String.valueOf(mc.screen)));
            ctx.setScreen(() -> null);
            // amistad, regalos, humor y memoria
            server.runOnServer(s -> {
                ServerPlayer p = s.getPlayerList().getPlayers().get(0);
                Villager v = s.overworld().getEntitiesOfClass(Villager.class, new AABB(-10, -70, -10, 10, -50, 10)).get(0);
                String before = com.starlevi.starscenes.talk.Actions.fill("%titulo%", p, v);
                StarScenesAPI.run(p, v, java.util.List.of("charlar"));
                p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CAKE));
                StarScenesAPI.run(p, v, java.util.List.of("regalar"));
                for (String k : new String[]{"personalidad", "titulo", "reaccion", "saludo", "charla", "humor", "relacion"}) {
                    System.out.println("StarVillagers prueba: %" + k + "% = " + com.starlevi.starscenes.talk.Actions.fill("%" + k + "%", p, v));
                }
                System.out.println("StarVillagers prueba: amistad antes " + before + " / despues " + com.starlevi.starvillagers.social.Social.mind(v).hearts(p.getUUID())
                        + ", condicion amistad>=10 " + StarScenesAPI.test(p, v, "amistad:>=10") + ", personalidades " + com.starlevi.starvillagers.social.Personality.ALL.size());
            });
            ctx.waitTicks(5);
            // hacer cosas juntos: abrazo (los dos hacen el gesto) y de la mano (te sigue)
            server.runOnServer(s -> {
                ServerPlayer p = s.getPlayerList().getPlayers().get(0);
                Villager v = s.overworld().getEntitiesOfClass(Villager.class, new AABB(-10, -70, -10, 10, -50, 10)).get(0);
                StarScenesAPI.run(p, v, java.util.List.of("amistad:90", "juntos:abrazar"));
                System.out.println("StarVillagers prueba: abrazo, ocupado " + com.starlevi.starvillagers.social.Together.busy(v) + ", distancia " + String.format(java.util.Locale.ROOT, "%.2f", p.distanceTo(v)));
            });
            ctx.waitTicks(15);
            System.out.println("StarVillagers prueba: gesto del jugador " + ctx.computeOnClient(mc -> com.starlevi.starscenes.client.PlayerGestures.active(mc.player.getId())));
            ctx.waitTicks(50);
            server.runOnServer(s -> {
                ServerPlayer p = s.getPlayerList().getPlayers().get(0);
                Villager v = s.overworld().getEntitiesOfClass(Villager.class, new AABB(-10, -70, -10, 10, -50, 10)).get(0);
                System.out.println("StarVillagers prueba: despues del abrazo ocupado " + com.starlevi.starvillagers.social.Together.busy(v) + ", sin IA " + v.isNoAi());
                StarScenesAPI.run(p, v, java.util.List.of("juntos:mano"));
            });
            server.runCommand("tp @p 0.5 -59 9 180 10");
            ctx.waitTicks(30);
            server.runOnServer(s -> {
                ServerPlayer p = s.getPlayerList().getPlayers().get(0);
                for (Villager v : s.overworld().getEntitiesOfClass(Villager.class, new AABB(-20, -70, -20, 20, -50, 20))) {
                    if (com.starlevi.starvillagers.social.Together.busy(v)) {
                        System.out.println("StarVillagers prueba: de la mano, distancia " + String.format(java.util.Locale.ROOT, "%.2f", p.distanceTo(v)));
                        com.starlevi.starvillagers.social.Together.stop(v);
                    }
                }
            });
            ctx.waitTicks(5);
            // los gestos se ven (tambien con el modelo de EMF)
            for (String g : new String[]{"happy", "shy", "laugh", "disgust"}) {
                server.runOnServer(s -> {
                    for (Villager v : s.overworld().getEntitiesOfClass(Villager.class, new AABB(-2, -70, -10, -1, -50, 10))) {
                        StarScenesAPI.play(v, g);
                    }
                });
                ctx.waitTicks(12);
                System.out.println("StarVillagers prueba: gesto " + g + " -> " + ctx.computeOnClient(mc -> {
                    for (var e : mc.level.entitiesForRendering()) {
                        if (e instanceof Villager v && v.getX() > -2 && v.getX() < -1) {
                            return com.starlevi.starscenes.client.NpcRenderer.gestureDebug(v);
                        }
                    }
                    return "no encontre al aldeano";
                }));
                ctx.waitTicks(40);
            }
            // cuerpo de mujer y pelo largo con fisicas (StarBody)
            System.out.println("StarVillagers prueba: cuerpo y pelo " + ctx.computeOnClient(mc -> {
                StringBuilder b = new StringBuilder();
                for (var e : mc.level.entitiesForRendering()) {
                    if (e instanceof Villager v && com.starlevi.starvillagers.StarVillagers.identity(v) != null) {
                        var id = com.starlevi.starvillagers.StarVillagers.identity(v);
                        b.append(id.name()).append(id.female() ? "(m" : "(h").append(", modelo ").append(((com.starlevi.starscenes.api.SceneActor) v).sceneModel())
                                .append(", tiras ").append(com.starlevi.starvillagers.client.VillagerHair.strips(id)).append(") ");
                    }
                }
                return b.toString();
            }));
            // familia: noviazgo, casamiento con anillo, un bebe y el arbol
            server.runOnServer(s -> {
                ServerPlayer p = s.getPlayerList().getPlayers().get(0);
                Villager v = s.overworld().getEntitiesOfClass(Villager.class, new AABB(-10, -70, -10, 10, -50, 10), x -> !x.isBaby()).get(0);
                StarScenesAPI.run(p, v, java.util.List.of("amistad:100", "familia:noviazgo"));
                p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(com.starlevi.starvillagers.StarVillagers.RING));
                StarScenesAPI.run(p, v, java.util.List.of("familia:casamiento"));
                int before = s.overworld().getEntitiesOfClass(Villager.class, new AABB(-20, -70, -20, 20, -50, 20), Villager::isBaby).size();
                StarScenesAPI.run(p, v, java.util.List.of("familia:hijo"));
                int after = s.overworld().getEntitiesOfClass(Villager.class, new AABB(-20, -70, -20, 20, -50, 20), Villager::isBaby).size();
                System.out.println("StarVillagers prueba: familia casados " + StarScenesAPI.test(p, v, "casados") + ", bebes " + before + " -> " + after
                        + ", arbol " + com.starlevi.starvillagers.family.Family.tree(v.getUUID()).size() + " personas, titulo "
                        + com.starlevi.starscenes.talk.Actions.fill("%titulo%", p, v));
                com.starlevi.starvillagers.family.Family.send(p, v.getUUID());
            });
            ctx.waitTicks(10);
            System.out.println("StarVillagers prueba: pantalla del arbol " + ctx.computeOnClient(mc -> mc.screen instanceof com.starlevi.starvillagers.client.FamilyTreeScreen));
            ctx.setScreen(() -> null);
            // biblioteca de skins de MCA
            try {
                var l = com.starlevi.starvillagers.client.library.Library.list("clothing", "", "likes", 0, 3).get(30, java.util.concurrent.TimeUnit.SECONDS);
                System.out.println("StarVillagers prueba: biblioteca " + (l == null ? "sin conexion" : l.size() + " skins" + (l.isEmpty() ? "" : ", la primera: " + l.get(0).title())));
            } catch (Exception e) {
                System.out.println("StarVillagers prueba: biblioteca fallo " + e);
            }
        }
    }
}
