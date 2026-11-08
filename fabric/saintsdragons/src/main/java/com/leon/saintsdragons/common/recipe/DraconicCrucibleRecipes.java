package com.leon.saintsdragons.common.recipe;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.leon.saintsdragons.common.SaintsDragonsCommon;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.List;
import java.util.Map;

/**
 * Port 1.21.11: las recetas del Crisol se leen de data/&lt;ns&gt;/draconic_crucible_recipe/ (antes eran recetas
 * de Minecraft con "type": "saintsdragons:draconic_crucible" / "..._smelting"; el JSON es el mismo).
 */
public final class DraconicCrucibleRecipes extends com.leon.saintsdragons.util.compat.JsonReloadListener {
    public static final DraconicCrucibleRecipes INSTANCE = new DraconicCrucibleRecipes();
    private static volatile List<DraconicCrucibleShapedRecipe> shaped = List.of();
    private static volatile List<DraconicCrucibleSmeltingRecipe> smelting = List.of();

    private DraconicCrucibleRecipes() {
        super(new Gson(), "draconic_crucible_recipe");
    }

    public static List<DraconicCrucibleShapedRecipe> shaped() {
        return shaped;
    }

    public static List<DraconicCrucibleSmeltingRecipe> smelting() {
        return smelting;
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> map, ResourceManager manager, ProfilerFiller profiler) {
        java.util.ArrayList<DraconicCrucibleShapedRecipe> sh = new java.util.ArrayList<>();
        java.util.ArrayList<DraconicCrucibleSmeltingRecipe> sm = new java.util.ArrayList<>();
        for (Map.Entry<Identifier, JsonElement> e : map.entrySet()) {
            try {
                JsonObject o = e.getValue().getAsJsonObject();
                String type = o.has("type") ? o.get("type").getAsString() : "saintsdragons:draconic_crucible";
                if (type.endsWith("draconic_crucible_smelting")) {
                    sm.add(DraconicCrucibleSmeltingRecipe.fromJson(e.getKey(), o));
                } else {
                    sh.add(DraconicCrucibleShapedRecipe.fromJson(e.getKey(), o));
                }
            } catch (Exception ex) {
                SaintsDragonsCommon.LOGGER.error("Draconic Crucible recipe {}: {}", e.getKey(), ex.getMessage());
            }
        }
        shaped = List.copyOf(sh);
        smelting = List.copyOf(sm);
        SaintsDragonsCommon.LOGGER.info("Draconic Crucible: {} shaped, {} smelting recipes", sh.size(), sm.size());
    }
}
