package com.tunombre.dancers;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * /dancer spawn <modelo> [<pos>] [<escala>]
 * /dancer play <objetivos> <animacion> [<bucle>]     (animacion = ns:ruta, p. ej. mikuevent:biped/miku/static)
 * /dancer stop <objetivos>
 * /dancer scale <objetivos> <escala>
 * /dancer model <objetivos> <modelo>
 * /dancer travel <objetivos> <0..1>      (cuanto de su recorrido original hace el baile; 0 = se queda en su sitio)
 */
public class DancerCommand {
    private static final String[] KNOWN = {"mikuevent:biped/miku/static", "mikuevent:biped/miku/konton_boogie", "mikuevent:biped/miku/melt",
            "mikuevent:biped/miku/magical_cure_love_shot", "mikuevent:biped/miku/anamanaguchi_miku", "mikuevent:biped/miku/minna_miku_miku",
            "mikuevent:biped/miku/looping_the_rooms"};
    private static final SuggestionProvider<CommandSourceStack> ANIMS = (c, b) -> SharedSuggestionProvider.suggest(KNOWN, b);
    private static final SuggestionProvider<CommandSourceStack> MODELS = (c, b) -> SharedSuggestionProvider.suggest(new String[]{"miku", "mikupd", "mikupd_ef"}, b);

    public static void onRegisterCommands(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("dancer").requires(s -> s.hasPermission(2))
                .then(Commands.literal("spawn")
                        .then(Commands.argument("model", StringArgumentType.word()).suggests(MODELS)
                                .executes(c -> spawn(c, c.getSource().getPosition(), 1f))
                                .then(Commands.argument("scale", FloatArgumentType.floatArg(0.05f, 64f))
                                        .executes(c -> spawn(c, c.getSource().getPosition(), FloatArgumentType.getFloat(c, "scale"))))
                                .then(Commands.argument("pos", Vec3Argument.vec3())
                                        .executes(c -> spawn(c, Vec3Argument.getVec3(c, "pos"), 1f))
                                        .then(Commands.argument("scale", FloatArgumentType.floatArg(0.05f, 64f))
                                                .executes(c -> spawn(c, Vec3Argument.getVec3(c, "pos"), FloatArgumentType.getFloat(c, "scale")))))))
                .then(Commands.literal("play")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .then(Commands.argument("animation", ResourceLocationArgument.id()).suggests(ANIMS)
                                        .executes(c -> play(c, false))
                                        .then(Commands.argument("loop", BoolArgumentType.bool()).executes(c -> play(c, BoolArgumentType.getBool(c, "loop")))))))
                .then(Commands.literal("stop")
                        .then(Commands.argument("targets", EntityArgument.entities()).executes(DancerCommand::stop)))
                .then(Commands.literal("scale")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .then(Commands.argument("scale", FloatArgumentType.floatArg(0.05f, 64f)).executes(DancerCommand::scale))))
                .then(Commands.literal("model")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .then(Commands.argument("model", StringArgumentType.word()).suggests(MODELS).executes(DancerCommand::model))))
                .then(Commands.literal("travel")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .then(Commands.argument("amount", FloatArgumentType.floatArg(0f, 1f)).executes(DancerCommand::travel)))));
    }

    private static List<DancerEntity> targets(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        List<DancerEntity> out = new ArrayList<>();
        for (Entity en : EntityArgument.getEntities(c, "targets")) if (en instanceof DancerEntity de) out.add(de);
        return out;
    }

    private static int spawn(CommandContext<CommandSourceStack> c, Vec3 pos, float scale) {
        ServerLevel level = c.getSource().getLevel();
        DancerEntity de = Dancers.DANCER.get().create(level);
        if (de == null) return 0;
        float yaw = c.getSource().getRotation().y;
        de.moveTo(pos.x, pos.y, pos.z, yaw, 0f);
        de.setYBodyRot(yaw); de.setYHeadRot(yaw);
        de.setModelName(StringArgumentType.getString(c, "model"));
        de.setDancerScale(scale);
        level.addFreshEntity(de);
        c.getSource().sendSuccess(() -> Component.literal("Bailarina creada (" + de.getModelName() + ", escala " + scale + ")"), true);
        return 1;
    }

    private static int play(CommandContext<CommandSourceStack> c, boolean loop) throws CommandSyntaxException {
        ResourceLocation id = ResourceLocationArgument.getId(c, "animation");
        List<DancerEntity> l = targets(c);
        for (DancerEntity de : l) de.play(id.toString(), loop);
        c.getSource().sendSuccess(() -> Component.literal("Animacion " + id + " en " + l.size() + " bailarina(s)"), true);
        return l.size();
    }

    private static int stop(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        List<DancerEntity> l = targets(c);
        for (DancerEntity de : l) de.stopAnim();
        return l.size();
    }

    private static int scale(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        List<DancerEntity> l = targets(c);
        float s = FloatArgumentType.getFloat(c, "scale");
        for (DancerEntity de : l) de.setDancerScale(s);
        return l.size();
    }

    private static int model(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        List<DancerEntity> l = targets(c);
        String m = StringArgumentType.getString(c, "model");
        for (DancerEntity de : l) de.setModelName(m);
        return l.size();
    }

    private static int travel(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        List<DancerEntity> l = targets(c);
        float a = FloatArgumentType.getFloat(c, "amount");
        for (DancerEntity de : l) de.setTravel(a);
        return l.size();
    }
}
