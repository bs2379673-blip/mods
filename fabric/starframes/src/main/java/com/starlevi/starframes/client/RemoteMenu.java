package com.starlevi.starframes.client;

import com.starlevi.starframes.net.Net;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** El control remoto de un canal: play, pausa, adelantar, volumen, repetir y la lista de reproduccion. */
public class RemoteMenu extends Screen {
    private static final int ROWS = 7;
    private final String channel;
    private EditBox add;
    private String keep = "";
    private int scroll, seen = -1;

    public RemoteMenu(String channel) {
        super(Component.literal("Control remoto"));
        this.channel = channel;
    }

    private void send(String action, String value) {
        ClientPlayNetworking.send(new Net.Control(this.channel, action, value));
    }

    @Override
    protected void init() {
        this.seen = ClientChannels.version;
        Net.ChannelState st = ClientChannels.state(this.channel);
        int x = this.width / 2 - 160, y = 40;
        String[][] buttons = {{"⏮", "anterior", ""}, {"-10s", "saltar", "-10000"}, {"⏯", "pausa", ""}, {"+10s", "saltar", "10000"},
                {"⏭", "siguiente", ""}, {"⏹", "parar", ""}};
        for (int i = 0; i < buttons.length; i++) {
            String[] b = buttons[i];
            addRenderableWidget(Button.builder(Component.literal(b[0]), w -> send(b[1], b[2])).bounds(x + i * 54, y, 50, 20).build());
        }
        y += 26;
        int vol = st == null ? 80 : st.volume();
        addRenderableWidget(Button.builder(Component.literal("🔉 -"), w -> send("volumen", String.valueOf(Math.max(0, vol - 10)))).bounds(x, y, 30, 20).build());
        addRenderableWidget(Button.builder(Component.literal("🔊 +"), w -> send("volumen", String.valueOf(Math.min(100, vol + 10)))).bounds(x + 34, y, 30, 20).build());
        addRenderableWidget(Button.builder(Component.literal((st != null && st.loopOne() ? "§a" : "") + "Repetir este"), w -> send("repetir_uno", ""))
                .bounds(x + 140, y, 88, 20).build());
        addRenderableWidget(Button.builder(Component.literal((st != null && st.loopList() ? "§a" : "") + "Repetir lista"), w -> send("repetir_lista", ""))
                .bounds(x + 232, y, 88, 20).build());
        y += 34;
        // lista
        List<String> list = st == null ? List.of() : st.list();
        this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, list.size() - ROWS)));
        for (int r = 0; r < ROWS && r + this.scroll < list.size(); r++) {
            int i = r + this.scroll;
            String label = (st.index() == i ? "§e▶ " : "   ") + (i + 1) + ". " + shorten(list.get(i));
            addRenderableWidget(Button.builder(Component.literal(label), w -> send("elegir", String.valueOf(i))).bounds(x, y + r * 22, 236, 20).build());
            addRenderableWidget(Button.builder(Component.literal("↑"), w -> send("subir", String.valueOf(i))).bounds(x + 240, y + r * 22, 24, 20).build());
            addRenderableWidget(Button.builder(Component.literal("↓"), w -> send("bajar", String.valueOf(i))).bounds(x + 268, y + r * 22, 24, 20).build());
            addRenderableWidget(Button.builder(Component.literal("§c✕"), w -> send("quitar", String.valueOf(i))).bounds(x + 296, y + r * 22, 24, 20).build());
        }
        y += ROWS * 22 + 6;
        this.add = new EditBox(this.font, x, y, 236, 20, Component.literal("Agregar"));
        this.add.setMaxLength(2048);
        this.add.setHint(Component.literal("Link para agregar a la lista"));
        this.add.setValue(this.keep);
        this.add.setResponder(v -> this.keep = v);
        addRenderableWidget(this.add);
        addRenderableWidget(Button.builder(Component.literal("Agregar"), w -> {
            if (!this.add.getValue().isBlank()) {
                send("agregar", this.add.getValue());
                this.keep = "";
                this.add.setValue("");
            }
        }).bounds(x + 240, y, 80, 20).build());
        y += 28;
        addRenderableWidget(Button.builder(Component.literal("Listo"), w -> onClose()).bounds(x + 240, y, 80, 20).build());
    }

    private static String shorten(String url) {
        String u = url.replaceFirst("^https?://(www\\.)?", "");
        if (!url.matches("^[a-zA-Z]+://.*")) {
            // archivo de la compu: solo el nombre
            u = u.substring(Math.max(u.lastIndexOf('/'), u.lastIndexOf('\\')) + 1);
        }
        return u.length() > 34 ? u.substring(0, 33) + "…" : u;
    }

    @Override
    public void tick() {
        super.tick();
        // el servidor cambio algo (otro control, termino un video): se rearma
        if (this.seen != ClientChannels.version) {
            rebuildWidgets();
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        this.scroll -= (int) Math.signum(dy);
        rebuildWidgets();
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        Net.ChannelState st = ClientChannels.state(this.channel);
        int x = this.width / 2 - 160;
        g.drawCenteredString(this.font, "✦ Control remoto · canal " + this.channel + " ✦", this.width / 2, 12, 0xFFE9B8FF);
        long[] t = ClientChannels.time(this.channel);
        String time = t == null ? "" : clock(t[0]) + (t[1] > 0 ? " / " + clock(t[1]) : "");
        g.drawString(this.font, ClientChannels.status(this.channel) + "  " + time, x, 28, 0xFFB0B0B0);
        if (st != null) {
            g.drawString(this.font, "Volumen " + st.volume() + "%", x + 70, 66 + 6, 0xFF8A8098);
            if (st.list().isEmpty()) {
                g.drawString(this.font, "La lista está vacía: agregá un link abajo", x, 100 + 6, 0xFF808080);
            }
        }
    }

    private static String clock(long ms) {
        long s = ms / 1000;
        return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format("%d:%02d", s / 60, s % 60);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
