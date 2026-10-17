package com.tunombre.spawncontrol.client;

import com.tunombre.spawncontrol.Net;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Pantalla de administracion de spawns (/spawns gui). Cada boton manda el comando /spawns equivalente; el servidor responde con la lista actualizada. */
public class SpawnScreen extends Screen {
    private static final int ROW_H = 24;
    private static final String[] ESTADO = {"Todos", "Bloqueados", "Activos", "Libres", "Con actividad"};
    private static final String[] TIPO = {"Todos", "Monstruos", "Animales", "Mods enteros"};
    private static final String[] ORDEN = {"Nombre", "Mas intentos", "Mas vivos"};

    private List<Net.Row> rows;
    private boolean defaultBlock;
    private final List<GuiEventListener> rowWidgets = new ArrayList<>();
    private EditBox filterBox, radiusBox;
    private Button bEstado, bTipo, bOrden, bDefault;
    private String filter = "";
    private String radius = "64";
    private int estado, tipo, orden, page;
    private int perPage = 8;
    private List<Net.Row> shown = new ArrayList<>();

    public SpawnScreen(Net.Snapshot s) {
        super(Component.literal("Control de spawns"));
        this.rows = s.rows();
        this.defaultBlock = s.defaultBlock();
    }

    public static void update(Net.Snapshot s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof SpawnScreen sc) {
            sc.rows = s.rows();
            sc.defaultBlock = s.defaultBlock();
            sc.refreshDefault();
            sc.layoutRows();
        } else if (s.open()) {
            mc.setScreen(new SpawnScreen(s));
        }
    }

    private static void send(String cmd) { Net.CH.sendToServer(new Net.Cmd(cmd)); }

    private int panelW() { return Math.min(width - 16, 470); }

    @Override
    protected void init() {
        rowWidgets.clear();
        int w = panelW(), x0 = (width - w) / 2;
        filterBox = new EditBox(font, x0, 20, w - 88, 18, Component.literal("Buscar"));
        filterBox.setHint(Component.literal("Buscar por mob o mod (ej: zombie, tacz, infectados)..."));
        filterBox.setValue(filter);
        filterBox.setResponder(v -> { filter = v; page = 0; layoutRows(); });
        addRenderableWidget(filterBox);
        radiusBox = new EditBox(font, x0 + w - 36, 20, 36, 18, Component.literal("Radio"));
        radiusBox.setValue(radius);
        radiusBox.setFilter(v -> v.matches("\\d{0,4}"));
        radiusBox.setResponder(v -> radius = v);
        radiusBox.setTooltip(Tooltip.create(Component.literal("Radio (bloques) de las zonas que creas con el boton Zona")));
        addRenderableWidget(radiusBox);

        int cw = (w - 8) / 3;
        bEstado = addRenderableWidget(Button.builder(Component.empty(), b -> { estado = (estado + 1) % ESTADO.length; page = 0; refreshChips(); layoutRows(); })
                .bounds(x0, 42, cw, 18).tooltip(Tooltip.create(Component.literal("Filtra por estado: bloqueados = lo que NO spawnea ahora; con actividad = lo que esta vivo o intento aparecer"))).build());
        bTipo = addRenderableWidget(Button.builder(Component.empty(), b -> { tipo = (tipo + 1) % TIPO.length; page = 0; refreshChips(); layoutRows(); })
                .bounds(x0 + cw + 4, 42, cw, 18).tooltip(Tooltip.create(Component.literal("Mods enteros: filas \"modid:*\" para encender o apagar todos los mobs de un mod de una vez"))).build());
        bOrden = addRenderableWidget(Button.builder(Component.empty(), b -> { orden = (orden + 1) % ORDEN.length; page = 0; refreshChips(); layoutRows(); })
                .bounds(x0 + 2 * (cw + 4), 42, cw, 18).tooltip(Tooltip.create(Component.literal("Intentos = cuantas veces el juego quiso hacerlo aparecer desde que arranco el servidor. Los que mas intentan son los primeros candidatos para tocar."))).build());

        addRenderableWidget(Button.builder(Component.literal("<"), b -> { if (page > 0) { page--; layoutRows(); } }).bounds(x0, height - 26, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> { if ((page + 1) * perPage < shown.size()) { page++; layoutRows(); } }).bounds(x0 + 24, height - 26, 20, 20).build());
        bDefault = addRenderableWidget(Button.builder(Component.empty(), b -> send(defaultBlock ? "spawns defecto libre" : "spawns defecto animales"))
                .bounds(x0 + w - 270, height - 26, 200, 20)
                .tooltip(Tooltip.create(Component.literal("Que pasa con los mobs SIN regla. \"Solo animales\": los monstruos no spawnean hasta que los actives vos. \"Todo libre\": spawnean como siempre."))).build());
        addRenderableWidget(Button.builder(Component.literal("Cerrar"), b -> onClose()).bounds(x0 + w - 62, height - 26, 62, 20).build());
        perPage = Math.max(3, (height - 100) / ROW_H);
        refreshChips();
        refreshDefault();
        layoutRows();
    }

    private void refreshChips() {
        bEstado.setMessage(Component.literal("Estado: " + ESTADO[estado]));
        bTipo.setMessage(Component.literal("Tipo: " + TIPO[tipo]));
        bOrden.setMessage(Component.literal("Orden: " + ORDEN[orden]));
    }

    private void refreshDefault() {
        if (bDefault == null) return;
        bDefault.setMessage(Component.literal("Por defecto: " + (defaultBlock ? "solo animales (click: todo libre)" : "todo libre (click: bloquear monstruos)"))
                .withStyle(defaultBlock ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
    }

    private static boolean isAnimal(String cat) {
        return switch (cat) { case "creature", "ambient", "water_creature", "water_ambient", "underground_water_creature", "axolotls" -> true; default -> false; };
    }

    private boolean matches(Net.Row r) {
        String f = filter.toLowerCase(Locale.ROOT).trim();
        if (!f.isEmpty() && !r.id().toLowerCase(Locale.ROOT).contains(f)) return false;
        switch (estado) {
            case 1 -> { if (r.state() != 2 && r.state() != 3) return false; }
            case 2 -> { if (r.state() != 1) return false; }
            case 3 -> { if (r.state() != 0) return false; }
            case 4 -> { if (r.alive() == 0 && r.ok() == 0 && r.blocked() == 0) return false; }
            default -> { }
        }
        switch (tipo) {
            case 1 -> { if (!r.cat().equals("monster")) return false; }
            case 2 -> { if (!isAnimal(r.cat())) return false; }
            case 3 -> { if (!r.cat().equals("mod")) return false; }
            default -> { }
        }
        return true;
    }

    private void layoutRows() {
        for (GuiEventListener w : rowWidgets) removeWidget(w);
        rowWidgets.clear();
        shown = new ArrayList<>();
        for (Net.Row r : rows) if (matches(r)) shown.add(r);
        Comparator<Net.Row> byName = Comparator.comparing(Net.Row::id);
        switch (orden) {
            case 1 -> shown.sort(Comparator.comparingInt((Net.Row r) -> -(r.ok() + r.blocked())).thenComparing(byName));
            case 2 -> shown.sort(Comparator.comparingInt((Net.Row r) -> -r.alive()).thenComparing(byName));
            default -> { }
        }
        int pages = Math.max(1, (shown.size() + perPage - 1) / perPage);
        if (page >= pages) page = pages - 1;
        int w = panelW(), x0 = (width - w) / 2, y = 66;
        int bx = x0 + w - 168;
        for (int i = page * perPage; i < Math.min(shown.size(), (page + 1) * perPage); i++) {
            Net.Row r = shown.get(i);
            String id = r.id().equals("*") ? "todos:todos" : r.id().endsWith(":*") ? r.id().substring(0, r.id().length() - 1) + "todos" : r.id();
            boolean on = r.state() == 0 || r.state() == 1;
            Component label = Component.literal(on ? "ON" : "OFF").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED);
            add(Button.builder(label, b -> send(on ? "spawns desactivar " + id : "spawns activar " + id + " silencio"))
                    .bounds(bx, y, 34, 20).tooltip(Tooltip.create(Component.literal(on ? "Spawnea. Click: lo apaga (sin aviso)" : "NO spawnea. Click: lo enciende (sin aviso)"))).build());
            add(Button.builder(Component.literal("Liberar"), b -> send("spawns activar " + id))
                    .bounds(bx + 36, y, 46, 20).tooltip(Tooltip.create(Component.literal("Lo enciende y lanza su aviso (imagen de tacui) a todos los jugadores"))).build());
            add(Button.builder(Component.literal("Zona"), b -> {
                String name = "z" + (System.currentTimeMillis() % 100000);
                int rad = 64;
                try { rad = Math.max(1, Integer.parseInt(radius)); } catch (Exception ignored) { }
                send("spawns zona aqui " + id + " " + name + " " + rad + " permitir");
                send("spawns zona solo " + id + " true");
            }).bounds(bx + 84, y, 40, 20).tooltip(Tooltip.create(Component.literal("Crea una zona permitida donde estas parado (radio de arriba) y el mob solo spawnea en zonas"))).build());
            add(Button.builder(Component.literal("X"), b -> send("spawns reset " + id)).bounds(bx + 126, y, 18, 20)
                    .tooltip(Tooltip.create(Component.literal("Borra la regla de este mob: vuelve a la politica por defecto"))).build());
            y += ROW_H;
        }
    }

    private void add(Button b) {
        addRenderableWidget(b);
        rowWidgets.add(b);
    }

    private static String pretty(String id) {
        if (id.equals("*")) return "TODOS los mobs";
        if (id.endsWith(":*")) return "TODO el mod " + id.substring(0, id.length() - 2);
        return id;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.drawCenteredString(font, "Control de spawns", width / 2, 6, 0xFFFFFF);
        int w = panelW(), x0 = (width - w) / 2, y = 66, nameW = w - 176;
        for (int i = page * perPage; i < Math.min(shown.size(), (page + 1) * perPage); i++) {
            Net.Row r = shown.get(i);
            String name = pretty(r.id());
            if (font.width(name) > nameW) name = font.plainSubstrByWidth(name, nameW - 6) + "..";
            g.drawString(font, name, x0, y + 1, 0xFFFFFF, false);
            String st;
            int col;
            switch (r.state()) {
                case 0 -> { st = "libre"; col = 0x66CC66; }
                case 3 -> { st = "BLOQUEADO (por defecto)"; col = 0xFF7755; }
                case 2 -> { st = r.day() > 0 && !r.released() ? "se libera el dia " + r.day() : "DESACTIVADO"; col = r.day() > 0 && !r.released() ? 0xFFCC33 : 0xFF5555; }
                default -> { st = "ACTIVO"; col = 0x55FF55; }
            }
            StringBuilder ex = new StringBuilder(st);
            if (r.inh()) ex.append(" (por su mod)");
            if (r.only()) ex.append(" solo-zonas");
            if (r.zones() > 0) ex.append(" z").append(r.zones());
            if (r.max() > 0) ex.append(" max").append(r.max());
            if (r.prob() < 100) ex.append(" ").append(r.prob()).append("%");
            if (r.ann()) ex.append(" aviso");
            int sx = x0;
            g.drawString(font, ex.toString(), sx, y + 12, col, false);
            sx += font.width(ex.toString()) + 8;
            String act = "vivos " + r.alive() + "  intentos " + r.ok() + " ok / " + r.blocked() + " bloq";
            g.drawString(font, act, sx, y + 12, (r.blocked() > 0 || r.alive() > 0) ? 0xDDDDAA : 0x777777, false);
            y += ROW_H;
        }
        if (shown.isEmpty()) g.drawCenteredString(font, "Nada coincide con el filtro", width / 2, 90, 0x888888);
        int pages = Math.max(1, (shown.size() + perPage - 1) / perPage);
        g.drawString(font, (page + 1) + "/" + pages + "  (" + shown.size() + ")", x0 + 50, height - 20, 0xAAAAAA, false);
        super.render(g, mx, my, pt);
    }

    @Override public boolean isPauseScreen() { return false; }
}
