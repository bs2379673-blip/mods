package com.leon.saintsdragons.common.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Port 1.21.11: receta con forma del Crisol (datos propios, ya no es una receta de Minecraft). */
public final class DraconicCrucibleShapedRecipe {
    public static final int GRID_SIZE = 9;

    private final Identifier id;
    private final int width;
    private final int height;
    /** null = casilla vacia. */
    private final List<CrucibleIngredient> ingredients;
    private final ItemStack result;
    private final int requiredHeatLevel;
    private final int processingTime;
    private final int priority;

    public DraconicCrucibleShapedRecipe(Identifier id, int width, int height, List<CrucibleIngredient> ingredients,
                                        ItemStack result, int requiredHeatLevel, int processingTime, int priority) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.ingredients = ingredients;
        this.result = result;
        this.requiredHeatLevel = requiredHeatLevel;
        this.processingTime = processingTime;
        this.priority = priority;
    }

    public Identifier getId() {
        return this.id;
    }

    public boolean matches(Container container) {
        if (container.getContainerSize() < GRID_SIZE) {
            return false;
        }
        return findMatch(container) != null;
    }

    public boolean consumeInputs(Container container) {
        Match match = findMatch(container);
        if (match == null) {
            return false;
        }
        for (int row = 0; row < this.height; row++) {
            for (int column = 0; column < this.width; column++) {
                int ingredientColumn = match.mirrored ? this.width - column - 1 : column;
                if (this.ingredients.get(ingredientColumn + row * this.width) != null) {
                    int slot = match.offsetX + column + (match.offsetY + row) * 3;
                    container.removeItem(slot, 1);
                }
            }
        }
        return true;
    }

    @Nullable
    private Match findMatch(Container container) {
        for (int offsetY = 0; offsetY <= 3 - this.height; offsetY++) {
            for (int offsetX = 0; offsetX <= 3 - this.width; offsetX++) {
                if (matchesAt(container, offsetX, offsetY, false)) {
                    return new Match(offsetX, offsetY, false);
                }
                if (matchesAt(container, offsetX, offsetY, true)) {
                    return new Match(offsetX, offsetY, true);
                }
            }
        }
        return null;
    }

    private boolean matchesAt(Container container, int offsetX, int offsetY, boolean mirrored) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                int recipeX = column - offsetX;
                int recipeY = row - offsetY;
                CrucibleIngredient expected = null;
                if (recipeX >= 0 && recipeX < this.width && recipeY >= 0 && recipeY < this.height) {
                    int ingredientX = mirrored ? this.width - recipeX - 1 : recipeX;
                    expected = this.ingredients.get(ingredientX + recipeY * this.width);
                }

                ItemStack actual = container.getItem(column + row * 3);
                if (expected == null ? !actual.isEmpty() : !expected.test(actual)) {
                    return false;
                }
            }
        }
        return true;
    }

    public int requiredHeatLevel() {
        return this.requiredHeatLevel;
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    public ItemStack result() {
        return this.result.copy();
    }

    public int processingTime() {
        return this.processingTime;
    }

    public int priority() {
        return this.priority;
    }

    /** Lo que queda en la grilla al usar los ingredientes (baldes vacios, etc). */
    public NonNullList<ItemStack> getRemainingItems(Container container) {
        NonNullList<ItemStack> out = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < out.size(); i++) {
            ItemStack st = container.getItem(i);
            if (!st.isEmpty()) {
                ItemStack rem = st.getItem().getCraftingRemainder();
                if (rem != null && !rem.isEmpty()) {
                    out.set(i, rem.copy());
                }
            }
        }
        return out;
    }

    public static DraconicCrucibleShapedRecipe fromJson(Identifier id, JsonObject json) {
        String[] pattern = readPattern(json);
        Map<Character, CrucibleIngredient> key = readKey(json);
        int height = pattern.length;
        int width = pattern[0].length();
        List<CrucibleIngredient> ingredients = new ArrayList<>();
        int occupiedSlots = 0;
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                char symbol = pattern[row].charAt(column);
                CrucibleIngredient ingredient = null;
                if (symbol != ' ') {
                    ingredient = key.get(symbol);
                    if (ingredient == null) {
                        throw new IllegalArgumentException("Pattern references undefined symbol '" + symbol + "'");
                    }
                    occupiedSlots++;
                }
                ingredients.add(ingredient);
            }
        }
        if (occupiedSlots == 0) {
            throw new IllegalArgumentException("Draconic Crucible patterns must contain at least one ingredient");
        }
        ItemStack result = CrucibleIngredient.stack(GsonHelper.getAsJsonObject(json, "result"));
        return new DraconicCrucibleShapedRecipe(id, width, height, ingredients, result,
                DraconicCrucibleSmeltingRecipe.readHeatLevel(json), DraconicCrucibleSmeltingRecipe.readProcessingTime(json),
                DraconicCrucibleSmeltingRecipe.readPriority(json));
    }

    private static String[] readPattern(JsonObject json) {
        var patternJson = GsonHelper.getAsJsonArray(json, "pattern");
        if (patternJson.isEmpty() || patternJson.size() > 3) {
            throw new IllegalArgumentException("Draconic Crucible patterns must contain between 1 and 3 rows");
        }
        String[] pattern = new String[patternJson.size()];
        int width = -1;
        for (int row = 0; row < pattern.length; row++) {
            pattern[row] = GsonHelper.convertToString(patternJson.get(row), "pattern[" + row + "]");
            if (pattern[row].isEmpty() || pattern[row].length() > 3) {
                throw new IllegalArgumentException("Each Draconic Crucible pattern row must contain between 1 and 3 characters");
            }
            if (width == -1) {
                width = pattern[row].length();
            } else if (pattern[row].length() != width) {
                throw new IllegalArgumentException("All Draconic Crucible pattern rows must have the same width");
            }
        }
        return pattern;
    }

    private static Map<Character, CrucibleIngredient> readKey(JsonObject json) {
        JsonObject keyJson = GsonHelper.getAsJsonObject(json, "key");
        Map<Character, CrucibleIngredient> key = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : keyJson.entrySet()) {
            if (entry.getKey().length() != 1 || entry.getKey().charAt(0) == ' ') {
                throw new IllegalArgumentException("Recipe key symbols must be one non-space character");
            }
            key.put(entry.getKey().charAt(0), CrucibleIngredient.parse(entry.getValue()));
        }
        return key;
    }

    private record Match(int offsetX, int offsetY, boolean mirrored) {
    }
}
