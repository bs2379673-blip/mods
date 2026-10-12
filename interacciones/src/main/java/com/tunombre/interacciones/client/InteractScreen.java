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

    /** si ibas agachado al pulsar G (al abrirse el menu el personaje se levanta, asi que se mira antes) */
    private final boolean crouched;

    public InteractScreen(LivingEntity target, boolean crouched) {
        super(Component.literal("Interactuar"));
        this.target = target;
        this.crouched = crouched;
    }

    private int boxTop, boxBottom;

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        boolean player = target instanceof net.minecraft.world.entity.player.Player;
        java.util.List<Integer> acts = new java.util.ArrayList<>();
        for (int a : Act.MENU) if (player || !Act.playerOnly(a)) acts.add(a);
        // por la espalda: no se piden, se hacen (en rojo)
        java.util.List<Integer> sneaky = new java.util.ArrayList<>();
        if (mc.player != null && Sessions.behind(mc.player, target)) {
            if (crouched && Sessions.isBlade(mc.player.getMainHandItem())) sneaky.add(Act.EXECUTE);
            if (player && isCuffs(mc.player.getMainHandItem())) sneaky.add(Act.ARREST);
            if (mc.player.getMainHandItem().isEmpty()) sneaky.add(Act.CHOKE);
            sneaky.add(Act.SCARE);
        }
        // dos columnas
        int bw = 112, bh = height < 300 ? 13 : 16, gap = height < 300 ? 1 : 2, rows = (acts.size() + 1) / 2 + (sneaky.size() + 1) / 2;
        int total = rows * (bh + gap) - gap + (sneaky.isEmpty() ? 0 : 6);
        int cx = width / 2, y0 = height / 2 - total / 2 + 6;
        boxTop = y0 - 18;
        boxBottom = y0 + total + 6;
        for (int i = 0; i < acts.size(); i++) {
            int a = acts.get(i);
            int x = i % 2 == 0 ? cx - bw - 2 : cx + 2, y = y0 + (i / 2) * (bh + gap);
            addRenderableWidget(Button.builder(Component.literal(Act.NAME[a]), b -> send(a)).bounds(x, y, bw, bh).build());
        }
        int sy = y0 + ((acts.size() + 1) / 2) * (bh + gap) + 6;
        for (int i = 0; i < sneaky.size(); i++) {
            int a = sneaky.get(i);
            int x = i % 2 == 0 ? cx - bw - 2 : cx + 2, y = sy + (i / 2) * (bh + gap);
            addRenderableWidget(Button.builder(Component.literal("\u00a7c" + Act.NAME[a]), b -> send(a)).bounds(x, y, bw, bh).build());
        }
    }

    /** esposas, grilletes o cinta de Cuffed (el servidor lo vuelve a comprobar con su API) */
    private static boolean isCuffs(net.minecraft.world.item.ItemStack s) {
        if (s.isEmpty()) return false;
        var id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem());
        if (id == null || !id.getNamespace().equals("cuffed")) return false;
        String n = id.getPath();
        return n.contains("cuff") || n.contains("shackle") || n.contains("tape");
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
