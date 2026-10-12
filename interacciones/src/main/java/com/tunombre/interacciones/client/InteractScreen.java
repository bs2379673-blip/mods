package com.tunombre.interacciones.client;

import com.tunombre.interacciones.Act;
import com.tunombre.interacciones.Net;
import com.tunombre.interacciones.Sessions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

/** menu de interaccion con el jugador (o NPC) que miras */
public class InteractScreen extends Screen {
    private final LivingEntity target;

    public InteractScreen(LivingEntity target) {
        super(Component.literal("Interactuar"));
        this.target = target;
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        int cx = width / 2, y = height / 2 - 52;
        int[] acts = {Act.HIGH_FIVE, Act.FIST_BUMP, Act.CARRY, Act.TRADE};
        for (int a : acts) {
            addRenderableWidget(Button.builder(Component.literal(Act.NAME[a]), b -> send(a)).bounds(cx - 70, y, 140, 20).build());
            y += 23;
        }
        boolean canExec = mc.player != null && mc.player.isShiftKeyDown() && Sessions.isBlade(mc.player.getMainHandItem()) && Sessions.behind(mc.player, target);
        if (canExec) addRenderableWidget(Button.builder(Component.literal("§c" + Act.NAME[Act.EXECUTE]), b -> send(Act.EXECUTE)).bounds(cx - 70, y, 140, 20).build());
    }

    private void send(int a) {
        Net.CH.sendToServer(new Net.Request(a, target.getId()));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(width / 2 - 80, height / 2 - 74, width / 2 + 80, height / 2 + 72, 0xB0101014);
        g.drawCenteredString(font, target.getDisplayName().getString(), width / 2, height / 2 - 68, 0xFF58C8FF);
        super.render(g, mx, my, pt);
    }

    @Override public boolean isPauseScreen() { return false; }
}
