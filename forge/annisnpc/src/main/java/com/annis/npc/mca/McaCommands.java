package com.annis.npc.mca;

import com.annis.npc.entity.AnnisNpcEntity;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import forge.net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import java.util.Map;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** /annisnpc mca ... (solo existe si MCA esta instalado). */
public final class McaCommands {
    private McaCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return literal("mca")
                .then(literal("acciones").executes(ctx -> {
                    StringBuilder sb = new StringBuilder("Acciones de MCA -> animacion (segundos):");
                    for (Map.Entry<String, McaIntegration.Accion> e : McaIntegration.acciones().entrySet()) {
                        sb.append("\n ").append(e.getKey()).append(" -> ")
                                .append(e.getValue().animacion().isEmpty() ? "(nada)" : e.getValue().animacion())
                                .append(" (").append(e.getValue().segundos() == 0 ? "mientras dure" : e.getValue().segundos() + " s").append(")");
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
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
                    ctx.getSource().sendSuccess(() -> Component.literal("Acciones de MCA recargadas de " + McaIntegration.file()), true);
                    return 1;
                }))
                .then(literal("convertir")
                        .then(argument("npc", EntityArgument.entities()).executes(ctx -> {
                            int n = 0;
                            for (Entity e : EntityArgument.getEntities(ctx, "npc")) {
                                if (e instanceof AnnisNpcEntity npc && McaIntegration.convert(npc) != null) {
                                    n++;
                                }
                            }
                            final int count = n;
                            ctx.getSource().sendSuccess(() -> Component.literal(count + " NPC convertidos en personas de MCA"), true);
                            return n;
                        })))
                .then(literal("es_aldeano")
                        .then(argument("objetivo", EntityArgument.entity()).executes(ctx -> {
                            boolean v = EntityArgument.getEntity(ctx, "objetivo") instanceof VillagerEntityMCA;
                            ctx.getSource().sendSuccess(() -> Component.literal(v ? "Es un aldeano de MCA" : "No es un aldeano de MCA"), false);
                            return v ? 1 : 0;
                        })));
    }

    private static int setAccion(CommandSourceStack src, String clave, String anim, int segundos) {
        McaIntegration.Accion old = McaIntegration.acciones().get(clave);
        int s = segundos >= 0 ? segundos : old != null ? old.segundos() : 3;
        String a = anim.equals("-") || anim.equalsIgnoreCase("nada") ? "" : anim;
        McaIntegration.setAccion(clave, a, s);
        src.sendSuccess(() -> Component.literal("MCA: " + clave + " -> " + (a.isEmpty() ? "(nada)" : a) + " (" + (s == 0 ? "mientras dure" : s + " s") + ")"), true);
        return 1;
    }

    /** Fachada sin tipos de MCA para el editor. */
    public static boolean convertNpc(AnnisNpcEntity npc) {
        return McaIntegration.convert(npc) != null;
    }
}
