package com.starlevi.starscenes.talk;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.starlevi.starscenes.net.Net;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Las tiendas: config/starscenes/tiendas/&lt;id&gt;.json. Cada oferta pide items (precio) y da un item
 * y/o corre acciones; puede tener condiciones y un limite de compras por jugador.
 */
public final class Shops {
    public record Offer(List<ItemSpec> price, ItemSpec result, String label, int limit, List<String> conditions, List<String> actions) {
    }

    public record Shop(String id, String name, List<Offer> offers) {
    }

    private record Open(String shop, int speaker) {
    }

    private static final Map<String, Shop> ALL = new LinkedHashMap<>();
    private static final Map<UUID, Open> OPEN = new HashMap<>();

    private Shops() {
    }

    public static List<String> ids() {
        return new ArrayList<>(ALL.keySet());
    }

    public static String load(List<String> errors) {
        ALL.clear();
        for (Map.Entry<String, JsonObject> e : JsonFolder.read("tiendas", errors).entrySet()) {
            try {
                JsonObject o = e.getValue();
                List<Offer> offers = new ArrayList<>();
                for (JsonElement el : o.getAsJsonArray("ofertas")) {
                    JsonObject j = el.getAsJsonObject();
                    List<ItemSpec> price = new ArrayList<>();
                    for (String s : JsonFolder.list(j, "precio")) {
                        ItemSpec it = ItemSpec.parse(s);
                        if (it == null) {
                            throw new IllegalArgumentException("item desconocido en el precio: " + s);
                        }
                        price.add(it);
                    }
                    String da = JsonFolder.str(j, "da", null);
                    ItemSpec result = da == null ? null : ItemSpec.parse(da);
                    if (da != null && result == null) {
                        throw new IllegalArgumentException("item desconocido: " + da);
                    }
                    offers.add(new Offer(price, result, JsonFolder.str(j, "texto", "").replace('&', '§'), JsonFolder.num(j, "limite", 0),
                            JsonFolder.list(j, "si"), JsonFolder.list(j, "acciones")));
                }
                ALL.put(e.getKey(), new Shop(e.getKey(), JsonFolder.str(o, "nombre", e.getKey()).replace('&', '§'), offers));
            } catch (Exception ex) {
                errors.add("tiendas/" + e.getKey() + ": " + ex.getMessage());
            }
        }
        return ALL.size() + " tiendas";
    }

    public static void open(ServerPlayer p, Entity speaker, String id) {
        Shop s = ALL.get(id);
        if (s == null) {
            p.displayClientMessage(Component.literal("§cNo existe la tienda " + id), true);
            return;
        }
        Dialogues.close(p);
        OPEN.put(p.getUUID(), new Open(id, speaker == null ? -1 : speaker.getId()));
        send(p, s, speaker);
    }

    private static Entity speaker(ServerPlayer p) {
        Open o = OPEN.get(p.getUUID());
        return o == null || o.speaker() < 0 ? null : p.level().getEntity(o.speaker());
    }

    private static void send(ServerPlayer p, Shop s, Entity speaker) {
        PlayerStore.Data d = PlayerStore.of(p);
        List<Net.OfferView> views = new ArrayList<>();
        for (int i = 0; i < s.offers().size(); i++) {
            Offer o = s.offers().get(i);
            if (!Actions.testAll(p, speaker, o.conditions())) {
                continue;
            }
            List<ItemStack> price = new ArrayList<>();
            for (ItemSpec it : o.price()) {
                price.add(it.display());
            }
            int left = o.limit() <= 0 ? -1 : Math.max(0, o.limit() - d.bought.getOrDefault(s.id() + "#" + i, 0));
            views.add(new Net.OfferView(i, price, o.result() == null ? ItemStack.EMPTY : o.result().display(), o.label(), left));
        }
        ServerPlayNetworking.send(p, new Net.ShopOpen(s.id(), s.name(), views));
    }

    public static void buy(ServerPlayer p, String id, int index) {
        Open open = OPEN.get(p.getUUID());
        Shop s = ALL.get(id);
        if (open == null || !open.shop().equals(id) || s == null || index < 0 || index >= s.offers().size()) {
            return;
        }
        Entity speaker = speaker(p);
        if (open.speaker() >= 0 && (speaker == null || speaker.distanceToSqr(p) > 12 * 12)) {
            return;
        }
        Offer o = s.offers().get(index);
        PlayerStore.Data d = PlayerStore.of(p);
        String key = id + "#" + index;
        if (!Actions.testAll(p, speaker, o.conditions()) || o.limit() > 0 && d.bought.getOrDefault(key, 0) >= o.limit()) {
            return;
        }
        for (ItemSpec it : o.price()) {
            if (!it.enough(p)) {
                p.displayClientMessage(Component.literal("§cTe falta: ").append(it.display().getHoverName()).append(" x" + it.count()), true);
                return;
            }
        }
        for (ItemSpec it : o.price()) {
            it.take(p);
        }
        if (o.result() != null) {
            o.result().give(p);
        }
        if (o.limit() > 0) {
            d.bought.merge(key, 1, Integer::sum);
            PlayerStore.changed(p);
        }
        Actions.runAll(p, speaker, o.actions());
        p.level().playSound(null, p.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.PLAYERS, 0.6f, 1.1f);
        Quests.dirty(p);
        send(p, s, speaker);
    }

    public static void close(ServerPlayer p) {
        OPEN.remove(p.getUUID());
    }
}
