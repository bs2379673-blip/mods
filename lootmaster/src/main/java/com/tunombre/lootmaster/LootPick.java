package com.tunombre.lootmaster;

import com.ogaba.sa_decor.common.loot.DecorLootConfig;
import com.ogaba.sa_decor.common.loot.DecorLootConfigManager;
import com.ogaba.sa_decor.common.loot.DecorLootInteraction;
import com.ogaba.sa_decor.common.loot.DecorLootRespawn;
import com.ogaba.sa_decor.common.loot.DecorLootTargeting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Plantillas de loot de los muebles decorados: cada mueble que guarda cosas recibe la plantilla de Survivors Arsenal que le corresponde
 * (heladera → cocina, armario → civil, escritorio → oficina, contenedor de basura → basura…). El tema puede forzar otra con "loot".
 */
public final class LootPick {
    private LootPick() { }

    /** Plantilla por defecto según el nombre del bloque. Admite varias separadas por | (se sortea una que exista). */
    public static String defaultSpec(String path) {
        switch (path) {
            case "refrigerator", "fridge", "fridge_green", "fridge_red": return "kitchen";
            case "freezer": return "cold_storage";
            case "kitchen_drawer", "counter_cabinet", "wall_wooden_drawer", "wall_wooden_drawer_glass", "metal_counter_cabinet", "wall_metal_counter_cabinet": return "kitchen_drawer";
            case "oak_wooden_drawer_one", "oak_wooden_drawer_double", "birch_wooden_drawer_one", "birch_wooden_drawer_double",
                 "spruce_wooden_drawer_one", "spruce_wooden_drawer_double", "wardrobe", "wooden_shelves": return "civilian";
            case "office_desk", "cabinet_one", "cabinet_double": return "office";
            case "hospital_cabinet": return "hospital";
            case "medicinal_cabinet": return "medicinal_cabinet";
            case "medicinal_crate": return "medicinal_crate";
            case "locker", "locker_gray", "locker_green", "locker_red", "big_metal_locker": return "locker";
            case "store_shelf", "marketing_shelf", "marketing_shelf_wooden": return "store_shelf";
            case "cash_register_machine": return "cash_register_machine";
            case "vending_machine": return "vending_machine";
            case "safe": return "safe";
            case "tool_storage": return "tool_storage";
            case "attach_case": return "valuables";
            case "cardboard_box": return "cardboard_box";
            case "medium_cardboard_box": return "medium_cardboard_box";
            case "small_cardboard_box", "small_cardboard_box_stack1", "small_cardboard_box_stack2", "small_cardboard_box_stack3": return "small_cardboard_box";
            case "military_crate": return "military_crate";
            case "big_military_crate": return "big_military_crate";
            case "oak_big_wooden_crate", "birch_big_wooden_crate", "spruce_big_wooden_crate", "double_wooden_supply_crate": return "big_crate";
            case "oak_supply_crate", "birch_supply_crate", "spruce_supply_crate", "wooden_supply_crate": return "small_crate";
            case "supply_drop": return "supply_drop";
            case "ammo_box", "ammo_box_stack_2", "ammo_box_stack_3", "ammo_box_stack_4": return "ammo_box";
            case "gun_cabinet": return "gun_cabinet";
            case "small_gun_cabinet": return "small_gun_cabinet";
            case "kitchen_shelf": return "kitchen";
            case "metal_rack", "wall_metal_rack": return "hardware|garage|construction";
            case "decor_workbench": return "tool_storage";
            case "gun_case", "gun_case_green": return "gun_case";
            case "handgun_case", "handgun_case_green": return "handgun_case";
            case "dumpster", "dumpster_blue": return "dumpster";
            case "garbage_bin", "trash_can", "trash_bin", "modern_trash_bin": return "trash_can";
            case "food_crate": return "food_storage";
            default: break;
        }
        if (path.contains("police")) return "police";
        if (path.contains("ambulance")) return "hospital|pharmacy";
        if (path.contains("pickup")) return "garage|farm";
        if (path.contains("van")) return "hardware|construction|food_store";
        if (path.contains("sedan")) return "civilian|garage";
        return null;
    }

    private static String resolve(String spec, RandomSource rnd) {
        if (spec == null || spec.isBlank() || spec.equals("none")) return null;
        List<String> ok = new ArrayList<>();
        for (String s : spec.split("\\|")) {
            String id = s.trim();
            if (!id.isEmpty() && DecorLootConfigManager.has(id)) ok.add(id);
        }
        return ok.isEmpty() ? null : ok.get(rnd.nextInt(ok.size()));
    }

    /**
     * Pone la plantilla a los contenedores de la pieza recién colocada.
     * @return la plantilla usada, o null si la pieza no guarda cosas o no hay plantilla
     */
    public static String assign(ServerLevel lv, Pieces.Info in, Decor.Piece p, List<BlockPos> cells, RandomSource rnd) {
        if (p.loot() != null && p.loot().equals("none")) return null;
        String profile = resolve(p.loot(), rnd);
        if (profile == null) profile = resolve(defaultSpec(in.path), rnd);
        if (profile == null) return null;
        Set<Long> seen = new HashSet<>();
        boolean any = false;
        for (BlockPos c : cells) {
            try {
                Optional<DecorLootTargeting.Target> ot = DecorLootTargeting.findLootContainer(lv, c);
                if (ot.isEmpty()) continue;
                DecorLootTargeting.Target t = ot.get();
                if (!seen.add(t.pos().asLong())) continue;
                if (t.data().isMarkedAsLootCrate()) continue;
                DecorLootInteraction.markAsLootCrate(lv, t, profile, false, false);
                any = true;
            } catch (RuntimeException e) {
                LootMaster.LOG.warn("[LootMaster] no se pudo poner plantilla {} en {}: {}", profile, c, e.toString());
            }
        }
        return any ? profile : null;
    }

    /**
     * Activa el reinicio del loot (cada {@code minutes} minutos) en las plantillas usadas que lo tengan apagado. Las que ya lo tienen encendido,
     * o están protegidas, no se tocan. Guarda el valor original para poder volver atrás con «devolver perfiles».
     * @return las plantillas cambiadas
     */
    public static List<String> ensureRespawn(State.Data d, Set<String> ids, int minutes) {
        List<String> changed = new ArrayList<>();
        int ticks = (int) Math.max(20, Math.min(Integer.MAX_VALUE / 2, Math.round(minutes * 1200.0)));
        for (String id : ids) {
            if (d.prot.contains(id) || !DecorLootConfigManager.has(id)) continue;
            DecorLootConfig c = DecorLootConfigManager.get(id);
            if (c == null) continue;
            DecorLootRespawn r = c.respawn();
            if (r.enabled()) continue;
            if (!d.orig.containsKey(id)) {
                State.Orig og = new State.Orig();
                og.per = c.perPlayer();
                og.rEn = r.enabled();
                og.rOnly = r.onlyIfEmpty();
                og.rClear = r.clearBeforeRespawn();
                og.ticks = r.timeTicks();
                d.orig.put(id, og);
            }
            DecorLootConfigManager.setRespawn(id, true, ticks, r.onlyIfEmpty(), r.clearBeforeRespawn());
            changed.add(id);
        }
        return changed;
    }
}
