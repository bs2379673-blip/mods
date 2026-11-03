package com.starlevi.starspirits.server;

import com.starlevi.starspirits.Config;
import com.starlevi.starspirits.ModRegistry;
import com.starlevi.starspirits.SpiritDef;
import com.starlevi.starspirits.SpiritEntity;
import com.starlevi.starspirits.item.SpiritItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Espiritus salvajes: aparecen muy de vez en cuando cerca de un jugador, se escapan, y si los
 * atrapas quedan en tu inventario. Para que sean tuyos hay que llevarlos a un Pilar Runico.
 */
public final class Wild {
    private Wild() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> tick(e.getServer()));
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 200 != 0 || Config.wildChancePerMinute <= 0) {
            return;
        }
        double chance = Config.wildChancePerMinute / 6.0; // revisa cada 10 segundos
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isSpectator() || p.level().dimension() != Level.OVERWORLD || p.getRandom().nextDouble() >= chance) {
                continue;
            }
            ServerLevel level = p.serverLevel();
            // uno por zona: si ya hay un salvaje cerca, no aparece otro
            if (!level.getEntitiesOfClass(SpiritEntity.class, p.getBoundingBox().inflate(80), SpiritEntity::isWild).isEmpty()) {
                continue;
            }
            for (int tries = 0; tries < 6; tries++) {
                double a = p.getRandom().nextDouble() * Math.PI * 2, r = 18 + p.getRandom().nextDouble() * 22;
                int x = Mth.floor(p.getX() + Math.cos(a) * r), z = Mth.floor(p.getZ() + Math.sin(a) * r);
                if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
                    continue;
                }
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos at = new BlockPos(x, y, z);
                if (!level.getFluidState(at.below()).isEmpty() || !level.getBlockState(at).isAir()) {
                    continue;
                }
                SpiritDef d = SpiritLibrary.roll(level, at, p.getRandom());
                if (d != null && spawn(level, Vec3.atBottomCenterOf(at), d.id) != null) {
                    p.displayClientMessage(Component.literal("✨ Sentís una presencia mágica cerca…").withStyle(ChatFormatting.LIGHT_PURPLE), true);
                    level.playSound(null, at, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 1.2f, 1.5f);
                }
                break;
            }
        }
    }

    /** Pone un espiritu salvaje en el mundo. */
    public static SpiritEntity spawn(ServerLevel level, Vec3 at, String type) {
        SpiritEntity s = ModRegistry.SPIRIT.get().create(level);
        if (s == null) {
            return null;
        }
        s.setSpiritType(type);
        s.setWild(true);
        s.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360, 0);
        s.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.EVENT, null);
        level.addFreshEntity(s);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.4, at.z, 10, 0.3, 0.3, 0.3, 0.03);
        return s;
    }

    /** El jugador lo atrapo: pasa a su inventario. */
    public static void capture(ServerPlayer p, SpiritEntity s) {
        if (p.distanceTo(s) > 3.5f) {
            return;
        }
        ItemStack st = SpiritItem.make(s.spiritType());
        if (!p.getInventory().add(st)) {
            p.drop(st, false);
        }
        ServerLevel sl = p.serverLevel();
        sl.sendParticles(ParticleTypes.WAX_ON, s.getX(), s.getY() + 0.4, s.getZ(), 14, 0.3, 0.3, 0.3, 0.05);
        sl.playSound(null, s.blockPosition(), SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.PLAYERS, 1f, 1.4f);
        p.displayClientMessage(Component.literal("✨ ¡Atrapaste a " + Caravan.displayName(s.spiritType())
                + "! Llevalo a un Pilar Rúnico para que sea tuyo").withStyle(ChatFormatting.GOLD), true);
        s.discard();
    }

    /** Se escapa (lo soltaron): vuelve a ser salvaje y sale corriendo. */
    public static void release(ServerLevel level, Vec3 at, String type) {
        SpiritEntity s = spawn(level, at, type);
        if (s != null) {
            level.playSound(null, BlockPos.containing(at), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1f, 1.8f);
            AABB box = new AABB(BlockPos.containing(at)).inflate(8);
            for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, box)) {
                p.displayClientMessage(Component.literal("💨 ¡" + Caravan.displayName(type) + " se escapó!"), true);
            }
        }
    }
}
