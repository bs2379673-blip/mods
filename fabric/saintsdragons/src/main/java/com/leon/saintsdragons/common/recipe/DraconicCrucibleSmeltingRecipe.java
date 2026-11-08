package com.leon.saintsdragons.common.recipe;

import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;

/** Port 1.21.11: receta de fundido del Crisol (datos propios, ya no es una receta de Minecraft). */
public record DraconicCrucibleSmeltingRecipe(
        Identifier id,
        CrucibleIngredient ingredient,
        ItemStack result,
        int requiredHeatLevel,
        int processingTime,
        int priority
) {
    public static DraconicCrucibleSmeltingRecipe fromJson(Identifier id, JsonObject json) {
        CrucibleIngredient ingredient = CrucibleIngredient.parse(GsonHelper.getNonNull(json, "ingredient"));
        ItemStack result = CrucibleIngredient.stack(GsonHelper.getAsJsonObject(json, "result"));
        return new DraconicCrucibleSmeltingRecipe(id, ingredient, result, readHeatLevel(json), readProcessingTime(json), readPriority(json));
    }

    static int readHeatLevel(JsonObject json) {
        int level = GsonHelper.getAsInt(json, "required_heat_level", 1);
        if (level < 1 || level > 3) {
            throw new IllegalArgumentException("required_heat_level must be between 1 and 3");
        }
        return level;
    }

    static int readProcessingTime(JsonObject json) {
        int processingTime = GsonHelper.getAsInt(json, "processing_time", 200);
        if (processingTime <= 0) {
            throw new IllegalArgumentException("processing_time must be greater than zero");
        }
        return processingTime;
    }

    static int readPriority(JsonObject json) {
        return GsonHelper.getAsInt(json, "priority", 0);
    }
}
