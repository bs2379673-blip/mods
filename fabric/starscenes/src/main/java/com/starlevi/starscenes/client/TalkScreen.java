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
import net.minecraft.world.entity.Entity;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Dialogo estilo novela visual: una caja ancha abajo al centro con el nombre del que habla en una
 * solapa, el texto que se va escribiendo (por paginas si es largo) y un ▼ para seguir. Las respuestas
 * aparecen como botones grandes en el medio de la pantalla. Se juega con el mouse, Espacio/Enter y 1-9.
 */
public class TalkScreen extends Screen {
    private static final float CHARS_PER_SEC = 50;
    private static final int LINES_PER_PAGE = 3;

    private Net.Talk talk;
    private List<String> pages = List.of();
    private int page;
    private long pageStart, opened;
    private int shownChars = -1, lastVoiced;
    private final VoicePlayer voice = new VoicePlayer();
    private boolean answered;

    private TalkScreen(Net.Talk t) {
        super(Component.literal("Diálogo"));
        this.opened = System.currentTimeMillis();
        set(t);
    }

    private void set(Net.Talk t) {
        this.talk = t;
        this.page = 0;
        this.pageStart = System.currentTimeMillis();
        this.shownChars = -1;
        this.lastVoiced = 0;
        this.answered = false;
        this.pages = List.of();
    }

