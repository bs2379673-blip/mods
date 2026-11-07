package com.starlevi.starbody.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.starlevi.starbody.Traits;
import com.starlevi.starbody.client.ui.StarButton;
import com.starlevi.starbody.client.ui.UI;
import com.starlevi.starbody.net.Net;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * El editor de rasgos: tu skin grande, donde pintas los pixeles que son pelo (en la cabeza, el
 * gorro, el torso o la chaqueta), y al lado tu personaje en 3D que ya se mueve con ese pelo.
 */
public class EditorScreen extends Screen {
    private static final String[] MOTION = {"Suave", "Normal", "Mucho"};

    private final Identifier skinId;
    private final String skin;
    private final Traits work;
    private final Traits original;
    private boolean erase;
    private int brush = 1;
    private boolean saved;
    private int dragging = -1;

    public EditorScreen() {
        super(Component.literal("StarBody"));
        this.skinId = StarBodyClient.mySkin();
        this.skin = this.skinId.toString();
        Traits cur = ClientTraits.get(this.skin);
        this.original = cur == null ? null : cur.copy();
        this.work = cur == null ? new Traits() : cur.copy();
        ClientTraits.draftSkin = this.skin;
        ClientTraits.draft = this.work;
        ClientTraits.changed(this.skin);
    }

    // ---------------------------------------------------------------- medidas

    private int pw() {
        return Math.min(470, this.width - 12);
    }

    private int ph() {
        return Math.min(300, this.height - 12);
    }

    private int px() {
        return (this.width - pw()) / 2;
    }

    private int py() {
        return (this.height - ph()) / 2;
    }

    /** Tamaño de cada pixel de la skin en pantalla. */
    private int cell() {
        return Math.max(2, Math.min((ph() - 44) / 64, (pw() - 190) / 64));
    }

    private int gx() {
        return px() + 10;
    }

    private int gy() {
        return py() + 30;
    }

    // ---------------------------------------------------------------- botones

    @Override
    protected void init() {
        int right = px() + pw() - 10, x = right - 160, y = py() + 30;
        addRenderableWidget(new StarButton(x, y, 78, 18, "✎ Pincel", this.erase ? StarButton.Style.TAB : StarButton.Style.TAB_ON, () -> {
            this.erase = false;
            rebuildWidgets();
        }));
        addRenderableWidget(new StarButton(x + 82, y, 78, 18, "⌫ Borrar", this.erase ? StarButton.Style.TAB_ON : StarButton.Style.TAB, () -> {
            this.erase = true;
            rebuildWidgets();
        }));
        y += 22;
        addRenderableWidget(new StarButton(x, y, 160, 18, "Tamaño: " + (this.brush == 1 ? "fino (1)" : "grueso (2)"), StarButton.Style.NORMAL, () -> {
            this.brush = this.brush == 1 ? 2 : 1;
            rebuildWidgets();
        }));
        // el personaje en 3D va en el medio de esta columna
        y = py() + ph() - 98;
        for (int i = 0; i < 3; i++) {
            int m = i;
            addRenderableWidget(new StarButton(x + i * 54, y, 52, 18, MOTION[i], this.work.motion == i ? StarButton.Style.TAB_ON : StarButton.Style.TAB, () -> {
                this.work.motion = m;
                ClientTraits.changed(this.skin);
                rebuildWidgets();
            }));
        }
        y += 24;
        addRenderableWidget(new StarButton(x, y, 78, 18, "✦ Automático", StarButton.Style.NORMAL, this::auto));
        addRenderableWidget(new StarButton(x + 82, y, 78, 18, "Borrar todo", StarButton.Style.DANGER, () -> {
            this.work.hair.clear();
            ClientTraits.changed(this.skin);
        }));
        y += 24;
        addRenderableWidget(new StarButton(x, y, 78, 18, "Cancelar", StarButton.Style.NORMAL, this::onClose));
        addRenderableWidget(new StarButton(x + 82, y, 78, 18, "Guardar", StarButton.Style.ACCENT, this::save));
    }

    private void save() {
        this.saved = true;
        byte[] data = this.work.empty() ? new byte[0] : this.work.encode();
        ClientTraits.draftSkin = null;
        ClientTraits.draft = null;
        ClientTraits.put(this.skin, this.work.empty() ? null : this.work.copy());
        StarBodyClient.saveLocal(this.skin, data);
        if (ClientPlayNetworking.canSend(Net.SetTraits.TYPE)) {
            ClientPlayNetworking.send(new Net.SetTraits(this.skin, data));
        }
        onClose();
    }

    @Override
    public void removed() {
        if (!this.saved) {
            // cancelado: vuelve lo que habia
            ClientTraits.draftSkin = null;
            ClientTraits.draft = null;
            ClientTraits.changed(this.skin);
            if (this.original == null) {
                ClientTraits.put(this.skin, null);
            }
        }
        super.removed();
    }

    /** Marca solo lo que suele ser pelo: lo que tiene color en los costados y atras del gorro, y arriba de la espalda. */
    private void auto() {
        NativeImage img = ClientTraits.read(this.skinId);
        if (img == null) {
            return;
        }
        try (img) {
            for (SkinLayout.Face f : SkinLayout.FACES) {
                if (!f.outer() || f.side() == 1) {
                    continue; // solo capas de afuera, no la cara
                }
                int rows = f.part() == SkinLayout.Part.HEAD ? f.height() : 7;
                for (int c = 0; c < f.width(); c++) {
                    for (int r = 0; r < rows; r++) {
                        int u = f.u0() + c, v = f.v0() + r;
                        boolean head = f.part() == SkinLayout.Part.HEAD;
                        if ((img.getPixel(u, v) >>> 24) > 40 && (!head || r >= 2) && (head || f.side() == 3)) {
                            this.work.hair(u, v, true);
                        }
                    }
                }
            }
        }
        ClientTraits.changed(this.skin);
    }

