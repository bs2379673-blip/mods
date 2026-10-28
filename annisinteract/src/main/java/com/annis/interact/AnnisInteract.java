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
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
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

        PayloadTypeRegistry.playC2S().register(RequestPayload.ID, RequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AnswerPayload.ID, AnswerPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(InvitePayload.ID, InvitePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AnchorPayload.ID, AnchorPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DownedPayload.ID, DownedPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SessionPayload.ID, SessionPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(RequestPayload.ID, (p, ctx) ->
                Sessions.request(ctx.player(), p.interaction(), p.targetId()));
        ServerPlayNetworking.registerGlobalReceiver(AnswerPayload.ID, (p, ctx) ->
                Sessions.answer(ctx.player(), p.accept()));

        ServerLivingEntityEvents.ALLOW_DEATH.register(Downed::allowDeath);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            Downed.onDeath(entity);
            Sessions.endFor(entity, null);
        });
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((oldP, newP, alive) ->
                Downed.onRespawn(oldP, newP));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Sessions.tick(server);
            Downed.tick(server);
        });
        EntityTrackingEvents.START_TRACKING.register(Sessions::onStartTracking);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Sessions.endFor(handler.player, null);
            Downed.onDisconnect(handler.player);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            Sessions.endAll();
            Downed.clearAll();
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) -> dispatcher.register(
                CommandManager.literal("interaccion")
                        .then(CommandManager.literal("aceptar").executes(ctx -> answer(ctx, true)))
                        .then(CommandManager.literal("rechazar").executes(ctx -> answer(ctx, false)))
                        .then(CommandManager.literal("parar").executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            Sessions.endFor(p, "Terminado");
                            return 1;
                        }))
                        .then(CommandManager.literal("lista").executes(ctx -> {
                            StringBuilder sb = new StringBuilder("Interacciones:");
                            for (Interaction i : Interaction.all()) {
                                sb.append(' ').append(i.id());
                            }
                            ctx.getSource().sendFeedback(() -> Text.literal(sb.toString()), false);
                            return 1;
                        }))
                        .then(CommandManager.literal("recargar").requires(s -> s.hasPermissionLevel(2)).executes(ctx -> {
                            InteractConfig.load();
                            ctx.getSource().sendFeedback(() -> Text.literal("Configuracion recargada: " + InteractConfig.file()), true);
                            return 1;
                        }))
                        .then(CommandManager.literal("caer").requires(s -> s.hasPermissionLevel(2))
                                .then(CommandManager.argument("quien", EntityArgumentType.entity()).executes(ctx -> {
                                    Entity e = EntityArgumentType.getEntity(ctx, "quien");
                                    if (!(e instanceof LivingEntity l) || Downed.isDowned(l)) {
                                        return 0;
                                    }
                                    Downed.down(l, l.getDamageSources().generic());
                                    return 1;
                                })))
                        .then(CommandManager.literal("levantar").requires(s -> s.hasPermissionLevel(2))
                                .then(CommandManager.argument("quien", EntityArgumentType.entity()).executes(ctx -> {
                                    Entity e = EntityArgumentType.getEntity(ctx, "quien");
                                    if (!(e instanceof LivingEntity l) || !Downed.isDowned(l)) {
                                        return 0;
                                    }
                                    Sessions.endFor(l, null);
                                    Downed.revive(l, null);
                                    com.annis.npc.link.EntityLinks.stop(l);
                                    return 1;
                                })))
                        .then(CommandManager.literal("hacer").requires(s -> s.hasPermissionLevel(2))
                                .then(CommandManager.argument("tipo", StringArgumentType.word())
                                        .suggests((c, b) -> {
                                            for (Interaction i : Interaction.all()) {
                                                b.suggest(i.id());
                                            }
                                            return b.buildFuture();
                                        })
                                        .then(CommandManager.argument("a", EntityArgumentType.entity())
                                                .then(CommandManager.argument("b", EntityArgumentType.entity())
                                                        .executes(AnnisInteract::force)))))));

        LOGGER.info("Annis Interacciones listo ({} interacciones{})", Interaction.all().size(), MCA ? ", con MCA" : "");
    }

    private static int answer(CommandContext<ServerCommandSource> ctx, boolean yes) throws CommandSyntaxException {
        Sessions.answer(ctx.getSource().getPlayerOrThrow(), yes);
        return 1;
    }

    /** /interaccion hacer <tipo> <a> <b>: sin invitacion ni corazones (para escenas). */
    private static int force(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Interaction type = Interaction.get(StringArgumentType.getString(ctx, "tipo"));
        Entity a = EntityArgumentType.getEntity(ctx, "a");
        Entity b = EntityArgumentType.getEntity(ctx, "b");
        if (type == null) {
            ctx.getSource().sendError(Text.literal("No existe esa interaccion (/interaccion lista)"));
            return 0;
        }
        if (!(a instanceof LivingEntity la) || !(b instanceof LivingEntity lb) || a == b || a.getWorld() != b.getWorld()) {
            ctx.getSource().sendError(Text.literal("Hacen falta dos seres vivos distintos en el mismo mundo"));
            return 0;
        }
        String problem = Sessions.check(type, la, lb);
        if (problem != null) {
            ctx.getSource().sendError(Text.literal(problem));
            return 0;
        }
        if (!Sessions.start(type, la, lb)) {
            ctx.getSource().sendError(Text.literal("No se pudo (falta espacio?)"));
            return 0;
        }
        ctx.getSource().sendFeedback(() -> Text.literal(type.name() + ": " + Sessions.name(a) + " y " + Sessions.name(b)), true);
        return 1;
    }
}
