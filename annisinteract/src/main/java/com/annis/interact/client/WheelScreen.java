package com.annis.interact.client;

import com.annis.interact.Interaction;
import com.annis.interact.net.RequestPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.util.InputUtil;

import java.util.List;

/** Rueda de interacciones: se abre manteniendo G mirando a alguien y se elige al soltar. */
public class WheelScreen extends Screen {
    private final LivingEntity target;
    private final List<Interaction> all;
    private List<Interaction> options;
    private int page;
    private int pages;
    private int hovered = -1;
    private boolean sent;

    public WheelScreen(LivingEntity target, List<Interaction> options) {
        super(Text.literal("Interacciones"));
        this.target = target;
        this.all = options;
        int max = 0;
        for (Interaction i : options) {
            max = Math.max(max, i.page());
        }
        this.pages = max + 1;
        setPage(0);
    }

    private void setPage(int p) {
        this.page = ((p % this.pages) + this.pages) % this.pages;
        this.options = new java.util.ArrayList<>();
        for (Interaction i : this.all) {
            if (i.page() == this.page) {
                this.options.add(i);
            }
        }
    }

    private static final String[] PAGE_NAMES = {"Juntos", "Especial", "Bailes", "Mias"};

    private boolean dancePage() {
        for (Interaction i : this.options) {
            if (i.dance()) {
                return true;
            }
        }
        return false;
    }

    private static String roleText() {
        return ClientState.followRole ? "Mi parte: femenina (sigo)" : "Mi parte: masculina (guio)";
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_R) {
            ClientState.followRole = !ClientState.followRole;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double h, double v) {
        if (this.pages > 1 && v != 0) {
            setPage(this.page + (v < 0 ? 1 : -1));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, h, v);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // sin fondo oscuro: se sigue viendo a la otra persona
    }

    private int radius() {
        return Math.min(105, Math.min(this.width, this.height) / 2 - 30);
    }

    private int pick(double mx, double my) {
        double dx = mx - this.width / 2.0, dy = my - this.height / 2.0;
        if (dx * dx + dy * dy < 22 * 22 || this.options.isEmpty()) {
            return -1;
        }
        double ang = Math.atan2(dx, -dy);
        if (ang < 0) {
            ang += Math.PI * 2;
        }
        double step = Math.PI * 2 / this.options.size();
        return (int) Math.floor((ang + step / 2) / step) % this.options.size();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        this.hovered = pick(mouseX, mouseY);
        int cx = this.width / 2, cy = this.height / 2;
        int r = radius();
        int n = this.options.size();
        // centro: con quien
        String who = this.target.getDisplayName().getString();
        ctx.fill(cx - 46, cy - 14, cx + 46, cy + 14, 0xA0101018);
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(who), cx, cy - 9, 0xFFE8D0FF);
        String sub = this.pages > 1 ? "< " + PAGE_NAMES[Math.min(this.page, PAGE_NAMES.length - 1)] + " >" : "suelta G";
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(sub), cx, cy + 3, 0xFF9A9AB0);
        if (this.pages > 1) {
            ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("rueda del raton o clic derecho: cambiar"), cx, this.height - 20, 0xFF9A9AB0);
        }
        if (dancePage()) {
            ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(roleText() + "   (R o clic central: cambiar)"),
                    cx, this.height - 34, 0xFFFFC8E8);
        }
        for (int i = 0; i < n; i++) {
            double ang = Math.PI * 2 * i / n;
            int x = cx + (int) Math.round(Math.sin(ang) * r);
            int y = cy - (int) Math.round(Math.cos(ang) * r);
            String label = this.options.get(i).name();
            int w = this.textRenderer.getWidth(label) / 2 + 6;
            boolean on = i == this.hovered;
            ctx.fill(x - w, y - 9, x + w, y + 9, on ? 0xE0B04A8A : 0xB0151520);
            if (on) {
                ctx.drawBorder(x - w, y - 9, w * 2, 18, 0xFFFFD0F0);
            }
            ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(label), x, y - 4, on ? 0xFFFFFFFF : 0xFFD8D8E8);
        }
        // linea del centro al elegido
        if (this.hovered >= 0) {
            double ang = Math.PI * 2 * this.hovered / n;
            for (int k = 24; k < r - 14; k += 3) {
                int x = cx + (int) Math.round(Math.sin(ang) * k);
                int y = cy - (int) Math.round(Math.cos(ang) * k);
                ctx.fill(x - 1, y - 1, x + 1, y + 1, 0xC0FFD0F0);
            }
        }
        if (this.target.isRemoved() || this.client != null && this.client.player != null
                && this.client.player.squaredDistanceTo(this.target) > 8 * 8) {
            this.close();
        }
    }

    private void choose(int i) {
        if (this.sent) {
            return;
        }
        this.sent = true;
        if (i >= 0 && i < this.options.size()) {
            Interaction it = this.options.get(i);
            String id = it.dance() && ClientState.followRole ? it.id() + Interaction.FOLLOW : it.id();
            ClientPlayNetworking.send(new RequestPayload(id, this.target.getId()));
        }
        this.close();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 2 && dancePage()) {
            ClientState.followRole = !ClientState.followRole;
            return true;
        }
        if (button == 1 && this.pages > 1) {
            setPage(this.page + 1);
            return true;
        }
        int i = pick(mouseX, mouseY);
        if (i >= 0) {
            choose(i);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (AnnisInteractClient.WHEEL.matchesKey(keyCode, scanCode)) {
            choose(this.hovered);
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    private int age;

    @Override
    public void tick() {
        super.tick();
        this.age++;
        // si se solto G antes de que se abriera la rueda (o fuera de la pantalla), elegir igual
        InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(AnnisInteractClient.WHEEL);
        if (this.age > 3 && this.client != null && key.getCategory() == InputUtil.Type.KEYSYM
                && !InputUtil.isKeyPressed(this.client.getWindow().getHandle(), key.getCode())) {
            choose(this.hovered);
        }
    }
}
