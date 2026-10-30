package com.annis.interact;

import com.annis.interact.net.AnchorPayload;
import com.annis.interact.net.AnswerPayload;
import com.annis.interact.net.DownedPayload;
import com.annis.interact.net.InvitePayload;
import com.annis.interact.net.RequestPayload;
import com.annis.interact.net.SessionPayload;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.annis.npc.compat.ModInitializer;
import com.annis.npc.compat.CommandRegistrationCallback;
import com.annis.npc.compat.ServerLivingEntityEvents;
import com.annis.npc.compat.ServerLifecycleEvents;
import com.annis.npc.compat.ServerTickEvents;
import com.annis.npc.compat.EntityTrackingEvents;
import com.annis.npc.compat.PayloadTypeRegistry;
import com.annis.npc.compat.ServerPlayConnectionEvents;
import com.annis.npc.compat.ServerPlayNetworking;
import com.annis.npc.compat.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Annis Interacciones: abrazos, besos en la mejilla, chocar los cinco, ir de la mano, llevar en
 * brazos, bailar en pareja, mirar las estrellas, caidos y ayudar a levantarse. Funciona entre
 * jugadores, NPC de Annis NPC y aldeanos de Minecraft Comes Alive; las animaciones van por el
 * sistema de Annis NPC (con pausa de EMF mientras duran).
 */
public class AnnisInteract implements ModInitializer {
    public static final String MOD_ID = "annisinteract";
    public static final Logger LOGGER = LoggerFactory.getLogger("Annis Interacciones");
    public static final boolean MCA = FabricLoader.getInstance().isModLoaded("mca");

