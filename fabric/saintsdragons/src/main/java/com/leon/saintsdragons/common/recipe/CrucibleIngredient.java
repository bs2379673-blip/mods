package com.leon.saintsdragons.common.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Port 1.21.11: ingrediente del Crisol Draconico leido del JSON de 1.20.1 ({"item": ...}, {"tag": ...} o
 * una lista de esos). Se compara contra el item o la etiqueta al usarlo (no necesita los registros al cargar).
 */
public final class CrucibleIngredient implements Predicate<ItemStack> {
    private final List<Item> items = new ArrayList<>();
    private final List<TagKey<Item>> tags = new ArrayList<>();

    public static CrucibleIngredient parse(JsonElement e) {
        CrucibleIngredient in = new CrucibleIngredient();
        in.add(e);
        if (in.items.isEmpty() && in.tags.isEmpty()) {
            throw new IllegalArgumentException("Empty ingredient: " + e);
        }
        return in;
    }

    private void add(JsonElement e) {
        if (e.isJsonArray()) {
            e.getAsJsonArray().forEach(this::add);
        } else if (e.isJsonPrimitive()) {
            String s = e.getAsString();
            if (s.startsWith("#")) {
                this.tags.add(TagKey.create(Registries.ITEM, Identifier.parse(s.substring(1))));
            } else {
                item(s);
            }
        } else if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("item")) {
                item(o.get("item").getAsString());
            }
            if (o.has("tag")) {
                this.tags.add(TagKey.create(Registries.ITEM, Identifier.parse(o.get("tag").getAsString())));
            }
        }
    }

    private void item(String id) {
        Item it = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
        if (it == null || it == net.minecraft.world.item.Items.AIR) {
            throw new IllegalArgumentException("Unknown item " + id);
        }
        this.items.add(it);
    }

    @Override
    public boolean test(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        for (Item it : this.items) {
            if (stack.is(it)) {
                return true;
            }
        }
        for (TagKey<Item> t : this.tags) {
            if (stack.is(t)) {
                return true;
            }
        }
        return false;
    }

    /** {"item": id, "count": n} (o "id" como en 1.21). */
    public static ItemStack stack(JsonObject o) {
        String id = o.has("item") ? o.get("item").getAsString() : o.get("id").getAsString();
        Item it = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
        if (it == null || it == net.minecraft.world.item.Items.AIR) {
            throw new IllegalArgumentException("Unknown item " + id);
        }
        return new ItemStack(it, o.has("count") ? o.get("count").getAsInt() : 1);
    }
}
