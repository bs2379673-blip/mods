package com.annis.npc.command;

import com.annis.npc.ModEntities;
import com.annis.npc.entity.AnnisNpcEntity;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.annis.npc.compat.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

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
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var root = literal("annisnpc").requires(source -> source.hasPermission(2))
                    .then(literal("anim")
                            .then(argument("targets", EntityArgument.entities())
                                    .then(literal("stop").executes(ctx -> {
                                        int n = 0;
                                        for (Entity e : EntityArgument.getEntities(ctx, "targets")) {
                                            if (e instanceof net.minecraft.world.entity.LivingEntity le) {
                                                com.annis.npc.link.EntityLinks.stop(le);
                                                n++;
                                            }
                                        }
                                        return n;
                                    }))
                                    .then(argument("animacion", StringArgumentType.string())
                                            .executes(ctx -> anim(ctx, 0))
                                            .then(argument("segundos", IntegerArgumentType.integer(0, 3600))
                                                    .executes(ctx -> anim(ctx, IntegerArgumentType.getInteger(ctx, "segundos")))))))
                    .then(literal("look")
                            .then(argument("targets", EntityArgument.entities())
                                    .then(literal("clear").executes(ctx -> {
                                        int n = 0;
                                        for (Entity e : EntityArgument.getEntities(ctx, "targets")) {
                                            if (e instanceof net.minecraft.world.entity.LivingEntity le && !(e instanceof AnnisNpcEntity)) {
                                                com.annis.npc.link.EntityLinks.setLook(le, null);
                                                n++;
                                            }
                                        }
                                        final int c = n;
                                        ctx.getSource().sendSuccess(() -> Component.literal("Aspecto quitado de " + c + " entidades"), true);
                                        return n;
                                    }))
                                    .then(argument("npc", EntityArgument.entity()).executes(ctx -> {
                                        Entity src = EntityArgument.getEntity(ctx, "npc");
                                        if (!(src instanceof AnnisNpcEntity npc)) {
                                            ctx.getSource().sendFailure(Component.literal("El origen tiene que ser un NPC de Annis"));
                                            return 0;
                                        }
                                        int n = 0;
                                        for (Entity e : EntityArgument.getEntities(ctx, "targets")) {
                                            if (e instanceof net.minecraft.world.entity.LivingEntity le && !(e instanceof AnnisNpcEntity)) {
                                                com.annis.npc.link.EntityLinks.setLook(le, com.annis.npc.link.EntityLinks.lookOf(npc));
                                                n++;
                                            }
                                        }
                                        final int c = n;
                                        ctx.getSource().sendSuccess(() -> Component.literal("Aspecto de NPC puesto a " + c + " entidades"), true);
                                        return n;
                                    }))));
            if (com.annis.npc.compat.FabricLoader.getInstance().isModLoaded("mca")) {
                root = root.then(com.annis.npc.mca.McaCommands.build());
            }
            dispatcher.register(root);
            dispatcher.register(literal("annisnpc")
                        .requires(source -> source.hasPermission(2))
                        .then(literal("spawn")
                                .then(argument("skin", StringArgumentType.string())
                                        .executes(ctx -> spawn(ctx, StringArgumentType.getString(ctx, "skin"), false))
                                        .then(argument("slim", BoolArgumentType.bool())
                                                .executes(ctx -> spawn(ctx,
                                                        StringArgumentType.getString(ctx, "skin"),
                                                        BoolArgumentType.getBool(ctx, "slim"))))))
                        .then(literal("skin")
                                .then(argument("targets", EntityArgument.entities())
                                        .then(argument("skin", StringArgumentType.string())
                                                .executes(ctx -> skin(ctx, null))
                                                .then(argument("slim", BoolArgumentType.bool())
                                                        .executes(ctx -> skin(ctx, BoolArgumentType.getBool(ctx, "slim")))))))
                        .then(literal("emote")
                                .then(argument("targets", EntityArgument.entities())
                                        .then(argument("emote", StringArgumentType.string())
                                                .executes(ctx -> emote(ctx, 5))
                                                .then(argument("seconds", IntegerArgumentType.integer(0, 3600))
                                                        .executes(ctx -> emote(ctx, IntegerArgumentType.getInteger(ctx, "seconds")))))))
                        .then(literal("stop")
                                .then(argument("targets", EntityArgument.entities())
                                        .executes(NpcCommands::stop)))
                        .then(literal("stationary")
                                .then(argument("targets", EntityArgument.entities())
                                        .then(argument("value", BoolArgumentType.bool())
                                                .executes(NpcCommands::stationary))))
                        .then(literal("group")
                                .then(argument("targets", EntityArgument.entities())
                                        .then(argument("group", StringArgumentType.word())
                                                .executes(NpcCommands::group))))
                        .then(literal("music")
                                .then(argument("targets", EntityArgument.entities())
                                        .then(argument("sound", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                                                .executes(ctx -> {
                                                    String id = net.minecraft.commands.arguments.ResourceLocationArgument.getId(ctx, "sound").toString();
                                                    List<AnnisNpcEntity> list = npcs(ctx);
                                                    list.forEach(n -> n.setMusic(id));
                                                    ctx.getSource().sendSuccess(() -> Component.literal("Musica " + id + " en " + list.size() + " NPC"), true);
                                                    return list.size();
                                                }))))
                        .then(literal("sync")
                                .then(argument("group", StringArgumentType.word())
                                        .executes(NpcCommands::sync))));
        });
    }

    private static int anim(CommandContext<CommandSourceStack> ctx, int seconds) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "animacion");
        int n = 0;
        for (Entity e : EntityArgument.getEntities(ctx, "targets")) {
            if (e instanceof net.minecraft.world.entity.LivingEntity le) {
                com.annis.npc.link.EntityLinks.play(le, name, seconds * 20);
                n++;
            }
        }
        final int c = n;
        ctx.getSource().sendSuccess(() -> Component.literal("Animacion \"" + name + "\" en " + c + " entidades"), true);
        return n;
    }

    private static int group(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String group = StringArgumentType.getString(ctx, "group");
        List<AnnisNpcEntity> list = npcs(ctx);
        list.forEach(npc -> npc.setScene(group));
        ctx.getSource().sendSuccess(() -> Component.literal(list.size() + " NPC en el grupo \"" + group + "\""), true);
        return list.size();
    }

    /** Arranca a la vez (mismo tick) a todos los NPC cargados del grupo, en todo el mundo. */
    private static int sync(CommandContext<CommandSourceStack> ctx) {
        String group = StringArgumentType.getString(ctx, "group").toLowerCase(java.util.Locale.ROOT);
        ServerLevel world = ctx.getSource().getLevel();
        int tick = (int) world.getGameTime();
        int n = 0;
        AnnisNpcEntity musicSource = null;
        for (Entity e : world.getAllEntities()) {
            if (e instanceof AnnisNpcEntity npc && group.equals(npc.getScene())) {
                npc.restartEmote(tick);
                if (musicSource == null && !npc.getMusic().isEmpty()) {
                    musicSource = npc;
                }
                n++;
            }
        }
        if (musicSource != null) {
            com.annis.npc.net.NpcNetworking.playMusic(musicSource, musicSource.getMusic());
        }
        final int count = n;
        ctx.getSource().sendSuccess(() -> Component.literal(count + " NPC sincronizados"), true);
        return n;
    }

    private static List<AnnisNpcEntity> npcs(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        List<AnnisNpcEntity> list = new ArrayList<>();
        for (Entity entity : EntityArgument.getEntities(ctx, "targets")) {
            if (entity instanceof AnnisNpcEntity npc) {
                list.add(npc);
            }
        }
        return list;
    }

    private static int spawn(CommandContext<CommandSourceStack> ctx, String skin, boolean slim) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel world = source.getLevel();
        AnnisNpcEntity npc = ModEntities.NPC.create(world);
        if (npc == null) {
            source.sendFailure(Component.literal("No se pudo crear el NPC"));
            return 0;
        }
        Vec3 pos = source.getPosition();
        float yaw = source.getRotation().y;
        npc.moveTo(pos.x, pos.y, pos.z, yaw, 0.0f);
        npc.setYHeadRot(yaw);
        npc.setYBodyRot(yaw);
        npc.setSkin(skin);
        npc.setSlim(slim);
        world.addFreshEntity(npc);
        source.sendSuccess(() -> Component.literal("NPC creado con la skin \"" + skin + "\""), true);
        return 1;
    }

    private static int skin(CommandContext<CommandSourceStack> ctx, Boolean slim) throws CommandSyntaxException {
        String skin = StringArgumentType.getString(ctx, "skin");
        List<AnnisNpcEntity> list = npcs(ctx);
        for (AnnisNpcEntity npc : list) {
            npc.setSkin(skin);
            if (slim != null) {
                npc.setSlim(slim);
            }
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Skin \"" + skin + "\" puesta en " + list.size() + " NPC"), true);
        return list.size();
    }

    private static int emote(CommandContext<CommandSourceStack> ctx, int seconds) throws CommandSyntaxException {
        String emote = StringArgumentType.getString(ctx, "emote");
        List<AnnisNpcEntity> list = npcs(ctx);
        for (AnnisNpcEntity npc : list) {
            npc.playEmote(emote, seconds * 20);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Emote \"" + emote + "\" en " + list.size() + " NPC"), true);
        return list.size();
    }

    private static int stop(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        List<AnnisNpcEntity> list = npcs(ctx);
        for (AnnisNpcEntity npc : list) {
            npc.stopEmote();
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Emote detenido en " + list.size() + " NPC"), true);
        return list.size();
    }

    private static int stationary(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        boolean value = BoolArgumentType.getBool(ctx, "value");
        List<AnnisNpcEntity> list = npcs(ctx);
        for (AnnisNpcEntity npc : list) {
            npc.setStationary(value);
        }
        ctx.getSource().sendSuccess(() -> Component.literal((value ? "Quietos: " : "Libres: ") + list.size() + " NPC"), true);
        return list.size();
    }
}
