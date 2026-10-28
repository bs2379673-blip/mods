package com.annis.npc.mca;

import com.annis.npc.entity.AnnisNpcEntity;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.util.Map;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** /annisnpc mca ... (solo existe si MCA esta instalado). */
public final class McaCommands {
    private McaCommands() {
    }

    public static LiteralArgumentBuilder<ServerCommandSource> build() {
        return literal("mca")
                .then(literal("acciones").executes(ctx -> {
                    StringBuilder sb = new StringBuilder("Acciones de MCA -> animacion (segundos):");
                    for (Map.Entry<String, McaIntegration.Accion> e : McaIntegration.acciones().entrySet()) {
                        sb.append("\n ").append(e.getKey()).append(" -> ")
                                .append(e.getValue().animacion().isEmpty() ? "(nada)" : e.getValue().animacion())
                                .append(" (").append(e.getValue().segundos() == 0 ? "mientras dure" : e.getValue().segundos() + " s").append(")");
                    }
                    ctx.getSource().sendFeedback(() -> Text.literal(sb.toString()), false);
                    return 1;
                }))
                .then(literal("accion")
                        .then(argument("clave", StringArgumentType.string())
                                .then(argument("animacion", StringArgumentType.string())
                                        .executes(ctx -> setAccion(ctx.getSource(), StringArgumentType.getString(ctx, "clave"),
                                                StringArgumentType.getString(ctx, "animacion"), -1))
                                        .then(argument("segundos", IntegerArgumentType.integer(0, 3600))
                                                .executes(ctx -> setAccion(ctx.getSource(), StringArgumentType.getString(ctx, "clave"),
                                                        StringArgumentType.getString(ctx, "animacion"),
                                                        IntegerArgumentType.getInteger(ctx, "segundos")))))))
                .then(literal("recargar").executes(ctx -> {
                    McaIntegration.load();
                    ctx.getSource().sendFeedback(() -> Text.literal("Acciones de MCA recargadas de " + McaIntegration.file()), true);
                    return 1;
                }))
                .then(literal("convertir")
                        .then(argument("npc", EntityArgumentType.entities()).executes(ctx -> {
                            int n = 0;
                            for (Entity e : EntityArgumentType.getEntities(ctx, "npc")) {
                                if (e instanceof AnnisNpcEntity npc && McaIntegration.convert(npc) != null) {
                                    n++;
                                }
                            }
                            final int count = n;
                            ctx.getSource().sendFeedback(() -> Text.literal(count + " NPC convertidos en personas de MCA"), true);
                            return n;
                        })))
                .then(literal("es_aldeano")
                        .then(argument("objetivo", EntityArgumentType.entity()).executes(ctx -> {
                            boolean v = EntityArgumentType.getEntity(ctx, "objetivo") instanceof VillagerEntityMCA;
                            ctx.getSource().sendFeedback(() -> Text.literal(v ? "Es un aldeano de MCA" : "No es un aldeano de MCA"), false);
                            return v ? 1 : 0;
                        })));
    }

    private static int setAccion(ServerCommandSource src, String clave, String anim, int segundos) {
        McaIntegration.Accion old = McaIntegration.acciones().get(clave);
        int s = segundos >= 0 ? segundos : old != null ? old.segundos() : 3;
        String a = anim.equals("-") || anim.equalsIgnoreCase("nada") ? "" : anim;
        McaIntegration.setAccion(clave, a, s);
        src.sendFeedback(() -> Text.literal("MCA: " + clave + " -> " + (a.isEmpty() ? "(nada)" : a) + " (" + (s == 0 ? "mientras dure" : s + " s") + ")"), true);
        return 1;
    }

    /** Fachada sin tipos de MCA para el editor. */
    public static boolean convertNpc(AnnisNpcEntity npc) {
        return McaIntegration.convert(npc) != null;
    }
}
