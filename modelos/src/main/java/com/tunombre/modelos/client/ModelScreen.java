package com.tunombre.modelos.client;

import com.tunombre.modelos.Net;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** /modelos gui: elegis un jugador a la izquierda, un modelo a la derecha, y pones / quitas. Rueda del mouse para desplazar cada lista. */
public class ModelScreen extends Screen {
    private static final int ROW_H = 20;
    private Net.Data data;
    private String selPlayer = "", selModel = "";
    private int scrollP, scrollM, perPage = 8;
    private final List<GuiEventListener> rowWidgets = new ArrayList<>();

    public ModelScreen(Net.Data d) { super(Component.literal("Modelos de jugador")); this.data = d; }

    public static void update(Net.Data d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof ModelScreen s) { s.data = d; s.layoutRows(); }
        else if (d.open()) mc.setScreen(new ModelScreen(d));
    }

    private static void send(String cmd) { Net.CH.sendToServer(new Net.Cmd(cmd)); }

    private int panelW() { return Math.min(width - 16, 440); }

    @Override
    protected void init() {
        int w = panelW(), x0 = (width - w) / 2, by = height - 26, ay = height - 50;
        perPage = Math.max(3, (height - 130) / ROW_H);
        int bw = (w - 8) / 3;
        addRenderableWidget(Button.builder(Component.literal("Poner (forzado)"), b -> apply(false)).bounds(x0, ay, bw, 20)
                .tooltip(Tooltip.create(Component.literal("Le pone el modelo y no puede cambiarlo. Epic Fight se apaga para el.\\nQueda guardado aunque se desconecte."))).build());
        addRenderableWidget(Button.builder(Component.literal("Poner temporal"), b -> apply(true)).bounds(x0 + bw + 4, ay, bw, 20)
                .tooltip(Tooltip.create(Component.literal("Igual, pero el modelo se borra cuando el jugador se desconecta."))).build());
        addRenderableWidget(Button.builder(Component.literal("Quitar modelo"), b -> { if (!selPlayer.isEmpty()) send("modelos quitar " + selPlayer); }).bounds(x0 + 2 * (bw + 4), ay, bw, 20)
                .tooltip(Tooltip.create(Component.literal("Le saca el modelo al jugador elegido y le devuelve Epic Fight."))).build());
        addRenderableWidget(Button.builder(Component.literal("Recargar lista"), b -> send("modelos recargar")).bounds(x0, by, 100, 20)
                .tooltip(Tooltip.create(Component.literal("Vuelve a leer la carpeta config/modelos/ del servidor"))).build());
        addRenderableWidget(Button.builder(Component.literal("Cerrar"), b -> onClose()).bounds(x0 + w - 62, by, 62, 20).build());
        layoutRows();
    }

    private void apply(boolean temp) {
        if (selPlayer.isEmpty() || selModel.isEmpty()) return;
        send("modelos poner " + selPlayer + " " + selModel + (temp ? " temporal" : ""));
    }

    private void layoutRows() {
        for (GuiEventListener g : rowWidgets) removeWidget(g);
        rowWidgets.clear();
        int w = panelW(), x0 = (width - w) / 2, colW = (w - 8) / 2, y0 = 44;
        scrollP = Math.max(0, Math.min(scrollP, Math.max(0, data.players().size() - perPage)));
        scrollM = Math.max(0, Math.min(scrollM, Math.max(0, data.models().size() - perPage)));
        for (int i = 0; i < perPage; i++) {
            int y = y0 + i * ROW_H;
            if (scrollP + i < data.players().size()) {
                Net.Who p = data.players().get(scrollP + i);
                boolean sel = p.name().equals(selPlayer);
                String t = (sel ? "> " : "") + p.name() + (p.model().isEmpty() ? "" : "  [" + p.model() + "]");
                Component c = Component.literal(t).withStyle(sel ? ChatFormatting.YELLOW : ChatFormatting.WHITE);
                add(Button.builder(c, b -> { selPlayer = p.name(); layoutRows(); }).bounds(x0, y, colW, ROW_H - 2).build());
            }
            if (scrollM + i < data.models().size()) {
                String m = data.models().get(scrollM + i);
                boolean sel = m.equals(selModel);
                Component c = Component.literal((sel ? "> " : "") + m).withStyle(sel ? ChatFormatting.YELLOW : ChatFormatting.WHITE);
                add(Button.builder(c, b -> { selModel = m; layoutRows(); }).bounds(x0 + colW + 8, y, colW, ROW_H - 2).build());
            }
        }
    }

    private void add(Button b) { addRenderableWidget(b); rowWidgets.add(b); }

    @Override
    public boolean mouseScrolled(double mx, double my, double d) {
        int w = panelW(), x0 = (width - w) / 2;
        if (mx < x0 + w / 2.0) scrollP -= (int) Math.signum(d); else scrollM -= (int) Math.signum(d);
        layoutRows();
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int w = panelW(), x0 = (width - w) / 2, colW = (w - 8) / 2;
        g.drawCenteredString(font, "Modelos de jugador", width / 2, 8, 0xFFFFFF);
        g.drawString(font, "Jugadores (" + data.players().size() + ")", x0, 32, 0xAAAAAA, false);
        g.drawString(font, "Modelos en config/modelos (" + data.models().size() + ")", x0 + colW + 8, 32, 0xAAAAAA, false);
        if (data.models().isEmpty()) g.drawString(font, "Carpeta vacia: pone .cpmodel o .b64", x0 + colW + 12, 50, 0x888888, false);
        int ty = height - 72;
        String sel = (selPlayer.isEmpty() ? "jugador: -" : "jugador: " + selPlayer) + "   " + (selModel.isEmpty() ? "modelo: -" : "modelo: " + selModel);
        g.drawString(font, sel, x0, ty, 0xFFFFFF, false);
        if (!data.cpm()) g.drawString(font, "OJO: Custom Player Models no esta instalado en el servidor", x0, ty - 12, 0xFF5555, false);
        else if (!data.epic()) g.drawString(font, "Epic Fight no esta instalado: solo se aplican los modelos", x0, ty - 12, 0xFFCC33, false);
        super.render(g, mx, my, pt);
    }

    @Override public boolean isPauseScreen() { return false; }
}
