package com.tunombre.obscurenpc;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Spawner de NPCs: repite un NPC (la plantilla) cada cierto tiempo hasta un maximo, mientras haya un jugador cerca. */
public class SpawnerEntity extends BlockEntity {
    public NpcData template = new NpcData();
    public String motor = "ef";
    public int intervalo = 600;     // ticks desde que muere (o desde que se activa) hasta que aparece otro
    public int maximo = 1;
    public int radio = 4;           // radio donde aparece
    public boolean activo = true;
    public int rango = 48;          // distancia de jugadores para funcionar
    private int timer = 20;

    public SpawnerEntity(BlockPos pos, BlockState state) {
        super(ObscureNpc.SPAWNER_BE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SpawnerEntity be) {
        if (!(level instanceof ServerLevel sl) || !be.activo) return;
        if (be.timer > 0) { be.timer--; return; }
        if (level.getGameTime() % 20 != 0 || level.getNearestPlayer(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, be.rango, false) == null) return;
        int alive = 0;
        for (Actor a : level.getEntitiesOfClass(Actor.class, new net.minecraft.world.phys.AABB(pos).inflate(be.radio + 24))) {
            if (pos.equals(a.spawner())) alive++;
        }
        if (alive >= be.maximo) return;
        for (int i = 0; i < 10; i++) {
            double x = pos.getX() + 0.5 + (level.random.nextDouble() * 2 - 1) * be.radio;
            double z = pos.getZ() + 0.5 + (level.random.nextDouble() * 2 - 1) * be.radio;
            BlockPos bp = BlockPos.containing(x, pos.getY() + 1, z);
            if (!level.getBlockState(bp).getCollisionShape(level, bp).isEmpty() || !level.getBlockState(bp.above()).getCollisionShape(level, bp.above()).isEmpty()) continue;
            Actor a = ObscureNpc.create(sl, be.motor, be.template.copy());
            if (a == null) return;
            a.moveTo(x, pos.getY() + 1, z, level.random.nextFloat() * 360F, 0F);
            a.setSpawner(pos);
            a.finalizeSpawn(sl, level.getCurrentDifficultyAt(bp), MobSpawnType.SPAWNER, null, null);
            sl.addFreshEntity(a);
            be.timer = be.intervalo;
            return;
        }
    }

    public static void notifyDeath(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof SpawnerEntity be) be.timer = be.intervalo;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("Template", template.toJson());
        tag.putString("Motor", motor);
        tag.putInt("Intervalo", intervalo);
        tag.putInt("Maximo", maximo);
        tag.putInt("Radio", radio);
        tag.putBoolean("Activo", activo);
        tag.putInt("Rango", rango);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Template")) template = NpcData.fromJson(tag.getString("Template"));
        motor = tag.contains("Motor") ? tag.getString("Motor") : "ef";
        intervalo = tag.contains("Intervalo") ? tag.getInt("Intervalo") : 600;
        maximo = tag.contains("Maximo") ? tag.getInt("Maximo") : 1;
        radio = tag.contains("Radio") ? tag.getInt("Radio") : 4;
        activo = !tag.contains("Activo") || tag.getBoolean("Activo");
        rango = tag.contains("Rango") ? tag.getInt("Rango") : 48;
    }
}
