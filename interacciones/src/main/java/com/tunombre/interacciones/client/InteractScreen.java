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

    private int boxTop, boxBottom;

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        boolean player = target instanceof net.minecraft.world.entity.player.Player;
        java.util.List<Integer> acts = new java.util.ArrayList<>();
        for (int a : Act.MENU) if (player || !Act.playerOnly(a)) acts.add(a);
        boolean canExec = mc.player != null && mc.player.isShiftKeyDown() && Sessions.isBlade(mc.player.getMainHandItem()) && Sessions.behind(mc.player, target);
        // dos columnas
        int bw = 112, bh = 18, gap = 3, rows = (acts.size() + 1) / 2 + (canExec ? 1 : 0);
        int total = rows * (bh + gap) - gap;
        int cx = width / 2, y0 = height / 2 - total / 2 + 6;
        boxTop = y0 - 18;
        boxBottom = y0 + total + 6;
        for (int i = 0; i < acts.size(); i++) {
            int a = acts.get(i);
            int x = i % 2 == 0 ? cx - bw - 2 : cx + 2, y = y0 + (i / 2) * (bh + gap);
            addRenderableWidget(Button.builder(Component.literal(Act.NAME[a]), b -> send(a)).bounds(x, y, bw, bh).build());
        }
        if (canExec)
            addRenderableWidget(Button.builder(Component.literal("§c" + Act.NAME[Act.EXECUTE]), b -> send(Act.EXECUTE))
                    .bounds(cx - bw - 2, y0 + ((acts.size() + 1) / 2) * (bh + gap), bw * 2 + 4, bh).build());
    }

    private void send(int a) {
        Net.CH.sendToServer(new Net.Request(a, target.getId()));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(width / 2 - 122, boxTop, width / 2 + 122, boxBottom, 0xB0101014);
        g.fill(width / 2 - 122, boxTop, width / 2 + 122, boxTop + 1, 0xFF58C8FF);
        g.drawCenteredString(font, target.getDisplayName().getString(), width / 2, boxTop + 5, 0xFF58C8FF);
        super.render(g, mx, my, pt);
    }

    @Override public boolean isPauseScreen() { return false; }
}
