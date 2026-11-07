package com.starlevi.starvillagers.client;

import com.starlevi.starbody.Traits;
import com.starlevi.starbody.client.HairMesh;
import com.starlevi.starbody.client.HairRender;
import com.starlevi.starbody.client.HairSim;
import com.starlevi.starscenes.api.StarScenesClientAPI;
import com.starlevi.starvillagers.Identity;
import com.starlevi.starvillagers.StarVillagers;
import net.minecraft.world.entity.npc.villager.Villager;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Con StarBody instalado, el pelo largo de los aldeanos (lo que cae a los costados y atras de la
 * cabeza) se dibuja en tiras con las fisicas suaves de StarBody. Se arma solo desde la capa de pelo.
 */
public final class VillagerHair {
    private static final Map<String, HairMesh> MESHES = new HashMap<>();
    private static final Map<Villager, HairSim> SIMS = new WeakHashMap<>();

    private VillagerHair() {
    }

    public static int strips(Identity id) {
        HairMesh m = mesh(id);
        return m == null ? 0 : m.strips.size();
    }

    private static HairMesh mesh(Identity id) {
        return MESHES.computeIfAbsent(id.hair(), k -> {
            Traits t = new Traits();
            for (int[] p : VillagerTextures.hairPixels(id)) {
                t.hair(p[0], p[1], true);
            }
            return new HairMesh(t);
        });
    }

    static void init() {
        VillagerTextures.strips = true;
        StarScenesClientAPI.addHeadLayer((e, partial, ps, collector, light) -> {
            if (!(e instanceof Villager v)) {
                return;
            }
            Identity id = StarVillagers.identity(v);
            if (id == null) {
                return;
            }
            if (!v.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty()) {
                return; // con casco, sin pelo largo
            }
            HairMesh mesh = mesh(id);
            if (mesh.strips.isEmpty()) {
                return;
            }
            HairSim sim = SIMS.computeIfAbsent(v, k -> new HairSim());
            sim.update(v, partial, 1);
            HairRender.submitHead(VillagerTextures.full(v), mesh, sim, ps, collector, light);
        });
    }

    static void clear() {
        MESHES.clear();
    }
}