    // ---------------------------------------------------------------- pintar

    private int[] pixelAt(double mx, double my) {
        int s = cell();
        int u = (int) Math.floor((mx - gx()) / s), v = (int) Math.floor((my - gy()) / s);
        return u >= 0 && v >= 0 && u < 64 && v < 64 ? new int[]{u, v} : null;
    }

    private void paint(double mx, double my) {
        int[] p = pixelAt(mx, my);
        if (p == null) {
            return;
        }
        boolean changed = false;
        for (int du = 0; du < this.brush; du++) {
            for (int dv = 0; dv < this.brush; dv++) {
                int u = p[0] + du, v = p[1] + dv;
                if (SkinLayout.at(u, v) == null) {
                    continue; // solo los costados de la cabeza y el torso
                }
                boolean want = this.dragging == 0;
                if (this.work.hair(u, v) != want) {
                    this.work.hair(u, v, want);
                    changed = true;
                }
            }
        }
        if (changed) {
            ClientTraits.changed(this.skin);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (pixelAt(e.x(), e.y()) != null) {
            // clic izquierdo pinta (o borra, con la goma); el derecho siempre borra
            this.dragging = e.button() == 1 || this.erase ? 1 : 0;
            paint(e.x(), e.y());
            return true;
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (this.dragging >= 0) {
            paint(e.x(), e.y());
            return true;
        }
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        this.dragging = -1;
        return super.mouseReleased(e);
    }

    // ---------------------------------------------------------------- dibujo

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        int px = px(), py = py(), pw = pw(), ph = ph(), s = cell(), gx = gx(), gy = gy(), size = 64 * s;
        UI.panel(g, px, py, pw, ph);
        g.drawString(this.font, "✦ StarBody · Pelo con físicas", px + 10, py + 9, UI.ACCENT, false);
        String hint = "Pintá el pelo que querés que se mueva";
        g.drawString(this.font, hint, px + pw - 10 - this.font.width(hint), py + 9, UI.MUTED, false);
        // fondo a cuadros (para ver lo transparente), la skin y lo que no se puede marcar, oscuro
        for (int by = 0; by < 16; by++) {
            for (int bx = 0; bx < 16; bx++) {
                g.fill(gx + bx * 4 * s, gy + by * 4 * s, gx + (bx + 1) * 4 * s, gy + (by + 1) * 4 * s, (bx + by) % 2 == 0 ? 0xFF2A2533 : 0xFF221E2A);
            }
        }
        g.blit(RenderPipelines.GUI_TEXTURED, this.skinId, gx, gy, 0, 0, size, size, 64, 64, 64, 64);
        for (int by = 0; by < 16; by++) {
            for (int bx = 0; bx < 16; bx++) {
                if (SkinLayout.at(bx * 4 + 1, by * 4 + 1) == null) {
                    g.fill(gx + bx * 4 * s, gy + by * 4 * s, gx + (bx + 1) * 4 * s, gy + (by + 1) * 4 * s, 0xB0100C16);
                }
            }
        }
        for (SkinLayout.Face f : SkinLayout.FACES) {
            g.renderOutline(gx + f.u0() * s, gy + f.v0() * s, f.width() * s, f.height() * s, 0x553D3156);
        }
        // lo marcado
        for (int v = 0; v < 64; v++) {
            for (int u = 0; u < 64; u++) {
                if (this.work.hair(u, v)) {
                    g.fill(gx + u * s, gy + v * s, gx + (u + 1) * s, gy + (v + 1) * s, 0x88C58BFF);
                }
            }
        }
        UI.box(g, gx - 1, gy - 1, size + 2, size + 2, 0x00000000, UI.BORDER);
        // el pixel bajo el mouse y en que parte esta
        int[] p = pixelAt(mx, my);
        if (p != null) {
            SkinLayout.Face f = SkinLayout.at(p[0], p[1]);
            int c = f == null ? 0x80FF6B7A : this.erase ? 0xFFFF6B7A : 0xFFFFC66B;
            g.renderOutline(gx + p[0] * s, gy + p[1] * s, s * this.brush, s * this.brush, c);
            String label = f == null ? "Acá no (solo costados de cabeza y torso)" : f.label();
            g.drawString(this.font, label, gx, gy + size + 4, f == null ? UI.MUTED : UI.TEXT, false);
        } else {
            g.drawString(this.font, "Izquierdo: pintar · Derecho: borrar", gx, gy + size + 4, UI.MUTED, false);
        }
        // columna derecha: el personaje y la info
        int right = px + pw - 10, cx = right - 80;
        int top = py + 76, bottom = py + ph - 104;
        UI.box(g, right - 160, top, 160, bottom - top, 0x60100C18, UI.BORDER);
        if (Minecraft.getInstance().player != null && bottom - top > 40) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, right - 159, top + 1, right - 1, bottom - 1, Math.max(20, (bottom - top) / 3), 0.0625f,
                    mx, my, Minecraft.getInstance().player);
        }
        g.drawString(this.font, "Movimiento", right - 160, py + ph - 110, UI.MUTED, false);
        HairMesh m = ClientTraits.mesh(this.skin);
        String count = m == null ? "Nada marcado" : m.pixels + " píxeles · " + m.strips.size() + " tiras";
        g.drawString(this.font, count, cx - this.font.width(count) / 2, top - 10, UI.MUTED, false);
        super.render(g, mx, my, partial);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
