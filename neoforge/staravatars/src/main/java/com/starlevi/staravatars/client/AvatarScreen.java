package com.starlevi.staravatars.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.starlevi.staravatars.net.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Menu de personajes: la lista del servidor a la izquierda y el elegido girando en grande, caminando.
 * Se abre con la tecla J (configurable).
 */
public class AvatarScreen extends Screen {
    private static final int ROW = 22, LIST_W = 150;
    private int scroll;
    private String selected;
    private float spin;
    private PlayerModel<?> dummy;
    private AvatarLayer drawer;

    public AvatarScreen() {
        super(Component.literal("Personajes"));
    }

    /** Llego algo nuevo del servidor: si el menu esta abierto, se rearma. */
    public static void refresh() {
        if (Minecraft.getInstance().screen instanceof AvatarScreen s) {
            s.rebuildWidgets();
        }
    }

    @Override
    protected void init() {
        List<Net.Avatar> all = ClientAvatars.index();
        if (this.selected == null && Minecraft.getInstance().player != null) {
            this.selected = ClientAvatars.outfitId(Minecraft.getInstance().player.getUUID());
        }
        if (this.selected == null && !all.isEmpty()) {
            this.selected = all.get(0).id();
        }
        int rows = Math.max(1, (this.height - 80) / ROW);
        this.scroll = Mth.clamp(this.scroll, 0, Math.max(0, all.size() - rows));
        for (int i = 0; i < rows && i + this.scroll < all.size(); i++) {
            Net.Avatar a = all.get(i + this.scroll);
            String mark = a.id().equals(this.selected) ? "▶ " : "";
            addRenderableWidget(Button.builder(Component.literal(mark + a.name()), b -> {
                this.selected = a.id();
                rebuildWidgets();
            }).bounds(12, 36 + i * ROW, LIST_W, 20).build());
        }
        String mine = Minecraft.getInstance().player == null ? null : ClientAvatars.outfitId(Minecraft.getInstance().player.getUUID());
        Button use = Button.builder(Component.literal(this.selected != null && this.selected.equals(mine) ? "✔ Lo estás usando" : "Usar este personaje"),
                b -> PacketDistributor.sendToServer(new Net.Choose(this.selected))).bounds(this.width / 2 + 20, this.height - 34, 150, 20).build();
        use.active = this.selected != null && !this.selected.equals(mine);
        addRenderableWidget(use);
        Button off = Button.builder(Component.literal("Volver a mi skin"), b -> PacketDistributor.sendToServer(new Net.Choose("")))
                .bounds(this.width / 2 + 176, this.height - 34, 120, 20).build();
        off.active = mine != null;
        addRenderableWidget(off);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (mx < LIST_W + 20) {
            this.scroll -= (int) Math.signum(dy);
            rebuildWidgets();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (mx > LIST_W + 20) {
            this.spin += (float) dx * 1.5f;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        g.drawString(this.font, "✦ Personajes ✦", 12, 14, 0xFFE9B8FF);
        if (ClientAvatars.index().isEmpty()) {
            g.drawString(this.font, "El servidor no tiene personajes", 12, 40, 0xFFB0B0B0);
            g.drawString(this.font, "(config/staravatars/personajes)", 12, 52, 0xFF808080);
            return;
        }
        if (this.selected == null) {
            return;
        }
        int cx = (LIST_W + 20 + this.width) / 2, cy = this.height - 50;
        ClientAvatars.Ready r = ClientAvatars.get(this.selected);
        if (r == null) {
            g.drawCenteredString(this.font, "Cargando personaje…", cx, this.height / 2, 0xFFD0D0D0);
            return;
        }
        drawAvatar(g, r, cx, cy, Math.min(this.height - 90, 220) / 2f, partial);
        g.drawCenteredString(this.font, "Arrastrá para girarlo", cx, 22, 0xFF8A8098);
    }

    private void drawAvatar(GuiGraphics g, ClientAvatars.Ready r, int x, int y, float size, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (this.dummy == null) {
            this.dummy = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
            this.drawer = new AvatarLayer(null);
        }
        float t = (mc.player == null ? 0 : mc.player.tickCount) + partial;
        // caminata de Minecraft, para ver como se mueve
        ModelPart[] parts = AvatarLayer.parts(this.dummy);
        for (ModelPart p : parts) {
            p.resetPose();
        }
        float swing = Mth.cos(t * 0.33f) * 0.7f;
        this.dummy.rightArm.xRot = swing;
        this.dummy.leftArm.xRot = -swing;
        this.dummy.rightLeg.xRot = -swing;
        this.dummy.leftLeg.xRot = swing;
        this.dummy.head.yRot = Mth.sin(t * 0.03f) * 0.3f;
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(x, y, 200);
        ps.scale(size, size, -size);
        ps.mulPose(Axis.XP.rotationDegrees(-8));
        ps.mulPose(Axis.YP.rotationDegrees(200 + this.spin));
        // como lo pone el dibujo de entidades: dado vuelta y con los pies en el piso
        ps.scale(-1, -1, 1);
        ps.translate(0, -1.501f, 0);
        Lighting.setupForEntityInInventory();
        this.drawer.draw(ps, g.bufferSource(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, r, parts);
        g.flush();
        Lighting.setupFor3DItems();
        ps.popPose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
