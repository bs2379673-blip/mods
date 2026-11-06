package com.starlevi.starframes.client;

import com.starlevi.starframes.block.ScreenBlockEntity;
import com.starlevi.starframes.net.Net;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Menu de una pantalla: que mostrar, en que canal, de que tamaño, a cuantos bloques se escucha. */
public class ScreenMenu extends Screen {
    private final BlockPos pos;
    private EditBox url, channel;

    public ScreenMenu(BlockPos pos) {
        super(Component.literal("Pantalla"));
        this.pos = pos;
    }

    private ScreenBlockEntity be() {
        return Minecraft.getInstance().level != null && Minecraft.getInstance().level.getBlockEntity(this.pos) instanceof ScreenBlockEntity s ? s : null;
    }

    private void send(String field, String value) {
        ClientPlayNetworking.send(new Net.EditScreen(this.pos, field, value));
    }

    private void step(String field, float cur, float delta) {
        send(field, String.format(Locale.ROOT, "%.3f", cur + delta));
    }

    @Override
    protected void init() {
        ScreenBlockEntity s = be();
        if (s == null) {
            onClose();
            return;
        }
        int x = this.width / 2 - 160, y = 30;
        // que mostrar
        this.url = new EditBox(this.font, x, y, 236, 20, Component.literal("Link"));
        this.url.setMaxLength(2048);
        this.url.setHint(Component.literal("Link de video, imagen, GIF o YouTube"));
        Net.ChannelState st = ClientChannels.state(s.channel());
        if (st != null) {
            this.url.setValue(st.current());
        }
        addRenderableWidget(this.url);
        addRenderableWidget(Button.builder(Component.literal("▶ Reproducir"), b -> send("url", this.url.getValue())).bounds(x + 240, y, 80, 20).build());
        y += 30;
        // canal: las pantallas con el mismo nombre muestran lo mismo
        this.channel = new EditBox(this.font, x, y, 156, 20, Component.literal("Canal"));
        this.channel.setMaxLength(32);
        this.channel.setValue(s.channel());
        addRenderableWidget(this.channel);
        addRenderableWidget(Button.builder(Component.literal("Vincular a este canal"), b -> send("canal", this.channel.getValue()))
                .bounds(x + 160, y, 160, 20).build());
        y += 34;
        // tamaño
        row(x, y, "ancho", () -> be().width(), 0.25f);
        y += 24;
        row(x, y, "alto", () -> be().height(), 0.25f);
        addRenderableWidget(Button.builder(Component.literal("16:9"), b -> send("alto", fmt(be().width() * 9 / 16))).bounds(x + 250, y, 34, 20).build());
        addRenderableWidget(Button.builder(Component.literal("4:3"), b -> send("alto", fmt(be().width() * 3 / 4))).bounds(x + 286, y, 34, 20).build());
        y += 24;
        row(x, y, "corrida_x", () -> be().offsetX(), 0.25f);
        y += 24;
        row(x, y, "corrida_y", () -> be().offsetY(), 0.25f);
        y += 24;
        row(x, y, "alcance", () -> (float) be().range(), 4f);
        y += 28;
        addRenderableWidget(Button.builder(Component.literal("Brillo propio / luz del lugar"), b -> send("brillo", String.valueOf(!be().bright())))
                .bounds(x, y, 200, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Controles ▸"), b -> {
            ScreenBlockEntity e = be();
            if (e != null) {
                Minecraft.getInstance().setScreen(new RemoteMenu(e.channel()));
            }
        }).bounds(x + 220, y, 100, 20).build());
        y += 28;
        addRenderableWidget(Button.builder(Component.literal("Listo"), b -> onClose()).bounds(x + 220, y, 100, 20).build());
    }

    private static String fmt(float v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }

    private void row(int x, int y, String field, java.util.function.Supplier<Float> cur, float step) {
        addRenderableWidget(Button.builder(Component.literal("-"), b -> step(field, cur.get(), -step)).bounds(x + 170, y, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> step(field, cur.get(), step)).bounds(x + 222, y, 20, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        ScreenBlockEntity s = be();
        if (s == null) {
            return;
        }
        int x = this.width / 2 - 160, y = 30 + 30 + 34;
        g.drawCenteredString(this.font, "✦ Pantalla ✦", this.width / 2, 12, 0xFFE9B8FF);
        String[] names = {"Ancho (bloques)", "Alto (bloques)", "Corrida a la derecha", "Corrida hacia arriba", "Se escucha hasta (bloques)"};
        float[] vals = {s.width(), s.height(), s.offsetX(), s.offsetY(), s.range()};
        for (int i = 0; i < names.length; i++) {
            g.drawString(this.font, names[i], x, y + 6 + i * 24, 0xFFB0B0B0);
            g.drawCenteredString(this.font, i == 4 ? String.valueOf((int) vals[i]) : String.format(Locale.ROOT, "%.2f", vals[i]), x + 206, y + 6 + i * 24,
                    0xFFFFFFFF);
        }
        g.drawString(this.font, s.bright() ? "Brilla sola" : "Toma la luz del lugar", x, y + 5 * 24 + 32, 0xFF8A8098);
        g.drawString(this.font, "Estado: " + ClientChannels.status(s.channel()), x, 30 + 23, 0xFF8A8098);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