    public static void show(Net.Talk t) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof TalkScreen ts) {
            ts.set(t);
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

    // ---------------------------------------------------------------- medidas

    private int boxW() {
        return Math.min(500, this.width - 30);
    }

    private int boxH() {
        return 18 + LINES_PER_PAGE * 11 + 12;
    }

    private int boxX() {
        return (this.width - boxW()) / 2;
    }

    private int boxY() {
        return this.height - boxH() - 10;
    }

    // ---------------------------------------------------------------- texto por paginas

    /** Parte el texto en paginas de pocas lineas, respetando los colores. */
    private List<String> pages() {
        if (this.pages.isEmpty()) {
            List<String> out = new ArrayList<>();
            List<String> lines = wrap(this.talk.text(), boxW() - 32);
            for (int i = 0; i < Math.max(1, lines.size()); i += LINES_PER_PAGE) {
                out.add(String.join("\n", lines.subList(Math.min(i, lines.size()), Math.min(lines.size(), i + LINES_PER_PAGE))));
            }
            this.pages = out;
        }
        return this.pages;
    }

    /** Corta en lineas por palabras; el color sigue en la linea siguiente. */
    private List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        String color = "";
        for (String para : text.split("\n")) {
            StringBuilder line = new StringBuilder(color);
            for (String word : para.split(" ")) {
                String tryLine = line.length() == color.length() ? line + word : line + " " + word;
                if (this.font.width(tryLine) > width && line.length() > color.length()) {
                    lines.add(line.toString());
                    color = lastColor(line.toString(), color);
                    line = new StringBuilder(color).append(word);
                } else {
                    line = new StringBuilder(tryLine);
                }
            }
            lines.add(line.toString());
            color = lastColor(line.toString(), color);
        }
        return lines;
    }

    private static String lastColor(String s, String prev) {
        String c = prev;
        for (int i = 0; i + 1 < s.length(); i++) {
            if (s.charAt(i) == '§') {
                char k = Character.toLowerCase(s.charAt(i + 1));
                c = k == 'r' ? "" : "§" + k;
            }
        }
        return c;
    }

    private String pageText() {
        List<String> p = pages();
        return p.get(Math.min(this.page, p.size() - 1));
    }

    private boolean lastPage() {
        return this.page >= pages().size() - 1;
    }

    private static int visible(String s) {
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

    private int currentChars() {
        if (this.shownChars >= 0) {
            return this.shownChars;
        }
        return (int) ((System.currentTimeMillis() - this.pageStart) / 1000f * CHARS_PER_SEC);
    }

    private boolean typing() {
        return currentChars() < visible(pageText());
    }

    private boolean choosing() {
        return !typing() && lastPage() && !this.talk.options().isEmpty();
    }

    /** Clic / Espacio: completa la pagina, pasa a la siguiente o sigue la charla. */
    private void advance() {
        if (typing()) {
            this.shownChars = visible(pageText());
        } else if (!lastPage()) {
            this.page++;
            this.pageStart = System.currentTimeMillis();
            this.shownChars = -1;
            this.lastVoiced = 0;
            click();
        } else if (this.talk.options().isEmpty()) {
            click();
            choose(0);
        }
        rebuildWidgets();
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.4f, 0.25f));
    }

    // ---------------------------------------------------------------- respuestas

    @Override
    protected void init() {
        if (!choosing()) {
            return;
        }
        List<String> opts = this.talk.options();
        int bw = Math.min(300, this.width - 60), bh = 20, gap = 6;
        int total = opts.size() * (bh + gap) - gap;
        int top = Math.max(10, (boxY() - total) / 2);
        for (int i = 0; i < opts.size(); i++) {
            int idx = i;
            addRenderableWidget(new StarButton((this.width - bw) / 2, top + i * (bh + gap), bw, bh, opts.get(i), StarButton.Style.CHOICE, () -> {
                click();
                choose(idx);
            }).hint(i < 9 ? String.valueOf(i + 1) : null));
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
        // termino de escribirse la ultima pagina: aparecen las respuestas
        if (this.children().isEmpty() && choosing()) {
            rebuildWidgets();
        }
    }

    // ---------------------------------------------------------------- dibujo

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
        // sin desenfoque: se sigue viendo al NPC; un poco mas oscuro abajo y mientras se elige
        g.fillGradient(0, this.height / 3, this.width, this.height, 0x00000000, 0xA0000000);
        if (choosing()) {
            g.fill(0, 0, this.width, this.height, 0x40000000);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        // la caja entra subiendo un poco
        float in = Math.min(1f, (System.currentTimeMillis() - this.opened) / 180f);
        int slide = (int) ((1 - in * in) * 16);
        int bw = boxW(), bh = boxH(), bx = boxX(), by = boxY() + slide;

        g.fill(bx + 3, by + 4, bx + bw + 3, by + bh + 4, 0x55000000);
        UI.box(g, bx, by, bw, bh, 0xE8120E1C, 0xFF6A5492);
        UI.box(g, bx + 2, by + 2, bw - 4, bh - 4, 0x00000000, 0x553D3156);
        g.fillGradient(bx + 1, by + bh - 3, bx + bw - 1, by + bh - 1, 0x00C58BFF, 0x60C58BFF);

        // solapa con el nombre
        String name = this.talk.name();
        if (!name.isEmpty()) {
            int nw = Math.max(70, this.font.width(name) + 24);
            int nx = bx + 14, ny = by - 12;
            UI.box(g, nx, ny, nw, 17, 0xFF7E4FC0, 0xFFE2C4FF);
            g.fillGradient(nx + 1, ny + 1, nx + nw - 1, ny + 8, 0x30FFFFFF, 0x00FFFFFF);
            g.drawString(this.font, name, nx + (nw - this.font.width(name)) / 2, ny + 5, 0xFFFFFFFF, true);
        }

        // texto
        int n = currentChars();
        String[] shown = upTo(pageText(), n).split("\n", -1);
        for (int i = 0; i < shown.length; i++) {
            g.drawString(this.font, shown[i], bx + 16, by + 16 + i * 11, 0xFFF4EEFB, true);
        }
        // la voz: un sonidito por letra nueva
        if (typing() && n > this.lastVoiced) {
            String plain = pageText().replaceAll("§.", "");
            int at = Math.min(n, plain.length()) - 1;
            if (at >= 0) {
                boolean rising = lastPage() && plain.trim().endsWith("?") && at >= plain.length() - 6;
                this.voice.letter(plain.charAt(at), this.talk.voice(), this.talk.pitch(), rising);
            }
            this.lastVoiced = n;
        }

        // indicador para seguir (late) o el contador de paginas
        if (!typing() && !choosing()) {
            int bob = (int) (Math.sin(System.currentTimeMillis() / 160.0) * 2);
            g.drawString(this.font, "▼", bx + bw - 18, by + bh - 14 + bob, UI.GOLD, true);
        }
        if (pages().size() > 1) {
            String pg = (this.page + 1) + "/" + pages().size();
            g.drawString(this.font, pg, bx + bw - 28 - this.font.width(pg), by + bh - 13, UI.MUTED, false);
        }
        if (choosing()) {
            String hint = "Elegí una respuesta";
            g.drawString(this.font, hint, bx + bw - 12 - this.font.width(hint), by + bh - 13, UI.MUTED, false);
        }
        super.render(g, mx, my, partial);
    }

    // ---------------------------------------------------------------- entrada

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (choosing()) {
            return super.mouseClicked(e, doubleClick);
        }
        advance();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        int k = e.key();
        if (k == GLFW.GLFW_KEY_SPACE || k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) {
            if (!choosing()) {
                advance();
                return true;
            }
        }
        if (choosing() && k >= GLFW.GLFW_KEY_1 && k <= GLFW.GLFW_KEY_9) {
            int i = k - GLFW.GLFW_KEY_1;
            if (i < this.talk.options().size()) {
                click();
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
