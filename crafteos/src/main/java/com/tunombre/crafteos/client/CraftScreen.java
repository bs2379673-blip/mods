package com.tunombre.crafteos.client;

import com.tunombre.crafteos.Net;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/** /crafteos gui: lista paginada (la arma el servidor) de lo que se puede craftear, con ON/OFF por resultado o por mod entero. */
public class CraftScreen extends Screen {
    private static final int ROW_H = 24;
    private static final String[] TIPO = {"Todos", "Mesa de crafteo", "Hornos", "Herreria/Cantero", "Otros (TACZ, mods)", "Mods enteros", "PROHIBIR items", "PROHIBIR mods"};
    private static final String[] ESTADO = {"Todos", "Desactivados", "Activos"};

    private Net.Page data;
    private final List<GuiEventListener> rowWidgets = new ArrayList<>();
    private EditBox searchBox;
    private Button bTipo, bEstado;
    private String search = "";
    private int tipo, estado, page, perPage = 8;

    public CraftScreen(Net.Page d) { super(Component.literal("Crafteos")); this.data = d; this.page = d.page(); }

    public static void update(Net.Page d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof CraftScreen s) { s.data = d; s.page = d.page(); s.layoutRows(); }
        else if (d.open()) mc.setScreen(new CraftScreen(d));
    }

    private static void send(String cmd) { Net.CH.sendToServer(new Net.Cmd(cmd)); }

    private void query() { Net.CH.sendToServer(new Net.Query(search, tipo, estado, page, perPage, false)); }

    private int panelW() { return Math.min(width - 16, 470); }

    @Override
    protected void init() {
        int w = panelW(), x0 = (width - w) / 2;
        perPage = Math.max(3, Math.min(40, (height - 100) / ROW_H));
        searchBox = new EditBox(font, x0, 20, w, 18, Component.literal("Buscar"));
        searchBox.setHint(Component.literal("Buscar por id (ej: diamond, tacz, ak47)..."));
        searchBox.setValue(search);
        searchBox.setResponder(v -> { search = v; page = 0; query(); });
        addRenderableWidget(searchBox);
        int cw = (w - 4) / 2;
        bTipo = addRenderableWidget(Button.builder(Component.empty(), b -> { tipo = (tipo + 1) % TIPO.length; page = 0; chips(); query(); })
                .bounds(x0, 42, cw, 18).tooltip(Tooltip.create(Component.literal("Que lista ver. \"Mods enteros\" muestra una fila por mod (modid:*) para apagar todo lo suyo de una vez."))).build());
        bEstado = addRenderableWidget(Button.builder(Component.empty(), b -> { estado = (estado + 1) % ESTADO.length; page = 0; chips(); query(); })
                .bounds(x0 + cw + 4, 42, cw, 18).build());
        addRenderableWidget(Button.builder(Component.literal("<"), b -> { if (page > 0) { page--; query(); } }).bounds(x0, height - 26, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; query(); }).bounds(x0 + 24, height - 26, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Recargar archivo"), b -> send("crafteos recargar")).bounds(x0 + w - 190, height - 26, 124, 20)
                .tooltip(Tooltip.create(Component.literal("Vuelve a leer config/crafteos/desactivados.json del servidor y lo aplica"))).build());
        addRenderableWidget(Button.builder(Component.literal("Cerrar"), b -> onClose()).bounds(x0 + w - 62, height - 26, 62, 20).build());
        chips();
        layoutRows();
        query();
    }

    private void chips() {
        bTipo.setMessage(Component.literal("Ver: " + TIPO[tipo]));
        bEstado.setMessage(Component.literal("Estado: " + ESTADO[estado]));
    }

    private void layoutRows() {
        for (GuiEventListener g : rowWidgets) removeWidget(g);
        rowWidgets.clear();
        int w = panelW(), x0 = (width - w) / 2, y = 66;
        for (Net.Row r : data.rows()) {
            boolean off = r.off();
            boolean ban = tipo >= 6;
            Component label = ban ? Component.literal(off ? "BAN" : "OK").withStyle(off ? ChatFormatting.RED : ChatFormatting.GREEN)
                    : Component.literal(off ? "OFF" : "ON").withStyle(off ? ChatFormatting.RED : ChatFormatting.GREEN);
            String cmdKey = r.key();
            Button b = Button.builder(label, bb -> send(ban ? (off ? "crafteos permitir " : "crafteos prohibir ") + cmdKey : (off ? "crafteos activar " : "crafteos desactivar ") + cmdKey))
                    .bounds(x0 + w - 40, y, 40, 20)
                    .tooltip(Tooltip.create(Component.literal(ban
                            ? (off ? "PROHIBIDO: no sale en cofres, no se craftea, se borra de inventarios y se oculta. Click: lo permite" : "Permitido. Click: lo prohibe (como si lo hubieras borrado del juego)")
                            : (off ? "Esta DESACTIVADO (nadie puede craftearlo). Click: lo activa" : "Se puede craftear. Click: lo desactiva en vivo")))).build();
            addRenderableWidget(b);
            rowWidgets.add(b);
            y += ROW_H;
        }
    }

    private static String kinds(int k) {
        List<String> l = new ArrayList<>();
        if ((k & 1) != 0) l.add("mesa");
        if ((k & 2) != 0) l.add("horno");
        if ((k & 4) != 0) l.add("herreria");
        if ((k & 8) != 0) l.add("cantero");
        if ((k & 16) != 0) l.add("mod");
        return String.join("+", l);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int w = panelW(), x0 = (width - w) / 2, y = 66;
        g.drawCenteredString(font, "Crafteos", width / 2, 6, 0xFFFFFF);
        for (Net.Row r : data.rows()) {
            String shown = r.key();
            ItemStack icon = ItemStack.EMPTY;
            ResourceLocation id = ResourceLocation.tryParse(r.key());
            if (id != null && ForgeRegistries.ITEMS.containsKey(id)) {
                Item it = ForgeRegistries.ITEMS.getValue(id);
                icon = new ItemStack(it);
                shown = it.getDescription().getString() + "  (" + r.key() + ")";
            } else if (r.key().endsWith(":*")) shown = "TODO el mod " + r.mod();
            if (!icon.isEmpty()) g.renderItem(icon, x0, y + 2);
            int tx = x0 + 20, maxW = w - 70;
            if (font.width(shown) > maxW) shown = font.plainSubstrByWidth(shown, maxW - 6) + "..";
            g.drawString(font, shown, tx, y + 1, r.off() ? 0xFF8888 : 0xFFFFFF, false);
            if (tipo >= 6) g.drawString(font, r.mod() + (r.off() ? " - PROHIBIDO (borrado del juego)" : " - permitido"), tx, y + 12, r.off() ? 0xFF5555 : 0x888888, false);
            else g.drawString(font, r.mod() + " - " + kinds(r.kinds()) + " - " + r.count() + (r.count() == 1 ? " receta" : " recetas") + (r.off() ? " - DESACTIVADO" : ""), tx, y + 12,
                    r.off() ? 0xFF5555 : 0x888888, false);
            y += ROW_H;
        }
        if (data.rows().isEmpty()) g.drawCenteredString(font, "Nada coincide con el filtro", width / 2, 90, 0x888888);
        int pages = Math.max(1, (data.total() + perPage - 1) / perPage);
        g.drawString(font, (data.page() + 1) + "/" + pages + "  (" + data.total() + ")" + (tipo >= 6 ? "  prohibidos: " : "  desactivados: ") + data.disabledTotal(), x0 + 50, height - 20, 0xAAAAAA, false);
        super.render(g, mx, my, pt);
    }

    @Override public boolean isPauseScreen() { return false; }
}
