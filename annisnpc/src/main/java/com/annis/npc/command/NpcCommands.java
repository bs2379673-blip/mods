package com.annis.npc.command;

import com.annis.npc.ModEntities;
import com.annis.npc.entity.AnnisNpcEntity;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * /annisnpc spawn <skin> [slim]
 * /annisnpc skin <objetivos> <skin> [slim]
 * /annisnpc emote <objetivos> <emote> [segundos]
 * /annisnpc stop <objetivos>
 * /annisnpc stationary <objetivos> <true|false>
 */
public final class NpcCommands {
    private NpcCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("annisnpc")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(literal("spawn")
                                .then(argument("skin", StringArgumentType.string())
                                        .executes(ctx -> spawn(ctx, StringArgumentType.getString(ctx, "skin"), false))
                                        .then(argument("slim", BoolArgumentType.bool())
                                                .executes(ctx -> spawn(ctx,
                                                        StringArgumentType.getString(ctx, "skin"),
                                                        BoolArgumentType.getBool(ctx, "slim"))))))
                        .then(literal("skin")
                                .then(argument("targets", EntityArgumentType.entities())
                                        .then(argument("skin", StringArgumentType.string())
                                                .executes(ctx -> skin(ctx, null))
                                                .then(argument("slim", BoolArgumentType.bool())
                                                        .executes(ctx -> skin(ctx, BoolArgumentType.getBool(ctx, "slim")))))))
                        .then(literal("emote")
                                .then(argument("targets", EntityArgumentType.entities())
                                        .then(argument("emote", StringArgumentType.string())
                                                .executes(ctx -> emote(ctx, 5))
                                                .then(argument("seconds", IntegerArgumentType.integer(0, 3600))
                                                        .executes(ctx -> emote(ctx, IntegerArgumentType.getInteger(ctx, "seconds")))))))
                        .then(literal("stop")
                                .then(argument("targets", EntityArgumentType.entities())
                                        .executes(NpcCommands::stop)))
                        .then(literal("stationary")
                                .then(argument("targets", EntityArgumentType.entities())
                                        .then(argument("value", BoolArgumentType.bool())
                                                .executes(NpcCommands::stationary))))
                        .then(literal("group")
                                .then(argument("targets", EntityArgumentType.entities())
                                        .then(argument("group", StringArgumentType.word())
                                                .executes(NpcCommands::group))))
                        .then(literal("sync")
                                .then(argument("group", StringArgumentType.word())
                                        .executes(NpcCommands::sync)))));
    }

    private static int group(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        String group = StringArgumentType.getString(ctx, "group");
        List<AnnisNpcEntity> list = npcs(ctx);
        list.forEach(npc -> npc.setScene(group));
        ctx.getSource().sendFeedback(() -> Text.literal(list.size() + " NPC en el grupo \"" + group + "\""), true);
        return list.size();
    }

    /** Arranca a la vez (mismo tick) a todos los NPC cargados del grupo, en todo el mundo. */
    private static int sync(CommandContext<ServerCommandSource> ctx) {
        String group = StringArgumentType.getString(ctx, "group").toLowerCase(java.util.Locale.ROOT);
        ServerWorld world = ctx.getSource().getWorld();
        int tick = (int) world.getTime();
        int n = 0;
        for (Entity e : world.iterateEntities()) {
            if (e instanceof AnnisNpcEntity npc && group.equals(npc.getScene())) {
                npc.restartEmote(tick);
                n++;
            }
        }
        final int count = n;
        ctx.getSource().sendFeedback(() -> Text.literal(count + " NPC sincronizados"), true);
        return n;
    }

    private static List<AnnisNpcEntity> npcs(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        List<AnnisNpcEntity> list = new ArrayList<>();
        for (Entity entity : EntityArgumentType.getEntities(ctx, "targets")) {
            if (entity instanceof AnnisNpcEntity npc) {
                list.add(npc);
            }
        }
        return list;
    }

    private static int spawn(CommandContext<ServerCommandSource> ctx, String skin, boolean slim) {
        ServerCommandSource source = ctx.getSource();
        ServerWorld world = source.getWorld();
        AnnisNpcEntity npc = ModEntities.NPC.create(world);
        if (npc == null) {
            source.sendError(Text.literal("No se pudo crear el NPC"));
            return 0;
        }
        Vec3d pos = source.getPosition();
        float yaw = source.getRotation().y;
        npc.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, 0.0f);
        npc.setHeadYaw(yaw);
        npc.setBodyYaw(yaw);
        npc.setSkin(skin);
        npc.setSlim(slim);
        world.spawnEntity(npc);
        source.sendFeedback(() -> Text.literal("NPC creado con la skin \"" + skin + "\""), true);
        return 1;
    }

    private static int skin(CommandContext<ServerCommandSource> ctx, Boolean slim) throws CommandSyntaxException {
        String skin = StringArgumentType.getString(ctx, "skin");
        List<AnnisNpcEntity> list = npcs(ctx);
        for (AnnisNpcEntity npc : list) {
            npc.setSkin(skin);
            if (slim != null) {
                npc.setSlim(slim);
            }
        }
        ctx.getSource().sendFeedback(() -> Text.literal("Skin \"" + skin + "\" puesta en " + list.size() + " NPC"), true);
        return list.size();
    }

    private static int emote(CommandContext<ServerCommandSource> ctx, int seconds) throws CommandSyntaxException {
        String emote = StringArgumentType.getString(ctx, "emote");
        List<AnnisNpcEntity> list = npcs(ctx);
        for (AnnisNpcEntity npc : list) {
            npc.playEmote(emote, seconds * 20);
        }
        ctx.getSource().sendFeedback(() -> Text.literal("Emote \"" + emote + "\" en " + list.size() + " NPC"), true);
        return list.size();
    }

    private static int stop(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        List<AnnisNpcEntity> list = npcs(ctx);
        for (AnnisNpcEntity npc : list) {
            npc.stopEmote();
        }
        ctx.getSource().sendFeedback(() -> Text.literal("Emote detenido en " + list.size() + " NPC"), true);
        return list.size();
    }

    private static int stationary(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        boolean value = BoolArgumentType.getBool(ctx, "value");
        List<AnnisNpcEntity> list = npcs(ctx);
        for (AnnisNpcEntity npc : list) {
            npc.setStationary(value);
        }
        ctx.getSource().sendFeedback(() -> Text.literal((value ? "Quietos: " : "Libres: ") + list.size() + " NPC"), true);
        return list.size();
    }
}