    @Override
    public void onInitialize() {
        InteractConfig.load();
        CustomInteractions.load();

        PayloadTypeRegistry.playC2S().register(RequestPayload.ID, RequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AnswerPayload.ID, AnswerPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(InvitePayload.ID, InvitePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AnchorPayload.ID, AnchorPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DownedPayload.ID, DownedPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SessionPayload.ID, SessionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(com.annis.interact.net.CustomInteractionsPayload.ID, com.annis.interact.net.CustomInteractionsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(com.annis.interact.net.TouchPayload.ID, com.annis.interact.net.TouchPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(com.annis.interact.net.TouchSyncPayload.ID, com.annis.interact.net.TouchSyncPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(RequestPayload.ID, (p, ctx) ->
                Sessions.request(ctx.player(), p.interaction(), p.targetId()));
        ServerPlayNetworking.registerGlobalReceiver(AnswerPayload.ID, (p, ctx) ->
                Sessions.answer(ctx.player(), p.accept()));
        ServerPlayNetworking.registerGlobalReceiver(com.annis.interact.net.TouchPayload.ID, (p, ctx) ->
                Sessions.touch(ctx.player(), p));

        Downed.initForge();
        ServerLivingEntityEvents.ALLOW_DEATH.register(Downed::allowDeath);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            Downed.onDeath(entity);
            Sessions.endFor(entity, null);
        });
        com.annis.npc.compat.ServerPlayerEvents.AFTER_RESPAWN.register((oldP, newP, alive) ->
                Downed.onRespawn(oldP, newP));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Sessions.tick(server);
            Downed.tick(server);
        });
        EntityTrackingEvents.START_TRACKING.register(Sessions::onStartTracking);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> CustomInteractions.sendTo(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Sessions.endFor(handler.player, null);
            Downed.onDisconnect(handler.player);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            Sessions.endAll();
            Downed.clearAll();
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) -> dispatcher.register(
                Commands.literal("interaccion")
                        .then(Commands.literal("aceptar").executes(ctx -> answer(ctx, true)))
                        .then(Commands.literal("rechazar").executes(ctx -> answer(ctx, false)))
                        .then(Commands.literal("parar").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            Sessions.endFor(p, "Terminado");
                            return 1;
                        }))
                        .then(Commands.literal("lista").executes(ctx -> {
                            StringBuilder sb = new StringBuilder("Interacciones:");
                            for (Interaction i : Interaction.all()) {
                                sb.append(' ').append(i.id());
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
                            return 1;
                        }))
                        .then(Commands.literal("recargar").requires(s -> s.hasPermission(2)).executes(ctx -> {
                            InteractConfig.load();
                            CustomInteractions.load();
                            CustomInteractions.sendAll(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(() -> Component.literal("Configuracion recargada: " + InteractConfig.file()
                                    + " y " + CustomInteractions.file()), true);
                            return 1;
                        }))
                        .then(Commands.literal("caer").requires(s -> s.hasPermission(2))
                                .then(Commands.argument("quien", EntityArgument.entity()).executes(ctx -> {
                                    Entity e = EntityArgument.getEntity(ctx, "quien");
                                    if (!(e instanceof LivingEntity l) || Downed.isDowned(l)) {
                                        return 0;
                                    }
                                    Downed.down(l, l.damageSources().generic());
                                    return 1;
                                })))
                        .then(Commands.literal("levantar").requires(s -> s.hasPermission(2))
                                .then(Commands.argument("quien", EntityArgument.entity()).executes(ctx -> {
                                    Entity e = EntityArgument.getEntity(ctx, "quien");
                                    if (!(e instanceof LivingEntity l) || !Downed.isDowned(l)) {
                                        return 0;
                                    }
                                    Sessions.endFor(l, null);
                                    Downed.revive(l, null);
                                    com.annis.npc.link.EntityLinks.stop(l);
                                    return 1;
                                })))
                        .then(Commands.literal("hacer").requires(s -> s.hasPermission(2))
                                .then(Commands.argument("tipo", StringArgumentType.word())
                                        .suggests((c, b) -> {
                                            for (Interaction i : Interaction.all()) {
                                                b.suggest(i.id());
                                            }
                                            return b.buildFuture();
                                        })
                                        .then(Commands.argument("a", EntityArgument.entity())
                                                .then(Commands.argument("b", EntityArgument.entity())
                                                        .executes(AnnisInteract::force)))))));

        LOGGER.info("Annis Interacciones listo ({} interacciones{})", Interaction.all().size(), MCA ? ", con MCA" : "");
    }

    private static int answer(CommandContext<CommandSourceStack> ctx, boolean yes) throws CommandSyntaxException {
        Sessions.answer(ctx.getSource().getPlayerOrException(), yes);
        return 1;
    }

    /** /interaccion hacer <tipo> <a> <b>: sin invitacion ni corazones (para escenas). */
    private static int force(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String id = StringArgumentType.getString(ctx, "tipo");
        boolean follow = id.endsWith(Interaction.FOLLOW);
        Interaction type = Interaction.get(follow ? id.substring(0, id.length() - Interaction.FOLLOW.length()) : id);
        Entity a = EntityArgument.getEntity(ctx, "a");
        Entity b = EntityArgument.getEntity(ctx, "b");
        if (type == null) {
            ctx.getSource().sendFailure(Component.literal("No existe esa interaccion (/interaccion lista)"));
            return 0;
        }
        if (!(a instanceof LivingEntity la) || !(b instanceof LivingEntity lb) || a == b || a.level() != b.level()) {
            ctx.getSource().sendFailure(Component.literal("Hacen falta dos seres vivos distintos en el mismo mundo"));
            return 0;
        }
        String problem = Sessions.check(type, la, lb);
        if (problem != null) {
            ctx.getSource().sendFailure(Component.literal(problem));
            return 0;
        }
        if (!Sessions.start(type, la, lb, follow)) {
            ctx.getSource().sendFailure(Component.literal("No se pudo (falta espacio?)"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(type.name() + ": " + Sessions.name(a) + " y " + Sessions.name(b)), true);
        return 1;
    }
}
