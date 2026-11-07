package com.starlevi.starscenes.client;

import com.starlevi.starscenes.client.ui.StarButton;
import com.starlevi.starscenes.client.ui.UI;
import com.starlevi.starscenes.net.Net;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * El cuadro de dialogo: abajo de la pantalla (se sigue viendo al NPC), con su nombre, el texto que se
 * va escribiendo y las respuestas numeradas (se eligen con el mouse o con 1-9).
 */
public class TalkScreen extends Screen {
    private static final float CHARS_PER_SEC = 55;

    private Net.Talk talk;
    private long started;
    private int shownChars = -1, lastBlip;
    private boolean answered;

    private TalkScreen(Net.Talk t) {
        super(Component.literal("Diálogo"));
        this.talk = t;
        this.started = System.currentTimeMillis();
    }

    public static void show(Net.Talk t) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof TalkScreen ts) {
            ts.talk = t;
            ts.started = System.currentTimeMillis();
            ts.shownChars = -1;
            ts.answered = false;
            ts.rebuildWidgets();
        } else {
            mc.setScreen(new TalkScreen(t));
        }
    }

    public static void end() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof TalkScreen ts) {
            ts.answered = true;
            mc.setScreen(null);
        }
    }

    private int panelW() {
        return Math.min(440, this.width - 24);
    }

    private int visible(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '§') {
                i++;
            } else {
                n++;
            }
        }
        return n;
    }

    /** El texto hasta n letras (sin contar los codigos de color). */
    private static String upTo(String s, int n) {
        StringBuilder b = new StringBuilder();
        int k = 0;
        for (int i = 0; i < s.length() && k < n; i++) {
            char c = s.charAt(i);
            b.append(c);
            if (c == '§' && i + 1 < s.length()) {
                b.append(s.charAt(++i));
            } else {
                k++;
            }
        }
        return b.toString();
    }

    private boolean typing() {
        return currentChars() < visible(this.talk.text());
    }

    private int currentChars() {
        if (this.shownChars >= 0) {
            return this.shownChars;
        }
        return (int) ((System.currentTimeMillis() - this.started) / 1000f * CHARS_PER_SEC);
    }

    private void skip() {
        this.shownChars = visible(this.talk.text());
        rebuildWidgets();
    }

    private List<FormattedCharSequence> lines(String text) {
        return this.font.split(Component.literal(text), panelW() - 24);
    }

    private int optionsTop() {
        int pw = panelW();
        int textH = Math.max(1, lines(this.talk.text()).size()) * 10;
        int optH = Math.max(1, this.talk.options().size()) * 18;
        int ph = 20 + textH + 8 + optH + 8;
        int py = this.height - ph - 10;
        return py + 20 + textH + 8;
    }

    @Override
    protected void init() {
        int pw = panelW(), px = (this.width - pw) / 2;
        int y = optionsTop();
        if (typing()) {
            return;
        }
        List<String> opts = this.talk.options();
        if (opts.isEmpty()) {
            addRenderableWidget(new StarButton(px + 10, y, pw - 20, 16, "Seguir", StarButton.Style.GHOST, () -> choose(0)).hint("▸"));
            return;
        }
        for (int i = 0; i < opts.size(); i++) {
            int idx = i;
            addRenderableWidget(new StarButton(px + 10, y + i * 18, pw - 20, 16, opts.get(i), StarButton.Style.NORMAL, () -> choose(idx)).left()
                    .hint(i < 9 ? String.valueOf(i + 1) : null));
        }
    }

    private void choose(int i) {
        if (this.answered) {
            return;
        }
        this.answered = true;
        ClientPlayNetworking.send(new Net.Choose(i));
    }

    @Override
    public void tick() {
        // termino de escribirse: aparecen las respuestas
        if (this.children().isEmpty() && !typing()) {
            rebuildWidgets();
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
        // sin desenfoque: se sigue viendo el mundo; solo se oscurece abajo
        g.fillGradient(0, this.height / 2, this.width, this.height, 0x00000000, 0x90000000);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        int pw = panelW(), px = (this.width - pw) / 2;
        List<FormattedCharSequence> all = lines(this.talk.text());
        int textH = Math.max(1, all.size()) * 10;
        int optH = Math.max(1, this.talk.options().size()) * 18;
        int ph = 20 + textH + 8 + optH + 8;
        int py = this.height - ph - 10;
        UI.panel(g, px, py, pw, ph);
        // nombre del NPC en una solapa
        String name = this.talk.name().isEmpty() ? "" : this.talk.name();
        if (!name.isEmpty()) {
            int nw = this.font.width(name) + 16;
            UI.box(g, px + 10, py - 9, nw, 14, UI.BG3, UI.ACCENT);
            g.drawString(this.font, name, px + 18, py - 6, UI.GOLD, false);
        }
        // texto escribiendose
        int n = currentChars();
        List<FormattedCharSequence> shown = lines(upTo(this.talk.text(), n));
        for (int i = 0; i < shown.size(); i++) {
            g.drawString(this.font, shown.get(i), px + 12, py + 14 + i * 10, UI.TEXT, false);
        }
        if (typing()) {
            // un "blip" suave cada tantas letras
            if (n / 3 != this.lastBlip) {
                this.lastBlip = n / 3;
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT.value(), 1.6f, 0.15f));
            }
            if ((System.currentTimeMillis() / 400) % 2 == 0) {
                g.drawString(this.font, "▾", px + pw - 16, py + ph - 14, UI.MUTED, false);
            }
        } else {
            UI.separator(g, px + 10, py + 20 + textH + 2, pw - 20);
        }
        super.render(g, mx, my, partial);
        if (typing()) {
            g.drawString(this.font, "clic para saltar", px + pw - 10 - this.font.width("clic para saltar"), py + ph + 1, 0x80FFFFFF, false);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (typing()) {
            skip();
            return true;
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        int k = e.key();
        if (k == GLFW.GLFW_KEY_SPACE || k == GLFW.GLFW_KEY_ENTER) {
            if (typing()) {
                skip();
                return true;
            }
            if (this.talk.options().isEmpty()) {
                choose(0);
                return true;
            }
        }
        if (!typing() && k >= GLFW.GLFW_KEY_1 && k <= GLFW.GLFW_KEY_9) {
            int i = k - GLFW.GLFW_KEY_1;
            if (i < this.talk.options().size()) {
                choose(i);
                return true;
            }
        }
        return super.keyPressed(e);
    }

    @Override
    public void onClose() {
        if (!this.answered) {
            this.answered = true;
            ClientPlayNetworking.send(new Net.Choose(-1));
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** El NPC que habla (o null). */
    public Entity speaker() {
        return Minecraft.getInstance().level == null || this.talk.speaker() < 0 ? null : Minecraft.getInstance().level.getEntity(this.talk.speaker());
    }
}
