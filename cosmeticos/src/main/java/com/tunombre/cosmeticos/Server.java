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
        disp.register(Commands.literal("dios").requires(s -> s.hasPermission(2))
                .then(Commands.literal("activar").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    for (String id : new String[]{"alas_dios", "guadana_dios"}) give(p, Cosmetics.byId(id));
                    c.getSource().sendSuccess(() -> Component.literal("Modo dios oscuro: alas y guadaña. Clic derecho con la mano vacía sobre un jugador para engancharlo; otra vez para soltarlo."), false);
                    return 1;
                }))
                .then(Commands.literal("desactivar").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    for (String id : new String[]{"alas_dios", "guadana_dios"}) p.removeEffect(Cosmeticos.effect(Cosmetics.byId(id).index()));
                    Hook h = hookOf(p);
                    if (h != null) release(h);
                    c.getSource().sendSuccess(() -> Component.literal("Modo dios oscuro desactivado."), false);
                    return 1;
                }))
                .then(Commands.literal("llevar").then(Commands.argument("jugador", EntityArgument.player()).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    ServerPlayer v = EntityArgument.getPlayer(c, "jugador");
                    if (!hasScythe(p)) { c.getSource().sendFailure(Component.literal("Necesitas la guadaña: /dios activar")); return 0; }
                    if (!grab(p, v)) { c.getSource().sendFailure(Component.literal("No se pudo (¿ya llevas a alguien?).")); return 0; }
                    return 1;
                })))
                .then(Commands.literal("soltar").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    Hook h = hookOf(p);
                    if (h == null) { c.getSource().sendFailure(Component.literal("No llevas a nadie.")); return 0; }
                    release(h);
                    return 1;
                })));
    }

    // ------------------------------------------------------------------ guadaña del dios oscuro ----
    private static boolean hasScythe(ServerPlayer p) {
        Cosmetics.Def d = Cosmetics.byId("guadana_dios");
        return d != null && p.hasEffect(Cosmeticos.effect(d.index())) && p.hasPermissions(2);
    }

    private static Hook hookOf(ServerPlayer owner) {
        for (net.minecraft.world.entity.Entity e : owner.serverLevel().getEntitiesOfClass(Hook.class, owner.getBoundingBox().inflate(16)))
            if (e instanceof Hook h && h.owner() == owner && h.isAlive()) return h;
        return null;
    }

    /** engancha a la víctima con la guadaña (sin pedirle permiso: es cosa de administradores) */
    public static boolean grab(ServerPlayer admin, ServerPlayer victim) {
        if (admin == victim || victim.isSpectator()) return false;
        if (victim.getVehicle() instanceof Hook old) release(old);
        if (hookOf(admin) != null) return false;
        Hook h = new Hook(Cosmeticos.HOOK.get(), admin.level());
        h.setOwner(admin.getId());
        h.setPos(admin.getX(), admin.getY() - 1.0, admin.getZ());
        admin.level().addFreshEntity(h);
        victim.stopRiding();
        victim.startRiding(h, true);
        victim.displayClientMessage(Component.literal("§4Te atrapó la guadaña de " + admin.getName().getString() + "."), true);
        admin.level().playSound(null, victim.blockPosition(), net.minecraft.sounds.SoundEvents.CHAIN_PLACE, net.minecraft.sounds.SoundSource.PLAYERS, 1F, 0.6F);
        return true;
    }

    public static void release(Hook h) {
        h.releasing = true;
        for (net.minecraft.world.entity.Entity e : h.getPassengers()) {
            e.stopRiding();
            e.fallDistance = 0;
            if (e instanceof ServerPlayer sp) {
                sp.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING, 100, 0, false, false, true));
                sp.displayClientMessage(Component.literal("§7Quedaste libre."), true);
            }
        }
        h.discard();
    }

    @SubscribeEvent
    public static void onInteract(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract e) {
        if (e.getLevel().isClientSide || e.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        if (!(e.getEntity() instanceof ServerPlayer admin) || !(e.getTarget() instanceof ServerPlayer victim)) return;
        if (!hasScythe(admin) || !admin.getMainHandItem().isEmpty()) return;
        if (victim.getVehicle() instanceof Hook h && h.owner() == admin) release(h);
        else if (!grab(admin, victim)) admin.displayClientMessage(Component.literal("§cYa llevas a alguien: suéltalo primero (clic derecho sobre él o /dios soltar)."), true);
        e.setCanceled(true);
        e.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
    }

    /** el enganchado no se puede bajar solo */
    @SubscribeEvent
    public static void onMount(net.minecraftforge.event.entity.EntityMountEvent e) {
        if (e.getLevel().isClientSide || !e.isDismounting()) return;
        if (e.getEntityBeingMounted() instanceof Hook h && h.isAlive() && !h.releasing && h.owner() != null) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onTickHooks(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.getServer().getTickCount() % 20 != 0) return;
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) {
            Hook h = hookOf(p);
            if (h != null && !hasScythe(p)) release(h);
        }
    }

    @SubscribeEvent
    public static void onDeathHook(net.minecraftforge.event.entity.living.LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        if (p.getVehicle() instanceof Hook h) release(h);
        Hook mine = hookOf(p);
        if (mine != null) release(mine);
    }

    @SubscribeEvent
    public static void onLogoutHook(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        if (p.getVehicle() instanceof Hook h) release(h);
        Hook mine = hookOf(p);
        if (mine != null) release(mine);
    }

    private static int clearAll(CommandSourceStack src, Collection<ServerPlayer> ps) {
        for (ServerPlayer p : ps) for (Cosmetics.Def d : Cosmetics.ALL) p.removeEffect(Cosmeticos.effect(d.index()));
        src.sendSuccess(() -> Component.literal("Cosmeticos quitados a " + ps.size() + " jugador(es)"), true);
        return ps.size();
    }
}
