package com.starlevi.starscenes.talk;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Un item escrito a mano: "minecraft:emerald 3", "emerald" (1) o "#minecraft:logs 16" (cualquiera
 * de una etiqueta). Sirve para precios, recompensas y lo que piden las misiones.
 */
public record ItemSpec(Item item, TagKey<Item> tag, int count, String text) {
    public static ItemSpec parse(String s) {
        String t = s.trim();
        int count = 1;
        int sp = t.lastIndexOf(' ');
        if (sp > 0) {
            try {
                count = Integer.parseInt(t.substring(sp + 1).trim());
                t = t.substring(0, sp).trim();
            } catch (NumberFormatException ignored) {
            }
        }
        count = Math.max(1, count);
        if (t.startsWith("#")) {
            Identifier id = Identifier.tryParse(t.substring(1));
            if (id == null) {
                return null;
            }
            return new ItemSpec(null, TagKey.create(Registries.ITEM, id), count, s.trim());
        }
        Identifier id = Identifier.tryParse(t);
        if (id == null) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null || item == Items.AIR) {
            return null;
        }
        return new ItemSpec(item, null, count, s.trim());
    }

    public boolean matches(ItemStack st) {
        if (st.isEmpty()) {
            return false;
        }
        return this.tag != null ? st.is(this.tag) : st.is(this.item);
    }

    /** Para mostrarlo: el item (o el primero de la etiqueta) con la cantidad. */
    public ItemStack display() {
        Item it = this.item;
        if (it == null) {
            for (Holder<Item> h : BuiltInRegistries.ITEM.getTagOrEmpty(this.tag)) {
                it = h.value();
                break;
            }
        }
        return it == null ? ItemStack.EMPTY : new ItemStack(it, this.count);
    }

    /** Cuantos de estos tiene el jugador. */
    public int has(Player p) {
        Inventory inv = p.getInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (matches(st)) {
                n += st.getCount();
            }
        }
        return n;
    }

    public boolean enough(Player p) {
        return has(p) >= this.count;
    }

    /** Le saca la cantidad (si la tiene); true si pudo. */
    public boolean take(Player p) {
        if (!enough(p)) {
            return false;
        }
        Inventory inv = p.getInventory();
        int left = this.count;
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
            ItemStack st = inv.getItem(i);
            if (matches(st)) {
                int k = Math.min(left, st.getCount());
                st.shrink(k);
                left -= k;
            }
        }
        inv.setChanged();
        return true;
    }

    /** Le da la cantidad (lo que no entra queda en el piso, a sus pies). */
    public void give(Player p) {
        ItemStack proto = display();
        if (proto.isEmpty()) {
            return;
        }
        int left = this.count;
        while (left > 0) {
            int k = Math.min(left, proto.getMaxStackSize());
            ItemStack st = proto.copyWithCount(k);
            if (!p.getInventory().add(st) && !st.isEmpty()) {
                p.drop(st, false);
            }
            left -= k;
        }
    }
}
