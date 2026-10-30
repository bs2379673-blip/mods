package com.annis.npc.client;

import com.annis.npc.entity.AnnisNpcEntity;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;

/** Conversacion con un NPC: texto que se escribe solo, avanza con clic, Espacio o Enter. */
public class NpcDialogScreen extends Screen {
    private final int npcId;
    private String[] lines = new String[0];
    private int index;
    private int age;
    private boolean loaded;
    private String speaker = "NPC";

    public NpcDialogScreen(int npcId) {
        super(Component.literal("Dialogo"));
        this.npcId = npcId;
    }

    private AnnisNpcEntity npc() {
        if (Minecraft.getInstance().level == null) {
            return null;
        }
        Entity e = Minecraft.getInstance().level.getEntity(this.npcId);
        return e instanceof AnnisNpcEntity n && !n.isRemoved() ? n : null;
    }

    @Override
    protected void init() {
        this.addRenderableWidget(Button.builder(Component.literal("Continuar"), b -> advance())
                .bounds(this.width - 108, this.height - 30, 90, 20).build());
    }

    private void load() {
        AnnisNpcEntity npc = npc();
        if (npc == null) {
            return;
        }
        String raw = npc.getDialog();
        this.lines = raw.isEmpty() ? new String[]{"..."} : raw.split("\\|");
        for (int i = 0; i < this.lines.length; i++) {
            this.lines[i] = this.lines[i].trim();
        }
        this.speaker = npc.hasCustomName() ? npc.getCustomName().getString() : "NPC";
        this.loaded = true;
    }

    private String current() {
        return this.index < this.lines.length ? this.lines[this.index] : "";
    }

    private void advance() {
        if (this.age * 2 < current().length()) {
            this.age = current().length(); // mostrar todo el texto
            return;
        }
        this.index++;
        this.age = 0;
        if (this.index >= this.lines.length) {
            this.onClose();
        }
    }

    @Override
    public void tick() {
        if (!this.loaded) {
            load();
        }
        this.age++;
        AnnisNpcEntity npc = npc();
        if (npc == null && this.loaded) {
            this.onClose();
        } else if (npc != null && Minecraft.getInstance().player != null
                && Minecraft.getInstance().player.distanceToSqr(npc) > 100.0) {
            this.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            advance();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        advance();
        return true;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        int boxH = 78;
        int top = this.height - boxH - 36;
        context.fill(16, top, this.width - 16, top + boxH, 0xC0101018);
        context.fill(16, top, this.width - 16, top + 1, 0xFFE0B040);
        context.drawString(this.font, Component.literal(this.speaker), 26, top + 7, 0xFFE0B040);

        String full = current();
        int shown = Math.min(full.length(), this.age * 2);
        List<FormattedCharSequence> wrapped = this.font.split(Component.literal(full.substring(0, shown)), this.width - 52);
        int y = top + 22;
        for (FormattedCharSequence line : wrapped) {
            context.drawString(this.font, line, 26, y, 0xFFFFFF);
            y += 11;
        }
        context.drawString(this.font,
                Component.literal((this.index + 1) + "/" + Math.max(1, this.lines.length)),
                this.width - 50, top + 7, 0x888888);
    }
}
