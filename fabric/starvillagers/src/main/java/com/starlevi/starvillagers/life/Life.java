package com.starlevi.starvillagers.life;

import com.starlevi.starscenes.api.StarScenesAPI;
import com.starlevi.starvillagers.Identity;
import com.starlevi.starvillagers.StarVillagers;
import com.starlevi.starvillagers.social.Social;
import com.starlevi.starvillagers.social.Together;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * La vida del pueblo, con el cuerpo (al estilo Touch'n Motion): los aldeanos trabajan en su puesto con
 * el gesto de su oficio, empujan las puertas al pasar, se tapan de la lluvia, se asustan con los
 * monstruos, bailan si suena musica, se sacuden al salir del agua, señalan cosas, juntan lo del piso
 * y a la tarde se sientan un rato en los escalones del pueblo.
 */
public final class Life {
    private static final Random R = new Random();

    private static final class S {
        String loop = "";
        long refresh;
        boolean wet;
        BlockPos lastDoor;
        long lastDoorAt, lastLook;
        // sentado
        BlockPos seat;
        long seatUntil;
        boolean goingToSeat, noAi;
        long goStart;
    }

    private static final Map<UUID, S> STATES = new HashMap<>();
    private static final Set<BlockPos> MUSIC = new HashSet<>();

    private Life() {
    }

    private static S state(Villager v) {
        return STATES.computeIfAbsent(v.getUUID(), k -> new S());
    }

    /** El gesto de trabajo de cada oficio. */
    private static String workAnim(String prof) {
        return switch (prof) {
            case "armorer", "weaponsmith", "toolsmith", "mason" -> "hammer";
            case "librarian" -> "read";
            case "cartographer" -> "write";
            case "cleric" -> "stir";
            case "farmer", "butcher", "fisherman", "leatherworker", "shepherd", "fletcher" -> "knead";
            default -> "";
        };
    }

    private static void loop(Villager v, S s, String anim, long now) {
        if (anim.equals(s.loop)) {
            if (!anim.isEmpty() && now >= s.refresh) {
                StarScenesAPI.play(v, anim);
                s.refresh = now + 40;
            }
            return;
        }
        s.loop = anim;
        s.refresh = now + 40;
        StarScenesAPI.play(v, anim.isEmpty() ? "idle" : anim);
    }

    public static boolean sitting(Villager v) {
        S s = STATES.get(v.getUUID());
        return s != null && s.seat != null && !s.goingToSeat;
    }

    private static void standUp(Villager v, S s) {
        if (s.seat == null) {
            return;
        }
        boolean wasSitting = !s.goingToSeat;
        s.seat = null;
        s.goingToSeat = false;
        if (wasSitting) {
            v.setNoAi(s.noAi);
            v.snapTo(v.getX(), v.getY() + 0.5, v.getZ(), v.getYRot(), 0);
        }
        s.loop = "";
        StarScenesAPI.play(v, "idle");
    }

    private static void tick(MinecraftServer server) {
        long tickNow = server.getTickCount();
        if (tickNow % 10 != 0) {
            return;
        }
        // musica sonando cerca de los jugadores (cada 2 s)
        if (tickNow % 40 == 0) {
            MUSIC.clear();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                ServerLevel level = p.level();
                int cx = p.blockPosition().getX() >> 4, cz = p.blockPosition().getZ() >> 4;
                for (int x = cx - 2; x <= cx + 2; x++) {
                    for (int z = cz - 2; z <= cz + 2; z++) {
                        if (!level.hasChunk(x, z)) {
                            continue;
                        }
                        for (BlockEntity be : level.getChunk(x, z).getBlockEntities().values()) {
                            if (be instanceof JukeboxBlockEntity j && j.getSongPlayer().isPlaying()) {
                                MUSIC.add(be.getBlockPos());
                            }
                        }
                    }
                }
            }
        }
        Set<UUID> seen = new HashSet<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerLevel level = p.level();
            for (Villager v : level.getEntitiesOfClass(Villager.class, p.getBoundingBox().inflate(48))) {
                if (!seen.add(v.getUUID()) || StarVillagers.identity(v) == null) {
                    continue;
                }
                try {
                    tickOne(level, v, level.getGameTime());
                } catch (Exception e) {
                    // que un aldeano raro no corte la vida del resto
                }
            }
        }
        if (tickNow % 1200 == 0) {
            STATES.keySet().retainAll(seen);
        }
    }

    private static void tickOne(ServerLevel level, Villager v, long now) {
        S s = state(v);
        if (Together.busy(v) || v.isTrading() || v.isSleeping() || !v.isAlive()) {
            if (s.seat != null) {
                standUp(v, s);
            }
            s.loop = "";
            return;
        }
        String prof = StarVillagers.profession(v);
        Activity act = v.getBrain().getActiveNonCoreActivity().orElse(null);
        double speed = v.getDeltaMovement().horizontalDistance();
        boolean moving = speed > 0.02 || v.getNavigation().isInProgress();

        // --- sentado en un escalon ---
        if (s.seat != null) {
            if (s.goingToSeat) {
                double d = v.position().distanceTo(Vec3.atBottomCenterOf(s.seat));
                if (d < 1.3) {
                    BlockState st = level.getBlockState(s.seat);
                    if (!(st.getBlock() instanceof StairBlock)) {
                        s.seat = null;
                        return;
                    }
                    Direction back = st.getValue(StairBlock.FACING);
                    float yaw = back.getOpposite().toYRot();
                    s.goingToSeat = false;
                    s.noAi = v.isNoAi();
                    v.getNavigation().stop();
                    v.setNoAi(true);
                    v.snapTo(s.seat.getX() + 0.5 + back.getStepX() * 0.12, s.seat.getY() + 0.5, s.seat.getZ() + 0.5 + back.getStepZ() * 0.12, yaw, 0);
                    v.setYBodyRot(yaw);
                    v.setYHeadRot(yaw);
                    s.seatUntil = now + 500 + R.nextInt(900);
                    s.loop = "";
                } else if (now - s.goStart > 300) {
                    s.seat = null; // no llego
                    s.goingToSeat = false;
                } else if (!v.getNavigation().isInProgress()) {
                    v.getNavigation().moveTo(s.seat.getX() + 0.5, s.seat.getY() + 0.5, s.seat.getZ() + 0.5, 0.5);
                }
                return;
            }
            if (now >= s.seatUntil || act == Activity.REST || act == Activity.PANIC || level.isRainingAt(v.blockPosition().above())) {
                standUp(v, s);
                return;
            }
            loop(v, s, "sit_chair", now);
            return;
        }

        // --- empujar puertas al pasar ---
        if (moving) {
            Vec3 dir = v.getDeltaMovement().horizontalDistanceSqr() > 1e-4 ? v.getDeltaMovement().normalize() : Vec3.directionFromRotation(0, v.getYRot());
            BlockPos ahead = BlockPos.containing(v.getX() + dir.x * 0.9, v.getY() + 0.5, v.getZ() + dir.z * 0.9);
            BlockState st = level.getBlockState(ahead);
            if (st.getBlock() instanceof DoorBlock && st.getValue(DoorBlock.OPEN) && (!ahead.equals(s.lastDoor) || now - s.lastDoorAt > 100)) {
                s.lastDoor = ahead;
                s.lastDoorAt = now;
                StarScenesAPI.play(v, "open_door");
                return;
            }
        }

        // --- salir del agua: se sacude ---
        boolean wet = v.isInWater();
        if (s.wet && !wet && !moving) {
            s.wet = false;
            StarScenesAPI.play(v, "shake");
            return;
        }
        s.wet = wet;

        // --- con que gesto en ronda anda ahora ---
        String want = "";
        if (act == Activity.PANIC) {
            want = "scared";
        } else if (moving && level.isRainingAt(v.blockPosition().above())) {
            want = "cover_head";
        } else if (!moving && musicNear(v)) {
            want = "dance";
        } else if (!moving && act == Activity.WORK && !v.isBaby()) {
            GlobalPos job = v.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
            if (job != null && job.dimension() == level.dimension() && job.pos().distToCenterSqr(v.position()) < 2.6 * 2.6) {
                want = workAnim(prof);
                if (!want.isEmpty()) {
                    v.getLookControl().setLookAt(Vec3.atCenterOf(job.pos()));
                }
            }
        }
        loop(v, s, want, now);
        if (!want.isEmpty()) {
            return;
        }

        // --- cosas sueltas: señalar animales o monstruos, sentarse a la tarde ---
        if (now - s.lastLook > 400 && R.nextInt(60) == 0) {
            List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, v.getBoundingBox().inflate(9),
                    e -> (e instanceof Monster || e instanceof Animal) && v.hasLineOfSight(e));
            if (!near.isEmpty()) {
                LivingEntity e = near.get(R.nextInt(near.size()));
                s.lastLook = now;
                v.getLookControl().setLookAt(e, 30, 30);
                StarScenesAPI.play(v, "point");
                String key = e instanceof Monster ? "ve_monstruo" : "ve_animal";
                String line = Social.personality(v).line(key, R).replace("%bicho%", e.getName().getString());
                if (!line.isEmpty()) {
                    StarScenesAPI.say(v, line, 3);
                }
                return;
            }
        }
        // los chicos juegan: saltan, bailan, dicen cosas
        if (v.isBaby() && moving && R.nextInt(90) == 0) {
            StarScenesAPI.play(v, R.nextBoolean() ? "happy" : "dance");
            String line = Social.personality(v).line("chico_juega", R);
            if (!line.isEmpty() && R.nextBoolean()) {
                StarScenesAPI.say(v, line, 2.5f);
            }
            return;
        }
        long t = level.getDayTime() % 24000;
        if (!v.isBaby() && (act == Activity.IDLE || act == Activity.MEET) && t > 8500 && t < 12000 && R.nextInt(120) == 0) {
            BlockPos seat = findSeat(level, v);
            if (seat != null) {
                s.seat = seat;
                s.goingToSeat = true;
                s.goStart = now;
                v.getNavigation().moveTo(seat.getX() + 0.5, seat.getY() + 0.5, seat.getZ() + 0.5, 0.5);
            }
        }
    }

    private static boolean musicNear(Villager v) {
        for (BlockPos p : MUSIC) {
            if (p.distToCenterSqr(v.position()) < 12 * 12) {
                return true;
            }
        }
        return false;
    }

    /** Un escalon de abajo libre (con aire arriba) cerca, que nadie use. */
    private static BlockPos findSeat(ServerLevel level, Villager v) {
        BlockPos c = v.blockPosition();
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-7, -2, -7), c.offset(7, 2, 7))) {
            BlockState st = level.getBlockState(p);
            if (st.getBlock() instanceof StairBlock && st.getValue(StairBlock.HALF) == Half.BOTTOM && level.getBlockState(p.above()).isAir()
                    && level.getBlockState(p.above(2)).isAir()) {
                found.add(p.immutable());
            }
        }
        for (S o : STATES.values()) {
            if (o.seat != null) {
                found.remove(o.seat);
            }
        }
        return found.isEmpty() ? null : found.get(R.nextInt(found.size()));
    }

    /** Un aldeano junto algo del piso (lo llama el mixin). */
    public static void pickedUp(Villager v) {
        if (!Together.busy(v)) {
            StarScenesAPI.play(v, StarVillagers.profession(v).equals("farmer") ? "harvest" : "pickup");
        }
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Life::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(srv -> {
            for (ServerLevel level : srv.getAllLevels()) {
                for (Map.Entry<UUID, S> e : STATES.entrySet()) {
                    if (e.getValue().seat != null && level.getEntity(e.getKey()) instanceof Villager v) {
                        standUp(v, e.getValue());
                    }
                }
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((e, level) -> {
            if (e instanceof Villager v) {
                S s = STATES.get(v.getUUID());
                if (s != null && s.seat != null) {
                    standUp(v, s);
                }
            }
        });
    }

    /** Para el identificador (no se usa aca, queda por si se necesita). */
    static Identity id(Villager v) {
        return StarVillagers.identity(v);
    }
}
