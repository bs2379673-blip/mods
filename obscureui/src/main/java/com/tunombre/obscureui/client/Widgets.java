package com.tunombre.obscureui.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.DoubleFunction;

/** Widgets propios con el estilo de Obscure UI. */
final class Widgets {
    private Widgets() { }

    static final int PANEL = 0xF2110709, SIDE = 0xF20B0405, LINE = 0xFF3A1217, ACCENT = 0xFFC01830, TEXT = 0xFFEBDADA, MUTED = 0xFF9C8587, FIELD = 0xFF1B0C0F;

    static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t), g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t), bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    static class Btn extends AbstractWidget {
        final Runnable action;
        int color = ACCENT;
        boolean selected;
        boolean flat;   // sin relleno de color hasta que se selecciona o pasas el raton

        Btn(int x, int y, int w, int h, String label, Runnable action) {
            super(x, y, w, h, Component.literal(label));
            this.action = action;
        }

        Btn color(int c) { this.color = c; return this; }
        Btn flat() { this.flat = true; return this; }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            boolean hov = isHoveredOrFocused() && active;
            int base = selected ? color : (flat ? 0xFF160A0C : mix(0xFF1B0C0F, color, 0.28F));
            int bg = hov ? mix(base, color, 0.45F) : base;
            if (!active) bg = 0xFF140A0B;
            g.fill(getX(), getY(), getX() + width, getY() + height, bg);
            int edge = selected ? mix(color, 0xFFFFFFFF, 0.25F) : (hov ? color : LINE);
            g.fill(getX(), getY(), getX() + width, getY() + 1, edge);
            g.fill(getX(), getY() + height - 1, getX() + width, getY() + height, edge);
            g.fill(getX(), getY(), getX() + 1, getY() + height, edge);
            g.fill(getX() + width - 1, getY(), getX() + width, getY() + height, edge);
            var f = Minecraft.getInstance().font;
            String s = getMessage().getString();
            while (f.width(s) > width - 6 && s.length() > 2) s = s.substring(0, s.length() - 2);
            g.drawString(f, s, getX() + (width - f.width(s)) / 2, getY() + (height - 8) / 2, active ? (selected ? 0xFFFFFFFF : TEXT) : 0xFF6B5A5C, false);
        }

        @Override
        public void onClick(double mx, double my) { if (active) action.run(); }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) { defaultButtonNarrationText(out); }
    }

    static class Sld extends AbstractWidget {
        double v;
        final String name;
        final double max;
        final DoubleFunction<String> fmt;
        final Consumer<Double> onRelease;
        int color = ACCENT;

        Sld(int x, int y, int w, int h, String name, double v, double max, DoubleFunction<String> fmt, Consumer<Double> onRelease) {
            super(x, y, w, h, Component.literal(name));
            this.name = name; this.v = v; this.max = max; this.fmt = fmt; this.onRelease = onRelease;
        }

        void setFrom(double mx) { v = Math.max(0, Math.min(1, (mx - getX()) / (double) width)); }

        @Override
        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            g.fill(getX(), getY(), getX() + width, getY() + height, FIELD);
            int w = (int) (width * v);
            g.fill(getX(), getY(), getX() + w, getY() + height, mix(FIELD, color, 0.75F));
            g.fill(getX() + Math.max(0, w - 1), getY(), getX() + Math.min(width, w + 1), getY() + height, 0xFFFFFFFF);
            var f = Minecraft.getInstance().font;
            String s = name + ": " + fmt.apply(v * max);
            g.drawString(f, s, getX() + 4, getY() + (height - 8) / 2, TEXT, true);
        }

        @Override
        public void onClick(double mx, double my) { setFrom(mx); }

        @Override
        protected void onDrag(double mx, double my, double dx, double dy) { setFrom(mx); }

        @Override
        public void onRelease(double mx, double my) { onRelease.accept(v * max); }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) { defaultButtonNarrationText(out); }
    }
}
