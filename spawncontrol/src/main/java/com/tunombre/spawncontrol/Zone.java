package com.tunombre.spawncontrol;

import net.minecraft.world.phys.Vec3;

/** Zona cilindrica (centro + radio, sin limite de altura) en una dimension. allow=false => zona prohibida. */
public class Zone {
    public String name = "zona";
    public String dim = "minecraft:overworld";
    public double x, y, z, r = 64;
    public boolean allow = true;

    public boolean contains(String dimension, Vec3 p) {
        if (!dim.equals(dimension)) return false;
        double dx = p.x - x, dz = p.z - z;
        return dx * dx + dz * dz <= r * r;
    }

    public String describe() {
        return name + " [" + (allow ? "permitida" : "PROHIBIDA") + "] " + dim + " (" + (int) x + ", " + (int) y + ", " + (int) z + ") radio " + (int) r;
    }
}
