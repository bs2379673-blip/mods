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
        boolean wall; // apoyado en la pared en vez de sentado
        Direction wallDir;
        // gesto en ronda por un rato (tomar sol, barrer, calentarse)
        String timed;
        long timedUntil;
        // comida en la mano
        net.minecraft.world.item.ItemStack handBefore;
        long handUntil;
        long lastEat, lastSun, lastSleepy, lastSweep;
    }

    /** Cuando cada jugador fue saludado de lejos por cada aldeano. */
    private static final Map<UUID, Map<UUID, Long>> WAVED = new HashMap<>();
    /** Campanas tocadas hace poco (para que no suenen todo el tiempo). */
    private static final Map<BlockPos, Long> BELLS = new HashMap<>();

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
            if (!s.wall) {
                v.snapTo(v.getX(), v.getY() + 0.5, v.getZ(), v.getYRot(), 0);
            }
        }
        s.wall = false;
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
            WAVED.keySet().retainAll(seen);
            BELLS.values().removeIf(at -> server.overworld().getGameTime() - at > 6000);
        }
    }

    private static void tickOne(ServerLevel level, Villager v, long now) {
        S s = state(v);
        if (s.handBefore != null && (now >= s.handUntil || v.isTrading())) {
            if (!v.isTrading()) {
                v.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, s.handBefore);
            }
            s.handBefore = null;
        }
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
                if (d < (s.wall ? 0.9 : 1.3)) {
                    Direction back;
                    double y;
                    if (s.wall) {
                        back = s.wallDir;
                        if (!wallAt(level, s.seat, back) || !level.getBlockState(s.seat).isAir()) {
                            s.seat = null;
                            s.wall = false;
                            return;
                        }
                        y = s.seat.getY();
                    } else {
                        BlockState st = level.getBlockState(s.seat);
                        if (!(st.getBlock() instanceof StairBlock)) {
                            s.seat = null;
                            return;
                        }
                        back = st.getValue(StairBlock.FACING);
                        y = s.seat.getY() + 0.5;
                    }
                    float yaw = back.getOpposite().toYRot();
                    double off = s.wall ? 0.2 : 0.12;
                    s.goingToSeat = false;
                    s.noAi = v.isNoAi();
                    v.getNavigation().stop();
                    v.setNoAi(true);
                    v.snapTo(s.seat.getX() + 0.5 + back.getStepX() * off, y, s.seat.getZ() + 0.5 + back.getStepZ() * off, yaw, 0);
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
            if (now >= s.seatUntil || act == Activity.REST || act == Activity.PANIC || (!s.wall && level.isRainingAt(v.blockPosition().above()))) {
                standUp(v, s);
                return;
            }
            loop(v, s, s.wall ? "lean_wall" : "sit_chair", now);
            if (s.wall && R.nextInt(80) == 0) {
                say(v, "apoyado", null);
            }
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
        if (s.timed != null && (moving || now >= s.timedUntil || act == Activity.PANIC)) {
            s.timed = null;
        }
        if (act == Activity.PANIC) {
            want = "scared";
        } else if (!moving && s.timed != null) {
            want = s.timed;
        } else if (moving && level.isRainingAt(v.blockPosition().above())) {
            want = "cover_head";
        } else if (!moving && musicNear(v)) {
            want = "dance";
        } else if (!moving && !v.isBaby() && fireNear(level, v) != null) {
            BlockPos fire = fireNear(level, v);
            v.getLookControl().setLookAt(Vec3.atCenterOf(fire));
            want = "warm_hands";
            if (!"warm_hands".equals(s.loop) && R.nextInt(3) == 0) {
                say(v, "fuego", null);
            }
        } else if (!moving && act == Activity.WORK && !v.isBaby()) {
            GlobalPos job = v.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
            if (job != null && job.dimension() == level.dimension() && job.pos().distToCenterSqr(v.position()) < 2.6 * 2.6) {
                want = workAnim(prof);
                if (!want.isEmpty()) {
                    v.getLookControl().setLookAt(Vec3.atCenterOf(job.pos()));
                    hold(v, s, tool(prof), now, 30);
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
        if (extras(level, v, s, act, moving, now, t)) {
            return;
        }
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

    /** Lo nuevo de la vida diaria: saludar de lejos, la campana, comer, tomar sol, sueño, barrer, apoyarse. */
    private static boolean extras(ServerLevel level, Villager v, S s, Activity act, boolean moving, long now, long t) {
        // saludar de lejos a los amigos
        if (R.nextInt(6) == 0) {
            for (ServerPlayer p : level.players()) {
                double d = p.distanceTo(v);
                if (d < 8 || d > 22 || p.isSpectator() || Social.mind(v).hearts(p.getUUID()) < 15 || !v.hasLineOfSight(p)) {
                    continue;
                }
                Map<UUID, Long> w = WAVED.computeIfAbsent(v.getUUID(), k -> new HashMap<>());
                if (now - w.getOrDefault(p.getUUID(), -99999L) < 6000) {
                    continue;
                }
                w.put(p.getUUID(), now);
                v.getLookControl().setLookAt(p, 30, 30);
                StarScenesAPI.play(v, "wave_far");
                say(v, "saludo_lejos", p);
                return true;
            }
        }
        if (moving || v.isBaby()) {
            return false;
        }
        // en la plaza (reunion): tocar la campana de vez en cuando
        if (act == Activity.MEET && R.nextInt(40) == 0) {
            GlobalPos meet = v.getBrain().getMemory(MemoryModuleType.MEETING_POINT).orElse(null);
            if (meet != null && meet.dimension() == level.dimension() && meet.pos().distToCenterSqr(v.position()) < 2.8 * 2.8
                    && now - BELLS.getOrDefault(meet.pos(), -99999L) > 2400) {
                BlockState st = level.getBlockState(meet.pos());
                if (st.getBlock() instanceof net.minecraft.world.level.block.BellBlock bell) {
                    BELLS.put(meet.pos(), now);
                    v.getLookControl().setLookAt(Vec3.atCenterOf(meet.pos()));
                    StarScenesAPI.play(v, "ring_bell");
                    say(v, "campana", null);
                    Direction side = Direction.getApproximateNearest(v.getX() - meet.pos().getX() - 0.5, 0, v.getZ() - meet.pos().getZ() - 0.5);
                    level.getServer().execute(() -> bell.attemptToRing(v, level, meet.pos(), side.getAxis().isHorizontal() ? side : Direction.NORTH));
                    return true;
                }
            }
        }
        // mediodia: comer algo
        if (t > 5500 && t < 7000 && now - s.lastEat > 6000 && R.nextInt(50) == 0 && !v.isTrading()) {
            s.lastEat = now;
            if (s.handBefore == null && v.getMainHandItem().isEmpty()) {
                s.handBefore = v.getMainHandItem().copy();
                v.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(food(v)));
            }
            s.handUntil = now + 50;
            StarScenesAPI.play(v, "eat");
            level.playSound(null, v.blockPosition(), net.minecraft.sounds.SoundEvents.GENERIC_EAT.value(), net.minecraft.sounds.SoundSource.NEUTRAL, 0.5f, 1f);
            if (R.nextBoolean()) {
                say(v, "comer", null);
            }
            return true;
        }
        // de mañana, con sol: tomar sol un ratito
        if (t > 1500 && t < 9000 && !level.isRaining() && act != Activity.WORK && now - s.lastSun > 4000 && R.nextInt(140) == 0
                && level.canSeeSky(v.blockPosition().above())) {
            s.lastSun = now;
            s.timed = "sunbathe";
            s.timedUntil = now + 200 + R.nextInt(200);
            say(v, "sol", null);
            return true;
        }
        // de mañana temprano: barrer frente a la casa (los que no tienen oficio o los que quieren)
        if (t > 300 && t < 3000 && act != Activity.WORK && now - s.lastSweep > 8000 && R.nextInt(160) == 0) {
            s.lastSweep = now;
            s.timed = "sweep";
            s.timedUntil = now + 160 + R.nextInt(120);
            hold(v, s, net.minecraft.world.item.Items.BRUSH, now, (int) (s.timedUntil - now));
            say(v, "barrer", null);
            return true;
        }
        // de noche, antes de acostarse: sueño
        if ((act == Activity.REST || t > 12000 && t < 13500) && now - s.lastSleepy > 1200 && R.nextInt(30) == 0) {
            s.lastSleepy = now;
            StarScenesAPI.play(v, R.nextBoolean() ? "sleepy" : "yawn");
            String line = Social.personality(v).line("pensamiento_sueno", R);
            if (!line.isEmpty() && R.nextBoolean()) {
                StarScenesAPI.say(v, line, 2.5f);
            }
            return true;
        }
        // apoyarse en una pared un rato (de dia, sin nada que hacer)
        if ((act == Activity.IDLE || act == Activity.MEET) && t < 12000 && R.nextInt(150) == 0) {
            BlockPos[] w = findWall(level, v);
            if (w != null) {
                s.seat = w[0];
                s.wall = true;
                s.wallDir = Direction.getApproximateNearest(w[1].getX() - w[0].getX(), 0, w[1].getZ() - w[0].getZ());
                s.goingToSeat = true;
                s.goStart = now;
                v.getNavigation().moveTo(w[0].getX() + 0.5, w[0].getY(), w[0].getZ() + 0.5, 0.5);
                return true;
            }
        }
        return false;
    }

    /** Le pone un item en la mano por un rato (y despues le devuelve lo que tenia). */
    private static void hold(Villager v, S s, net.minecraft.world.item.Item item, long now, int ticks) {
        if (item == null || v.isTrading()) {
            return;
        }
        if (s.handBefore == null) {
            if (!v.getMainHandItem().isEmpty()) {
                return; // ya tiene algo suyo en la mano
            }
            s.handBefore = net.minecraft.world.item.ItemStack.EMPTY;
            v.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(item));
        }
        s.handUntil = Math.max(s.handUntil, now + ticks);
    }

    /** La herramienta de cada oficio mientras trabaja. */
    private static net.minecraft.world.item.Item tool(String prof) {
        return switch (prof) {
            case "armorer", "weaponsmith", "toolsmith" -> net.minecraft.world.item.Items.IRON_PICKAXE;
            case "mason" -> net.minecraft.world.item.Items.STONE_PICKAXE;
            case "librarian" -> net.minecraft.world.item.Items.BOOK;
            case "cartographer" -> net.minecraft.world.item.Items.FEATHER;
            case "cleric" -> net.minecraft.world.item.Items.STICK;
            case "farmer" -> net.minecraft.world.item.Items.WHEAT;
            case "butcher" -> net.minecraft.world.item.Items.PORKCHOP;
            case "fisherman" -> net.minecraft.world.item.Items.COD;
            case "leatherworker" -> net.minecraft.world.item.Items.LEATHER;
            case "shepherd" -> net.minecraft.world.item.Items.WHITE_WOOL;
            case "fletcher" -> net.minecraft.world.item.Items.ARROW;
            default -> null;
        };
    }

    private static net.minecraft.world.item.Item food(Villager v) {
        return switch (StarVillagers.profession(v)) {
            case "farmer" -> net.minecraft.world.item.Items.CARROT;
            case "fisherman" -> net.minecraft.world.item.Items.COOKED_COD;
            case "butcher" -> net.minecraft.world.item.Items.COOKED_BEEF;
            case "cleric" -> net.minecraft.world.item.Items.SWEET_BERRIES;
            default -> R.nextInt(3) == 0 ? net.minecraft.world.item.Items.APPLE : net.minecraft.world.item.Items.BREAD;
        };
    }

    private static void say(Villager v, String key, ServerPlayer p) {
        String line = Social.personality(v).line(key, R);
        if (line.isEmpty()) {
            return;
        }
        line = line.replace("%jugador%", p != null ? p.getName().getString() : "");
        StarScenesAPI.say(v, line, 3);
    }

    private static boolean solid(ServerLevel level, BlockPos p, Direction face) {
        return level.getBlockState(p).isFaceSturdy(level, p, face);
    }

    /** Hay pared de dos de alto pegada a p del lado dir. */
    private static boolean wallAt(ServerLevel level, BlockPos p, Direction dir) {
        BlockPos w = p.relative(dir);
        return solid(level, w, dir.getOpposite()) && solid(level, w.above(), dir.getOpposite())
                && !(level.getBlockState(w).getBlock() instanceof DoorBlock);
    }

    /** Un lugar libre junto a una pared (el lugar y el bloque de la pared), que no use nadie. */
    private static BlockPos[] findWall(ServerLevel level, Villager v) {
        BlockPos c = v.blockPosition();
        List<BlockPos[]> found = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-6, -1, -6), c.offset(6, 1, 6))) {
            if (!level.getBlockState(p).isAir() || !level.getBlockState(p.above()).isAir() || !solid(level, p.below(), Direction.UP)) {
                continue;
            }
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (wallAt(level, p, d) && level.getBlockState(p.relative(d.getOpposite())).isAir()) {
                    found.add(new BlockPos[]{p.immutable(), p.relative(d).immutable()});
                    break;
                }
            }
        }
        for (S o : STATES.values()) {
            if (o.seat != null) {
                found.removeIf(f -> f[0].equals(o.seat));
            }
        }
        return found.isEmpty() ? null : found.get(R.nextInt(found.size()));
    }

    private static BlockPos fireNear(ServerLevel level, Villager v) {
        BlockPos c = v.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-3, -1, -3), c.offset(3, 1, 3))) {
            if (net.minecraft.world.level.block.CampfireBlock.isLitCampfire(level.getBlockState(p))) {
                return p.immutable();
            }
        }
        return null;
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

    /** Deja al aldeano como estaba (parado, con lo suyo en la mano) antes de guardarlo. */
    private static void release(Villager v, S s) {
        if (s.seat != null) {
            standUp(v, s);
        }
        if (s.handBefore != null) {
            v.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, s.handBefore);
            s.handBefore = null;
        }
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Life::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(srv -> {
            for (ServerLevel level : srv.getAllLevels()) {
                for (Map.Entry<UUID, S> e : STATES.entrySet()) {
                    if (level.getEntity(e.getKey()) instanceof Villager v) {
                        release(v, e.getValue());
                    }
                }
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((e, level) -> {
            if (e instanceof Villager v) {
                S s = STATES.get(v.getUUID());
                if (s != null) {
                    release(v, s);
                }
            }
        });
    }

    /** Para el identificador (no se usa aca, queda por si se necesita). */
    static Identity id(Villager v) {
        return StarVillagers.identity(v);
    }
}
