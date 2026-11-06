package com.starlevi.starscenes.client;

import com.starlevi.starscenes.entity.NpcEntity;
import com.starlevi.starscenes.net.Net;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Menu para editar un NPC (agachado + clic derecho, o /starscenes menu). */
public class NpcScreen extends Screen {
    private static final float[] STEPS = {0.05f, 0.1f, 0.5f, 1f};
    private final int entityId;
    private int step = 1;
    private EditBox name;

    public NpcScreen(int entityId) {
        super(Component.literal("NPC"));
        this.entityId = entityId;
    }

    private NpcEntity npc() {
        if (Minecraft.getInstance().level == null) {
            return null;
        }
        Entity e = Minecraft.getInstance().level.getEntity(this.entityId);
        return e instanceof NpcEntity n ? n : null;
    }

    private void send(String action, String value) {
        ClientPlayNetworking.send(new Net.Edit(this.entityId, action, value));
    }

    @Override
    protected void init() {
        NpcEntity n = npc();
        if (n == null) {
            onClose();
            return;
        }
        int x = this.width / 2 - 155, y = 34;
        // modelo y skin: anterior / siguiente
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> cycle("modelo", ClientScenes.modelIds(), -1)).bounds(x + 210, y, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> cycle("modelo", ClientScenes.modelIds(), 1)).bounds(x + 290, y, 20, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.literal("◀"), b -> cycle("skin", skins(), -1)).bounds(x + 210, y, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("▶"), b -> cycle("skin", skins(), 1)).bounds(x + 290, y, 20, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.literal("-"), b -> send("estilo", String.valueOf(Math.max(0, npcOr().style() - 1)))).bounds(x + 210, y, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> send("estilo", String.valueOf(npcOr().style() + 1))).bounds(x + 290, y, 20, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.literal("-"), b -> send("escala", String.valueOf(Math.max(0.1f, npcOr().scale() - 0.1f)))).bounds(x + 210, y, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> send("escala", String.valueOf(npcOr().scale() + 0.1f))).bounds(x + 290, y, 20, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.literal("Mira / no mira"), b -> send("mirar", String.valueOf(!npcOr().look()))).bounds(x + 210, y, 100, 20).build());
        y += 28;
        // nombre
        this.name = new EditBox(this.font, x, y, 200, 20, Component.literal("Nombre"));
        this.name.setMaxLength(120);
        this.name.setValue(n.getCustomName() == null ? "" : n.getCustomName().getString().replace('§', '&'));
        addRenderableWidget(this.name);
        addRenderableWidget(Button.builder(Component.literal("Poner nombre"), b -> send("nombre", this.name.getValue().isBlank() ? "-" : this.name.getValue()))
                .bounds(x + 210, y, 100, 20).build());
        y += 30;
        // mover y girar
        String[][] dirs = {{"adelante", "Adelante"}, {"atras", "Atrás"}, {"izquierda", "Izquierda"}, {"derecha", "Derecha"}, {"arriba", "Arriba"}, {"abajo", "Abajo"}};
        for (int i = 0; i < dirs.length; i++) {
            String d = dirs[i][0];
            addRenderableWidget(Button.builder(Component.literal(dirs[i][1]), b -> send("mover", d + " " + STEPS[this.step]))
                    .bounds(x + (i % 3) * 70, y + (i / 3) * 22, 68, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Paso"), b -> this.step = (this.step + 1) % STEPS.length).bounds(x + 210, y, 100, 20).build());
        y += 46;
        addRenderableWidget(Button.builder(Component.literal("↺ 15°"), b -> send("girar", "-15")).bounds(x, y, 68, 20).build());
        addRenderableWidget(Button.builder(Component.literal("↻ 15°"), b -> send("girar", "15")).bounds(x + 70, y, 68, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Que me mire"), b -> send("mirarme", "")).bounds(x + 140, y, 100, 20).build());
        y += 28;
        // items: lo que tengas en la mano
        addRenderableWidget(Button.builder(Component.literal("Ítem en su mano"), b -> send("item", "mano")).bounds(x, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("En la otra mano"), b -> send("item", "otra_mano")).bounds(x + 105, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("En la cabeza"), b -> send("item", "cabeza")).bounds(x + 210, y, 100, 20).build());
        y += 30;
        addRenderableWidget(Button.builder(Component.literal("§cQuitar NPC"), b -> {
            send("quitar", "");
            onClose();
        }).bounds(x, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Listo"), b -> onClose()).bounds(x + 210, y, 100, 20).build());
    }

    private NpcEntity npcOr() {
        NpcEntity n = npc();
        if (n == null) {
            onClose();
            throw new IllegalStateException("el NPC ya no esta");
        }
        return n;
    }

    private static List<String> skins() {
        List<String> l = new ArrayList<>();
        l.add("-");
        l.addAll(ClientScenes.skinIds());
        return l;
    }

    private void cycle(String what, List<String> options, int dir) {
        NpcEntity n = npc();
        if (n == null || options.isEmpty()) {
            return;
        }
        String cur = what.equals("modelo") ? n.model() : n.skin().isEmpty() ? "-" : n.skin();
        int i = options.indexOf(cur);
        i = Math.floorMod(i + dir, options.size());
        send(what, options.get(i));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        NpcEntity n = npc();
        if (n == null) {
            return;
        }
        int x = this.width / 2 - 155, y = 34;
        g.drawCenteredString(this.font, "✦ Editar NPC ✦", this.width / 2, 12, 0xFFE9B8FF);
        g.drawString(this.font, "Modelo: §f" + n.model(), x, y + 6, 0xFFB0B0B0);
        g.drawCenteredString(this.font, n.model(), x + 260, y + 6, 0xFFFFFFFF);
        y += 24;
        g.drawString(this.font, "Skin: §f" + (n.skin().isEmpty() ? "la del modelo" : n.skin()), x, y + 6, 0xFFB0B0B0);
        y += 24;
        g.drawString(this.font, "Estilo de animaciones: §f" + (n.style() == 0 ? "al azar" : n.style()), x, y + 6, 0xFFB0B0B0);
        g.drawCenteredString(this.font, String.valueOf(n.style()), x + 260, y + 6, 0xFFFFFFFF);
        y += 24;
        g.drawString(this.font, String.format(Locale.ROOT, "Tamaño: §f%.1f", n.scale()), x, y + 6, 0xFFB0B0B0);
        g.drawCenteredString(this.font, String.format(Locale.ROOT, "%.1f", n.scale()), x + 260, y + 6, 0xFFFFFFFF);
        y += 24;
        g.drawString(this.font, "Mira a los jugadores: §f" + (n.look() ? "sí" : "no"), x, y + 6, 0xFFB0B0B0);
        y += 58;
        g.drawString(this.font, "Paso: " + STEPS[this.step] + " bloques", x + 215, y + 28, 0xFF8A8098);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
