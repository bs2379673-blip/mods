package com.tunombre.cosmeticos;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Server {
    private Server() { }

    /** ultima mascara enviada por jugador */
    private static final Map<UUID, Long> SENT = new HashMap<>();

    static long maskOf(ServerPlayer p) {
        long m = 0L;
        for (MobEffectInstance i : p.getActiveEffects())
            if (i.getEffect() instanceof CosmeticEffect c) m |= 1L << c.index;
        return m;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.getServer().getTickCount() % 10 != 0) return;
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) {
            long m = maskOf(p);
            Long old = SENT.get(p.getUUID());
            if (old == null || old != m) {
                SENT.put(p.getUUID(), m);
                Net.toAll(p.getUUID(), m);
            }
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        // al que entra le mandamos los cosmeticos de todos los que ya estan
        for (ServerPlayer o : sp.server.getPlayerList().getPlayers()) {
            long m = maskOf(o);
            if (m != 0L) Net.to(sp, o.getUUID(), m);
        }
        long m = maskOf(sp);
        SENT.put(sp.getUUID(), m);
        Net.toAll(sp.getUUID(), m);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        SENT.remove(e.getEntity().getUUID());
        Net.toAll(e.getEntity().getUUID(), 0L);
    }

    /** al morir se pierden los efectos: los cosmeticos se conservan */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone e) {
        if (!e.isWasDeath() || !(e.getEntity() instanceof ServerPlayer np)) return;
        for (MobEffectInstance i : e.getOriginal().getActiveEffects())
            if (i.getEffect() instanceof CosmeticEffect) np.addEffect(new MobEffectInstance(i.getEffect(), MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
    }

    private static void give(ServerPlayer p, Cosmetics.Def d) {
        p.addEffect(new MobEffectInstance(Cosmeticos.effect(d.index()), MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> disp = e.getDispatcher();
        var ids = (com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack>) (c, b) -> SharedSuggestionProvider.suggest(Cosmetics.ALL.stream().map(Cosmetics.Def::id), b);
        disp.register(Commands.literal("cosmetico").requires(s -> s.hasPermission(2))
                .then(Commands.literal("lista").executes(c -> {
                    StringBuilder sb = new StringBuilder("Cosmeticos (/cosmetico dar <jugadores> <id>):");
                    for (Cosmetics.Def d : Cosmetics.ALL) sb.append("\n  ").append(d.id()).append(" - ").append(d.nombre());
                    c.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
                    return Cosmetics.ALL.size();
                }))
                .then(Commands.literal("dar").then(Commands.argument("jugadores", EntityArgument.players())
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(ids).executes(c -> {
                            Cosmetics.Def d = Cosmetics.byId(StringArgumentType.getString(c, "id"));
                            if (d == null) { c.getSource().sendFailure(Component.literal("No existe ese cosmetico (usa /cosmetico lista)")); return 0; }
                            Collection<ServerPlayer> ps = EntityArgument.getPlayers(c, "jugadores");
                            for (ServerPlayer p : ps) give(p, d);
                            c.getSource().sendSuccess(() -> Component.literal("Cosmetico '" + d.nombre() + "' para " + ps.size() + " jugador(es)"), true);
                            return ps.size();
                        }))))
                .then(Commands.literal("quitar").then(Commands.argument("jugadores", EntityArgument.players())
                        .then(Commands.argument("id", StringArgumentType.word()).suggests(ids).executes(c -> {
                            Cosmetics.Def d = Cosmetics.byId(StringArgumentType.getString(c, "id"));
                            if (d == null) { c.getSource().sendFailure(Component.literal("No existe ese cosmetico")); return 0; }
                            Collection<ServerPlayer> ps = EntityArgument.getPlayers(c, "jugadores");
                            for (ServerPlayer p : ps) p.removeEffect(Cosmeticos.effect(d.index()));
                            c.getSource().sendSuccess(() -> Component.literal("Cosmetico '" + d.nombre() + "' quitado a " + ps.size() + " jugador(es)"), true);
                            return ps.size();
                        }))
                        .then(Commands.literal("todos").executes(c -> clearAll(c.getSource(), EntityArgument.getPlayers(c, "jugadores"))))))
                .then(Commands.literal("limpiar").then(Commands.argument("jugadores", EntityArgument.players())
                        .executes(c -> clearAll(c.getSource(), EntityArgument.getPlayers(c, "jugadores"))))));
    }

    private static int clearAll(CommandSourceStack src, Collection<ServerPlayer> ps) {
        for (ServerPlayer p : ps) for (Cosmetics.Def d : Cosmetics.ALL) p.removeEffect(Cosmeticos.effect(d.index()));
        src.sendSuccess(() -> Component.literal("Cosmeticos quitados a " + ps.size() + " jugador(es)"), true);
        return ps.size();
    }
}
