package com.starlevi.starspirits.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.starlevi.starspirits.SpiritDef;
import com.starlevi.starspirits.net.Net;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Menu de la varita: tus espiritus (solo los tuyos) con su modelito girando; elegis cuales te siguen
 * (hasta 2) y en que area de tu casa vive cada uno; abajo, tus areas y crear una con lo marcado.
 */
public class SpiritMenuScreen extends Screen {
    private static final UUID NONE = new UUID(0, 0);
    private static final int ROW = 28, ROWS = 6, W = 340;
    private static Net.Menu data;
    private int scroll;
    private EditBox name;
    private final SpiritRig.Pose pose = new SpiritRig.Pose();

    public SpiritMenuScreen() {
        super(Component.literal("Tus espíritus"));
    }

    /** Llego el menu del servidor: se abre o se actualiza. */
    public static void receive(Net.Menu m) {
        data = m;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.screen instanceof SpiritMenuScreen s) {
            s.rebuildWidgets();
        } else if (m.open()) {
            mc.setScreen(new SpiritMenuScreen());
        }
    }

    private static void send(int kind, UUID spirit, UUID area, String name) {
        PacketDistributor.sendToServer(new Net.MenuAction(kind, spirit, area, name == null ? "" : name));
    }

    private int left() {
        return (this.width - W) / 2;
    }

    private int top() {
        return Math.max(8, (this.height - (ROWS * ROW + 110)) / 2);
    }

    @Override
    protected void init() {
        if (data == null) {
            return;
        }
        String keep = this.name == null ? "" : this.name.getValue();
        int x = left(), y = top() + 22;
        List<Net.MenuSpirit> list = data.spirits();
        this.scroll = Mth.clamp(this.scroll, 0, Math.max(0, list.size() - ROWS));
        for (int i = 0; i < ROWS && i + this.scroll < list.size(); i++) {
            Net.MenuSpirit s = list.get(i + this.scroll);
            int ry = y + i * ROW;
            addRenderableWidget(Button.builder(Component.literal(s.follows() ? "♥ Te sigue" : "Seguirme"),
                    b -> send(Net.MenuAction.FOLLOW, s.id(), NONE, null)).bounds(x + 150, ry + 4, 78, 20).build());
            addRenderableWidget(Button.builder(Component.literal("🏠 " + areaName(s.area())),
                    b -> send(Net.MenuAction.ASSIGN, s.id(), nextArea(s.area()), null)).bounds(x + 232, ry + 4, 104, 20).build());
        }
        // areas
        int ay = y + ROWS * ROW + 10;
        int ax = x;
        for (Net.MenuArea a : data.areas()) {
            if (ax + 110 > x + W) {
                break;
            }
            addRenderableWidget(Button.builder(Component.literal("✕ " + a.name()), b -> send(Net.MenuAction.DELETE, NONE, a.id(), null))
                    .bounds(ax, ay + 14, 106, 18).tooltip(net.minecraft.client.gui.components.Tooltip.create(
                            Component.literal("Borrar esta área (" + a.size() + ")"))).build());
            ax += 110;
        }
        this.name = new EditBox(this.font, x, ay + 48, 150, 18, Component.literal("Nombre"));
        this.name.setMaxLength(24);
        this.name.setHint(Component.literal("Nombre del área (opcional)"));
        this.name.setValue(keep);
        addRenderableWidget(this.name);
        addRenderableWidget(Button.builder(Component.literal(data.selection().isEmpty() ? "Marcá 2 puntas con la varita" : "Crear área " + data.selection()),
                b -> send(Net.MenuAction.CREATE, NONE, NONE, this.name.getValue())).bounds(x + 156, ay + 47, 180, 20).build()).active = !data.selection().isEmpty();
    }

    private String areaName(UUID id) {
        if (id == null || id.equals(NONE)) {
            return "Sin casita";
        }
        for (Net.MenuArea a : data.areas()) {
            if (a.id().equals(id)) {
                return a.name();
            }
        }
        return "Sin casita";
    }

    /** Ninguna -> area 1 -> area 2 -> ... -> ninguna. */
    private UUID nextArea(UUID cur) {
        List<UUID> ids = new ArrayList<>();
        ids.add(NONE);
        for (Net.MenuArea a : data.areas()) {
            ids.add(a.id());
        }
        int i = ids.indexOf(cur == null ? NONE : cur);
        return ids.get((i + 1) % ids.size());
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        int before = this.scroll;
        this.scroll -= (int) Math.signum(dy);
        if (before != this.scroll) {
            rebuildWidgets();
        }
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        if (data == null) {
            return;
        }
        int x = left(), y = top();
        g.drawCenteredString(this.font, Component.literal("✦ Tus espíritus ✦"), this.width / 2, y + 4, 0xFFE9B8FF);
        long follows = data.spirits().stream().filter(Net.MenuSpirit::follows).count();
        g.drawString(this.font, Component.literal("Te siguen " + follows + "/" + data.maxFollow()), x + W - 70, y + 6, 0xFFB0B0C0);
        y += 22;
        List<Net.MenuSpirit> list = data.spirits();
        if (list.isEmpty()) {
            g.drawCenteredString(this.font, Component.literal("Todavía no tenés espíritus: atrapá uno y llevalo a un Pilar Rúnico"),
                    this.width / 2, y + 30, 0xFFC0C0C0);
        }
        float now = (net.minecraft.client.Minecraft.getInstance().level == null ? 0 : net.minecraft.client.Minecraft.getInstance().level.getGameTime()) + partial;
        for (int i = 0; i < ROWS && i + this.scroll < list.size(); i++) {
            Net.MenuSpirit s = list.get(i + this.scroll);
            int ry = y + i * ROW;
            g.fill(x, ry + 1, x + W, ry + ROW - 1, 0x55201830);
            SpiritDef d = ClientDefs.def(s.type());
            int color = d == null ? 0xFFFFFFFF : 0xFF000000 | d.rarity.color;
            drawSpirit(g, s.type(), x + 16, ry + ROW - 3, now + i * 37);
            g.drawString(this.font, d == null ? s.type() : d.name, x + 34, ry + 6, color);
            g.drawString(this.font, d == null ? "" : d.rarity.label, x + 34, ry + 16, 0xFF9A90A8);
        }
        if (list.size() > ROWS) {
            g.drawString(this.font, (this.scroll + 1) + "-" + Math.min(list.size(), this.scroll + ROWS) + " de " + list.size() + " (rueda)",
                    x, y + ROWS * ROW + 1, 0xFF8A8098);
        }
        int ay = y + ROWS * ROW + 10;
        g.drawString(this.font, data.areas().isEmpty() ? "Tus áreas: ninguna todavía" : "Tus áreas (clic para borrar):", x, ay + 2, 0xFFCFE8D0);
        g.drawString(this.font, "Nueva área:", x, ay + 38, 0xFFCFE8D0);
    }

    private void drawSpirit(GuiGraphics g, String type, int x, int y, float t) {
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(x, y, 150);
        ps.scale(20, -20, 20);
        ps.mulPose(Axis.XP.rotationDegrees(-12));
        Lighting.setupForEntityInInventory();
        this.pose.walk = 0;
        this.pose.walkAmount = 0;
        this.pose.age = t;
        this.pose.headYaw = 0;
        this.pose.headPitch = 0;
        this.pose.happy = -1;
        this.pose.sleeping = false;
        this.pose.seed = type.hashCode();
        try {
            SpiritDraw.draw(type, ps, g.bufferSource(), LightTexture.FULL_BRIGHT, this.pose, Mth.sin(t * 0.03f) * 35 + 20, 1f);
            g.flush();
        } catch (Exception ignored) {
            // modelo que todavia no llego
        }
        Lighting.setupFor3DItems();
        ps.popPose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
