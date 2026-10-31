package com.annis.compat.client;

import com.annis.compat.AnnisCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Locale;

/**
 * Solo en la prueba automatica (-Dannis.test): ordenes por chat "[TEST] estacion anvil",
 * "[TEST] accion", "[TEST] estacion ninguna", "[TEST] usar x y z" (abre un bloque como si
 * el jugador hiciera clic derecho), "[TEST] cerrar".
 */
public final class TestHook {
    private TestHook() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener((ClientChatReceivedEvent e) -> {
            String s = e.getMessage().getString();
            int i = s.indexOf("[TEST] ");
            if (i < 0) {
                return;
            }
            String[] cmd = s.substring(i + 7).trim().split(" +");
            Minecraft mc = Minecraft.getInstance();
            switch (cmd[0]) {
                case "estacion" -> {
                    String k = cmd.length > 1 ? cmd[1].toLowerCase(Locale.ROOT) : "ninguna";
                    CinematicModule.forced = k.equals("ninguna") ? null : k;
                    CinematicModule.forcedActionAt = -1;
                    AnnisCompat.LOGGER.info("[TEST] estacion forzada: {}", CinematicModule.forced);
                }
                case "accion" -> CinematicModule.forcedActionAt = System.nanoTime();
                case "usar" -> mc.execute(() -> {
                    if (mc.player == null || mc.gameMode == null || cmd.length < 4) {
                        return;
                    }
                    BlockPos pos = new BlockPos(Integer.parseInt(cmd[1]), Integer.parseInt(cmd[2]), Integer.parseInt(cmd[3]));
                    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
                    var r = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
                    AnnisCompat.LOGGER.info("[TEST] usar {} -> {} (pantalla {})", pos, r, mc.screen);
                });
                case "cerrar" -> mc.execute(() -> {
                    if (mc.player != null) {
                        mc.player.closeContainer();
                    }
                    mc.setScreen(null);
                });
                default -> {
                }
            }
        });
    }
}
