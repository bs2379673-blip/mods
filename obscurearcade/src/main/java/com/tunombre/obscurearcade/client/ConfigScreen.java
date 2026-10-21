package com.tunombre.obscurearcade.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tunombre.obscurearcade.Net;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Pantalla de configuracion de la maquina o consola (la abren los operadores con la Llave Arcade). */
public class ConfigScreen extends Screen {
    private final JsonObject data;
    private final String type;
    private final List<String> keys = new ArrayList<>();
    private final List<String> labels = new ArrayList<>();
    private final List<String> options = new ArrayList<>();
    private final List<EditBox> boxes = new ArrayList<>();
    private final List<String> original = new ArrayList<>();
    private final List<String> songs = new ArrayList<>();
    private final List<String> styles = new ArrayList<>();

    public ConfigScreen(String json) {
        super(Component.literal("Obscure Arcade"));
        this.data = JsonParser.parseString(json).getAsJsonObject();
        this.type = data.get("type").getAsString();
        fill(songs, "songs");
        fill(styles, "styles");
        JsonObject c = data.getAsJsonObject("cfg");
        if (type.equals("arcade")) {
            add("song", "Canción", c, "songs");
            add("cost_item", "Ítem de coste (id)", c, null);
            add("cost_count", "Cantidad de coste", c, null);
            add("reward_cmd", "Comando de premio", c, null);
            add("reward_rank", "Rango mínimo (D C B A S SS)", c, null);
        } else {
            add("song1", "Canción 1", c, "songs");
            add("song2", "Canción 2 (opcional)", c, "songs");
            add("style", "Estilo de pelea", c, "styles");
            add("end_cmd", "Comando final ({ganador} {perdedor})", c, null);
        }
    }

    private void fill(List<String> out, String k) {
        if (!data.has(k)) return;
        for (JsonElement e : data.getAsJsonArray(k)) out.add(e.getAsString());
    }

    private void add(String key, String label, JsonObject c, String opt) {
        keys.add(key);
        labels.add(label);
        options.add(opt);
        original.add(c.has(key) ? c.get(key).getAsString() : "");
    }

    @Override
    protected void init() {
        boxes.clear();
        int w = Math.min(320, width - 80);
        int x = (width - w) / 2 - 20;
        int y = height / 2 - keys.size() * 15 - 20;
        for (int i = 0; i < keys.size(); i++) {
            EditBox b = new EditBox(font, x, y + i * 32 + 12, w, 18, Component.literal(labels.get(i)));
            b.setMaxLength(300);
            b.setValue(original.get(i));
            addRenderableWidget(b);
            boxes.add(b);
            String opt = options.get(i);
            if (opt != null) {
                final int idx = i;
                addRenderableWidget(Button.builder(Component.literal("▶"), bt -> cycle(idx)).bounds(x + w + 4, y + i * 32 + 11, 20, 20).build());
            }
        }
        int by = y + keys.size() * 32 + 14;
        addRenderableWidget(Button.builder(Component.literal("Guardar"), b -> { save(); onClose(); }).bounds(width / 2 - 154, by, 100, 20).build());
        if (type.equals("versus")) {
            addRenderableWidget(Button.builder(Component.literal("Puesto 1 aquí"), b -> act("spot1")).bounds(width / 2 - 50, by, 100, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Puesto 2 aquí"), b -> act("spot2")).bounds(width / 2 + 54, by, 100, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Restablecer puestos"), b -> act("reset_spots")).bounds(width / 2 - 154, by + 24, 150, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Cancelar duelos"), b -> act("cancel")).bounds(width / 2 + 4, by + 24, 150, 20).build());
        } else {
            addRenderableWidget(Button.builder(Component.literal("Reiniciar ranking"), b -> act("reset_top")).bounds(width / 2 - 50, by, 204, 20).build());
        }
    }

    private void cycle(int i) {
        List<String> l = "styles".equals(options.get(i)) ? styles : songs;
        if (l.isEmpty()) return;
        int at = l.indexOf(boxes.get(i).getValue());
        boxes.get(i).setValue(l.get((at + 1) % l.size()));
    }

    private JsonObject base() {
        JsonObject o = new JsonObject();
        o.addProperty("x", data.get("x").getAsInt());
        o.addProperty("y", data.get("y").getAsInt());
        o.addProperty("z", data.get("z").getAsInt());
        return o;
    }

    private void act(String op) {
        JsonObject o = base();
        o.addProperty("op", op);
        Net.sendToServer("act", o.toString());
    }

    private void save() {
        for (int i = 0; i < keys.size(); i++) {
            String v = boxes.get(i).getValue();
            if (v.equals(original.get(i))) continue;
            JsonObject o = base();
            o.addProperty("k", keys.get(i));
            o.addProperty("v", v);
            Net.sendToServer("set", o.toString());
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int w = Math.min(320, width - 80);
        int x = (width - w) / 2 - 20;
        int y = height / 2 - keys.size() * 15 - 20;
        g.drawCenteredString(font, type.equals("arcade") ? "Máquina recreativa" : "Consola de duelo", width / 2, y - 22, 0xFFFFFF);
        for (int i = 0; i < keys.size(); i++) g.drawString(font, labels.get(i), x, y + i * 32, 0xA0A0A0, false);
        super.render(g, mx, my, pt);
    }
}
