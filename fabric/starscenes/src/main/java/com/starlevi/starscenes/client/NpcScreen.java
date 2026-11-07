package com.starlevi.starscenes.client;

import com.starlevi.starscenes.client.ui.StarButton;
import com.starlevi.starscenes.client.ui.UI;
import com.starlevi.starscenes.entity.NpcEntity;
import com.starlevi.starscenes.net.Net;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * El editor de un NPC (agachado + clic derecho, o /scenes menu), con pestañas: Apariencia,
 * Animaciones, Charla y Posicion. Los valores se ven en vivo.
 */
public class NpcScreen extends Screen {
    private enum Tab {
        LOOK("Apariencia"), ANIM("Animaciones"), TALK("Charla"), MOVE("Posición");

        final String label;

        Tab(String l) {
            this.label = l;
        }
    }

    private static final float[] STEPS = {0.05f, 0.1f, 0.25f, 0.5f, 1f};
    private static Tab lastTab = Tab.LOOK;
    private static int step = 1;

    private final int entityId;
    private final List<String> dialogues;
    private String dialogue;
    private Tab tab = lastTab;
    private EditBox name;
    private String nameDraft;
    private int animScroll;
    private final List<Row> rows = new ArrayList<>();

    /** Una fila con titulo a la izquierda y un valor (se dibuja en vivo). */
    private record Row(String label, int y, java.util.function.Supplier<String> value, int valueX) {
    }

    /** Que pestaña abre la proxima vez (0 Apariencia, 1 Animaciones, 2 Charla, 3 Posicion). */
    public static void nextTab(int i) {
        lastTab = Tab.values()[Math.floorMod(i, Tab.values().length)];
    }

    public NpcScreen(Net.OpenMenu m) {
        super(Component.literal("NPC"));
        this.entityId = m.entity();
        this.dialogue = m.dialogue();
        this.dialogues = new ArrayList<>(m.dialogues());
    }

    private NpcEntity npc() {
        if (Minecraft.getInstance().level == null) {
            return null;
        }
        Entity e = Minecraft.getInstance().level.getEntity(this.entityId);
        return e instanceof NpcEntity n ? n : null;
    }

    private void send(String action, String value) {
        ClientPlayNetworking.send(new Net.Edit(this.entityId, action, value));
    }

    private int pw() {
        return Math.min(330, this.width - 16);
    }

    private int ph() {
        return Math.min(232, this.height - 12);
    }

    private int px() {
        return (this.width - pw()) / 2;
    }

    private int py() {
        return (this.height - ph()) / 2;
    }

