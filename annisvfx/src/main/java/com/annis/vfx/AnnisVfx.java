package com.annis.vfx;

import com.annis.vfx.net.VfxPayload;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Annis VFX: efectos visuales para cinematicas. El servidor solo dice que efecto, donde y cuando;
 * cada cliente lo dibuja con el mismo reloj y la misma semilla, asi todos ven exactamente lo mismo.
 *
 * Comandos (op):
 *   /vfx arbol <pos> [quien]        escena del Arbol de la Vida (y "quien" aparece al final)
 *   /vfx hojas <pos> [cantidad]     solo hojas cayendo
 *   /vfx brillo <pos> [radio]       solo el brillo verde en el piso
 *   /vfx flores <pos> [radio]       solo flores que brotan
 *   /vfx parar                      quita todos los efectos
 */
public class AnnisVfx implements ModInitializer {
    public static final String MOD_ID = "annisvfx";
    public static final Logger LOGGER = LoggerFactory.getLogger("Annis VFX");
    /** Hasta donde se manda un efecto (bloques). */
    private static final double RANGE = 160.0;

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(VfxPayload.ID, VfxPayload.CODEC);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) -> {
            LiteralArgumentBuilder<ServerCommandSource> root = CommandManager.literal("vfx").requires(s -> s.hasPermissionLevel(2));
            root.then(CommandManager.literal("arbol")
                    .then(CommandManager.argument("pos", Vec3ArgumentType.vec3())
                            .executes(ctx -> play(ctx, "arbol", null, 0, 0))
                            .then(CommandManager.argument("quien", EntityArgumentType.entity())
                                    .executes(ctx -> play(ctx, "arbol", EntityArgumentType.getEntity(ctx, "quien"), 0, 0)))));
            root.then(CommandManager.literal("hojas")
                    .then(CommandManager.argument("pos", Vec3ArgumentType.vec3())
                            .executes(ctx -> play(ctx, "hojas", null, 16, 0))
                            .then(CommandManager.argument("cantidad", FloatArgumentType.floatArg(1, 200))
                                    .executes(ctx -> play(ctx, "hojas", null, FloatArgumentType.getFloat(ctx, "cantidad"), 0)))));
            root.then(CommandManager.literal("brillo")
                    .then(CommandManager.argument("pos", Vec3ArgumentType.vec3())
                            .executes(ctx -> play(ctx, "brillo", null, 6, 0))
                            .then(CommandManager.argument("radio", FloatArgumentType.floatArg(1, 40))
                                    .executes(ctx -> play(ctx, "brillo", null, FloatArgumentType.getFloat(ctx, "radio"), 0)))));
            root.then(CommandManager.literal("flores")
                    .then(CommandManager.argument("pos", Vec3ArgumentType.vec3())
                            .executes(ctx -> play(ctx, "flores", null, 3.5f, 0))
                            .then(CommandManager.argument("radio", FloatArgumentType.floatArg(0.5f, 20))
                                    .executes(ctx -> play(ctx, "flores", null, FloatArgumentType.getFloat(ctx, "radio"), 0)))));
            root.then(CommandManager.literal("parar").executes(ctx -> {
                ServerCommandSource src = ctx.getSource();
                send(src.getWorld(), src.getPosition(), new VfxPayload("parar", 0, 0, 0, 0, 0, -1, 0, 0), true);
                src.sendFeedback(() -> Text.literal("Efectos quitados"), false);
                return 1;
            }));
            dispatcher.register(root);
        });
        LOGGER.info("Annis VFX listo");
    }

    private static int play(CommandContext<ServerCommandSource> ctx, String effect, Entity who, float a, float b)
            throws CommandSyntaxException {
        ServerCommandSource src = ctx.getSource();
        Vec3d pos = Vec3ArgumentType.getVec3(ctx, "pos");
        ServerWorld world = src.getWorld();
        int seed = world.getRandom().nextInt();
        // empieza un poquito despues para que el paquete llegue a todos antes
        long start = world.getTime() + 2;
        VfxPayload p = new VfxPayload(effect, pos.x, pos.y, pos.z, start, seed, who == null ? -1 : who.getId(), a, b);
        int n = send(world, pos, p, false);
        src.sendFeedback(() -> Text.literal("VFX " + effect + " para " + n + " jugador(es)"), false);
        return n;
    }

    /** Lo manda a los jugadores cercanos (o a todos los del mundo). */
    private static int send(ServerWorld world, Vec3d pos, VfxPayload p, boolean everyone) {
        int n = 0;
        for (ServerPlayerEntity pl : everyone ? PlayerLookup.world(world) : PlayerLookup.around(world, pos, RANGE)) {
            ServerPlayNetworking.send(pl, p);
            n++;
        }
        return n;
    }
}
