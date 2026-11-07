package com.starlevi.starvillagers.client;

import com.starlevi.starscenes.client.ui.StarButton;
import com.starlevi.starscenes.client.ui.UI;
import com.starlevi.starvillagers.look.Editor;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** La ficha de un aldeano (Libro de aldeanos): nombre, genero, personalidad, humor, amistad, pareja y mas. */
public final class EditorBookScreen extends Screen {
    private final Editor.Open d;
    private EditBox name;
    /** A quien se elige como pareja: "nadie", "vos" o el id de un aldeano. */
    private int who;
    private final List<String[]> choices = new ArrayList<>();

    public EditorBookScreen(Editor.Open d) {
        super(Component.literal("Libro de aldeanos"));
        this.d = d;
        this.choices.add(new String[]{"nadie", "Nadie"});
        this.choices.add(new String[]{"vos", "Vos"});
        for (Editor.Other o : d.others()) {
            this.choices.add(new String[]{String.valueOf(o.entity()), o.name() + (o.female() ? " ♀" : " ♂")});
        }
        String cur = !d.spouse().isEmpty() ? d.spouse() : d.partner();
        for (int i = 0; i < this.choices.size(); i++) {
            if (this.choices.get(i)[1].startsWith(cur.equals("vos") ? "Vos" : cur.isEmpty() ? "Nadie" : cur + " ")) {
                this.who = i;
            }
        }
    }

    private void send(String what, String value) {
        ClientPlayNetworking.send(new Editor.Set(this.d.entity(), what, value));
    }

    private int px() {
        return this.width / 2 - 160;
    }

    private int py() {
        return Math.max(8, this.height / 2 - 120);
    }

    @Override
    protected void init() {
        int x = px() + 110, y = py() + 30, w = 200, h = 18, gap = 24;
        this.name = new EditBox(this.font, x, y, w - 64, h, Component.literal("Nombre"));
        this.name.setValue(this.d.name());
        this.name.setMaxLength(32);
        addRenderableWidget(this.name);
        addRenderableWidget(new StarButton(x + w - 60, y, 60, h, "Guardar", StarButton.Style.ACCENT, () -> send("nombre", this.name.getValue())));
        y += gap;
        addRenderableWidget(new StarButton(x, y, w, h, this.d.female() ? "Mujer ♀ (cambiar)" : "Hombre ♂ (cambiar)", StarButton.Style.NORMAL,
                () -> send("genero", this.d.female() ? "hombre" : "mujer")));
        y += gap;
        addRenderableWidget(new StarButton(x, y, w, h, "Personalidad: " + this.d.personality() + "  ▶", StarButton.Style.NORMAL, () -> {
            List<String> l = this.d.personalities();
            if (!l.isEmpty()) {
                int i = (l.indexOf(this.d.personality()) + 1) % l.size();
                send("personalidad", l.get(i));
            }
        }));
        y += gap;
        stepper(x, y, w, h, "Humor", this.d.mood(), "humor");
        y += gap;
        stepper(x, y, w, h, "Amistad con vos", this.d.hearts(), "amistad");
        y += gap;
        addRenderableWidget(new StarButton(x, y, w, h, "Pareja: " + this.choices.get(this.who)[1] + "  ▶", StarButton.Style.NORMAL, () -> {
            this.who = (this.who + 1) % this.choices.size();
            rebuildWidgets();
        }));
        y += gap;
        String target = this.choices.get(this.who)[0];
        addRenderableWidget(new StarButton(x, y, w / 2 - 2, h, "♥ Novios", StarButton.Style.NORMAL, () -> send("pareja", target)));
        addRenderableWidget(new StarButton(x + w / 2 + 2, y, w / 2 - 2, h, "♥ Casar", StarButton.Style.ACCENT, () -> send("esposo", target)));
        y += gap;
        addRenderableWidget(new StarButton(x, y, w / 2 - 2, h, "Tener un bebé", StarButton.Style.NORMAL, () -> send("bebe", "")));
        addRenderableWidget(new StarButton(x + w / 2 + 2, y, w / 2 - 2, h, "Árbol familiar", StarButton.Style.NORMAL, () -> send("arbol", "")));
        y += gap;
        addRenderableWidget(new StarButton(x, y, w, h, "Ropa y pelo (biblioteca de MCA)", StarButton.Style.NORMAL, () -> send("vestir", "")));
        addRenderableWidget(new StarButton(this.width / 2 - 50, py() + 246, 100, 20, "Cerrar", StarButton.Style.GHOST, this::onClose));
    }

    private void stepper(int x, int y, int w, int h, String label, int value, String key) {
        addRenderableWidget(new StarButton(x, y, 28, h, "−", StarButton.Style.NORMAL, () -> send(key, String.valueOf(value - 10))));
        addRenderableWidget(new StarButton(x + w - 28, y, 28, h, "+", StarButton.Style.NORMAL, () -> send(key, String.valueOf(value + 10))));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        UI.panel(g, px(), py(), 320, 274);
        super.render(g, mx, my, pt);
        UI.title(g, this.font, "Libro de aldeanos · " + this.d.name(), this.width / 2, py() + 10);
        int x = px() + 14, y = py() + 35, gap = 24;
        String[] labels = {"Nombre", "Género", "Personalidad", "Humor", "Amistad con vos", "Pareja", "", "", ""};
        for (String l : labels) {
            if (!l.isEmpty()) {
                g.drawString(this.font, l, x, y, UI.MUTED, false);
            }
            y += gap;
        }
        // valores de los contadores
        int cx = px() + 110 + 100;
        g.drawCenteredString(this.font, String.valueOf(this.d.mood()), cx, py() + 30 + gap * 3 + 5, UI.TEXT);
        g.drawCenteredString(this.font, this.d.hearts() + " / 100", cx, py() + 30 + gap * 4 + 5, UI.TEXT);
        String rel = !this.d.spouse().isEmpty() ? "Casado/a con: " + this.d.spouse() : !this.d.partner().isEmpty() ? "De novio/a con: " + this.d.partner() : "Sin pareja";
        g.drawString(this.font, rel, x, py() + 30 + gap * 6 + 5, UI.GOLD, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