    @Override
    protected void init() {
        NpcEntity n = npc();
        if (n == null) {
            onClose();
            return;
        }
        this.rows.clear();
        int px = px(), py = py(), pw = pw(), ph = ph();
        // pestañas
        Tab[] tabs = Tab.values();
        int tw = (pw - 16 - (tabs.length - 1) * 3) / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            addRenderableWidget(new StarButton(px + 8 + i * (tw + 3), py + 22, tw, 16, t.label, t == this.tab ? StarButton.Style.TAB_ON : StarButton.Style.TAB,
                    () -> {
                        this.tab = t;
                        lastTab = t;
                        rebuildWidgets();
                    }));
        }
        int y = py + 46, x = px + 10, right = px + pw - 10;
        switch (this.tab) {
            case LOOK -> {
                picker(x, y, right, "Modelo", () -> npcOr().model(), d -> cycle("modelo", ClientScenes.modelIds(), d));
                y += 24;
                picker(x, y, right, "Skin", () -> npcOr().skin().isEmpty() ? "la del modelo" : npcOr().skin(), d -> cycle("skin", skins(), d));
                y += 24;
                stepper(x, y, right, "Tamaño", () -> String.format(Locale.ROOT, "%.2f", npcOr().scale()),
                        d -> send("escala", String.format(Locale.ROOT, "%.2f", Math.max(0.1f, npcOr().scale() + d * 0.1f))));
                y += 30;
                this.rows.add(new Row("Nombre (con &colores)", y - 11, () -> "", 0));
                this.name = new EditBox(this.font, x, y, right - x - 60, 18, Component.literal("Nombre"));
                this.name.setMaxLength(120);
                this.name.setValue(this.nameDraft != null ? this.nameDraft : n.getCustomName() == null ? "" : n.getCustomName().getString().replace('§', '&'));
                this.name.setResponder(v -> this.nameDraft = v);
                this.name.setHint(Component.literal("Sin nombre"));
                addRenderableWidget(this.name);
                addRenderableWidget(new StarButton(right - 56, y, 56, 18, "Poner", StarButton.Style.ACCENT,
                        () -> send("nombre", this.name.getValue().isBlank() ? "-" : this.name.getValue())));
            }
            case ANIM -> {
                stepper(x, y, right, "Estilo (idle1, idle2…)", () -> npcOr().style() == 0 ? "al azar" : String.valueOf(npcOr().style()),
                        d -> send("estilo", String.valueOf(Math.max(0, npcOr().style() + d))));
                y += 26;
                this.rows.add(new Row("Tocá una para verla:", y, () -> "", 0));
                y += 12;
                List<String> anims = anims(n);
                int cols = 3, bw = (right - x - (cols - 1) * 4) / cols;
                int maxRows = Math.max(1, (py + ph - 34 - y) / 20);
                this.animScroll = Math.max(0, Math.min(this.animScroll, (anims.size() + cols - 1) / cols - maxRows));
                for (int i = this.animScroll * cols; i < anims.size() && i < (this.animScroll + maxRows) * cols; i++) {
                    int r = i / cols - this.animScroll, c = i % cols;
                    String a = anims.get(i);
                    addRenderableWidget(new StarButton(x + c * (bw + 4), y + r * 20, bw, 18, a, StarButton.Style.NORMAL, () -> send("animar", a)));
                }
                if (anims.isEmpty()) {
                    this.rows.add(new Row("§7(el modelo no tiene animaciones)", y + 4, () -> "", 0));
                }
            }
            case TALK -> {
                picker(x, y, right, "Diálogo", () -> this.dialogue.isEmpty() ? "ninguno" : this.dialogue, d -> {
                    List<String> l = new ArrayList<>();
                    l.add("");
                    l.addAll(this.dialogues);
                    int i = Math.floorMod(l.indexOf(this.dialogue) + d, l.size());
                    this.dialogue = l.get(i);
                    send("dialogo", this.dialogue.isEmpty() ? "-" : this.dialogue);
                });
                y += 22;
                addRenderableWidget(new StarButton(right - 120, y, 120, 16, "▶ Probar diálogo", StarButton.Style.ACCENT, () -> {
                    onClose();
                    send("probar", "");
                }));
                y += 28;
                toggle(x, y, right, "Mira a los jugadores", () -> npcOr().look(), v -> send("mirar", String.valueOf(v)));
                y += 24;
                stepper(x, y, right, "Te ve a (bloques)", () -> String.format(Locale.ROOT, "%.1f", npcOr().radius()),
                        d -> send("radio", String.valueOf(Math.max(0.5f, npcOr().radius() + d))));
                y += 26;
                this.rows.add(new Row("§7Los diálogos están en config/starscenes/dialogos", y, () -> "", 0));
            }
            case MOVE -> {
                // cruz de direcciones
                int cx = x + 60, cy = y + 18;
                addRenderableWidget(new StarButton(cx - 18, cy - 20, 36, 18, "▲", StarButton.Style.NORMAL, () -> move("adelante")));
                addRenderableWidget(new StarButton(cx - 18, cy + 20, 36, 18, "▼", StarButton.Style.NORMAL, () -> move("atras")));
                addRenderableWidget(new StarButton(cx - 58, cy, 36, 18, "◀", StarButton.Style.NORMAL, () -> move("izquierda")));
                addRenderableWidget(new StarButton(cx + 22, cy, 36, 18, "▶", StarButton.Style.NORMAL, () -> move("derecha")));
                addRenderableWidget(new StarButton(cx + 70, cy - 20, 44, 18, "Subir", StarButton.Style.NORMAL, () -> move("arriba")));
                addRenderableWidget(new StarButton(cx + 70, cy + 20, 44, 18, "Bajar", StarButton.Style.NORMAL, () -> move("abajo")));
                addRenderableWidget(new StarButton(right - 80, cy - 20, 80, 18, "Paso: " + STEPS[step], StarButton.Style.GHOST, () -> {
                    step = (step + 1) % STEPS.length;
                    rebuildWidgets();
                }));
                addRenderableWidget(new StarButton(right - 80, cy, 38, 18, "↺", StarButton.Style.NORMAL, () -> send("girar", "-15")));
                addRenderableWidget(new StarButton(right - 38, cy, 38, 18, "↻", StarButton.Style.NORMAL, () -> send("girar", "15")));
                addRenderableWidget(new StarButton(right - 80, cy + 20, 80, 18, "Que me mire", StarButton.Style.NORMAL, () -> send("mirarme", "")));
                y = cy + 46;
                this.rows.add(new Row("Ítems (lo que tengas en la mano):", y, () -> "", 0));
                y += 12;
                int bw = (right - x - 8) / 3;
                addRenderableWidget(new StarButton(x, y, bw, 22, "Mano", StarButton.Style.NORMAL, () -> send("item", "mano")).icon(n.item(NpcEntity.MAIN)));
                addRenderableWidget(new StarButton(x + bw + 4, y, bw, 22, "Otra mano", StarButton.Style.NORMAL, () -> send("item", "otra_mano"))
                        .icon(n.item(NpcEntity.OFF)));
                addRenderableWidget(new StarButton(x + 2 * (bw + 4), y, bw, 22, "Cabeza", StarButton.Style.NORMAL, () -> send("item", "cabeza"))
                        .icon(n.item(NpcEntity.HEAD)));
            }
        }
        // abajo
        addRenderableWidget(new StarButton(px + 10, py + ph - 26, 84, 18, "Quitar NPC", StarButton.Style.DANGER, () -> {
            send("quitar", "");
            onClose();
        }));
        addRenderableWidget(new StarButton(px + pw - 84, py + ph - 26, 74, 18, "Listo", StarButton.Style.ACCENT, this::onClose));
    }

    private void move(String dir) {
        send("mover", dir + " " + STEPS[step]);
    }

    /** Fila: titulo, ◀ valor ▶. */
    private void picker(int x, int y, int right, String label, java.util.function.Supplier<String> value, java.util.function.IntConsumer change) {
        int w = 150;
        addRenderableWidget(new StarButton(right - w, y, 18, 18, "◀", StarButton.Style.NORMAL, () -> change.accept(-1)));
        addRenderableWidget(new StarButton(right - 18, y, 18, 18, "▶", StarButton.Style.NORMAL, () -> change.accept(1)));
        this.rows.add(new Row(label, y + 5, value, right - w / 2 - 0));
    }

    /** Fila: titulo, - valor +. */
    private void stepper(int x, int y, int right, String label, java.util.function.Supplier<String> value, java.util.function.IntConsumer change) {
        int w = 90;
        addRenderableWidget(new StarButton(right - w, y, 18, 18, "-", StarButton.Style.NORMAL, () -> change.accept(-1)));
        addRenderableWidget(new StarButton(right - 18, y, 18, 18, "+", StarButton.Style.NORMAL, () -> change.accept(1)));
        this.rows.add(new Row(label, y + 5, value, right - w / 2));
    }

    /** Fila: titulo y un interruptor Si/No. */
    private void toggle(int x, int y, int right, String label, java.util.function.BooleanSupplier value, java.util.function.Consumer<Boolean> set) {
        boolean on = value.getAsBoolean();
        addRenderableWidget(new StarButton(right - 60, y, 60, 18, on ? "Sí" : "No", on ? StarButton.Style.ACCENT : StarButton.Style.NORMAL, () -> {
            set.accept(!value.getAsBoolean());
        }));
        this.rows.add(new Row(label, y + 5, () -> "", 0));
    }

    private List<String> anims(NpcEntity n) {
        ClientScenes.Ready r = ClientScenes.model(n.model());
        return r == null ? List.of() : new ArrayList<>(r.model.anims.keySet());
    }

    private NpcEntity npcOr() {
        NpcEntity n = npc();
        if (n == null) {
            onClose();
            throw new IllegalStateException("el NPC ya no esta");
        }
        return n;
    }

    private static List<String> skins() {
        List<String> l = new ArrayList<>();
        l.add("-");
        l.addAll(ClientScenes.skinIds());
        return l;
    }

    private void cycle(String what, List<String> options, int dir) {
        NpcEntity n = npc();
        if (n == null || options.isEmpty()) {
            return;
        }
        String cur = what.equals("modelo") ? n.model() : n.skin().isEmpty() ? "-" : n.skin();
        int i = options.indexOf(cur);
        i = Math.floorMod(i + dir, options.size());
        send(what, options.get(i));
    }

    private int lastHash;

    @Override
    public void tick() {
        // el servidor confirmo un cambio (ej. un toggle o un item): se redibujan los botones
        NpcEntity n = npc();
        if (n == null) {
            onClose();
            return;
        }
        int h = java.util.Objects.hash(n.look(), n.item(NpcEntity.MAIN).getItem(), n.item(NpcEntity.OFF).getItem(), n.item(NpcEntity.HEAD).getItem(),
                n.model());
        if (this.lastHash != 0 && h != this.lastHash) {
            rebuildWidgets();
        }
        this.lastHash = h;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        NpcEntity n = npc();
        if (n == null) {
            return;
        }
        int px = px(), py = py(), pw = pw(), ph = ph();
        UI.panel(g, px, py, pw, ph);
        g.drawString(this.font, "✦ Editar NPC", px + 10, py + 8, UI.ACCENT, false);
        String who = n.getCustomName() != null ? n.getCustomName().getString() : n.model();
        String w = UI.fit(this.font, who, pw - 110);
        g.drawString(this.font, w, px + pw - 10 - this.font.width(w), py + 8, UI.GOLD, false);
        UI.separator(g, px + 8, py + ph - 32, pw - 16);
        for (Row r : this.rows) {
            g.drawString(this.font, r.label(), px + 10, r.y(), UI.MUTED, false);
            String v = r.value().get();
            if (!v.isEmpty()) {
                String t = UI.fit(this.font, v, 110);
                g.drawString(this.font, t, r.valueX() - this.font.width(t) / 2, r.y(), UI.TEXT, false);
            }
        }
        super.render(g, mx, my, partial);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (this.tab == Tab.ANIM) {
            this.animScroll -= (int) Math.signum(dy);
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
