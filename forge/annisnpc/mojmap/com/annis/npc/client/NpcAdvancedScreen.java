package com.annis.npc.client;

import com.annis.npc.entity.AnnisNpcEntity;
import com.annis.npc.net.NpcActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Opciones avanzadas del NPC: comportamiento, dialogo, avatar de Figura, equipo y rutina. */
public class NpcAdvancedScreen extends Screen {
    private static final String[] MODES = {"Pasivo", "Seguirme", "Guardia (ataca monstruos)"};
    private static final int[] SECONDS = {3, 5, 10, 20, 30, 60, 120, 300};

    private final int npcId;
    private final List<String> activities = new ArrayList<>();
    private int activityIndex = 3; // pescar
    private int secondsIndex = 2;
    private int figuraCycle = -1;
    private boolean loaded;
    private int age;

    private EditBox dialogField;
    private EditBox figuraField;
    private Button modeButton;
    private Button routineToggle;
    private Button activityButton;
    private Button secondsButton;
    private Button mcaButton;
    private long mcaArmedAt;

    public NpcAdvancedScreen(int npcId) {
        super(Component.literal("Opciones avanzadas"));
        this.npcId = npcId;
        this.activities.add("");
        this.activities.addAll(Activities.names());
    }

    private AnnisNpcEntity npc() {
        if (Minecraft.getInstance().level == null) {
            return null;
        }
        Entity e = Minecraft.getInstance().level.getEntity(this.npcId);
        return e instanceof AnnisNpcEntity n && !n.isRemoved() ? n : null;
    }

    private void send(int action, String text, boolean flag, int number) {
        ClientPlayNetworking.send(new NpcActionPayload(this.npcId, action, text, flag, number));
    }

    private void sendDialog() {
        send(NpcActionPayload.SET_DIALOG, this.dialogField.getValue(), false, 0);
    }

    private void sendFigura() {
        send(NpcActionPayload.SET_FIGURA, this.figuraField.getValue().trim(), false, 0);
        AnnisNpcEntity npc = npc();
        if (npc != null) {
            FiguraBridge.reload(npc); // "Aplicar" con el mismo nombre vuelve a cargar el avatar
        }
    }

    @Override
    protected void init() {
        int colW = Math.min(210, (this.width - 36) / 2);
        int lx = this.width / 2 - colW - 6;
        int rx = this.width / 2 + 6;

        // --- izquierda: comportamiento ---
        this.modeButton = Button.builder(Component.empty(), b -> {
            AnnisNpcEntity npc = npc();
            if (npc != null) {
                send(NpcActionPayload.SET_MODE, "", false, (npc.getMode() + 1) % MODES.length);
            }
        }).bounds(lx, 42, colW, 20).build();
        this.addRenderableWidget(this.modeButton);

        this.dialogField = new EditBox(this.font, lx, 80, colW, 18, Component.literal("Dialogo"));
        this.dialogField.setHint(Component.literal("Frases separadas por |"));
        this.dialogField.setMaxLength(1500);
        this.addRenderableWidget(this.dialogField);
        this.addRenderableWidget(Button.builder(Component.literal("Guardar dialogo"), b -> sendDialog())
                .bounds(lx, 100, colW, 20).build());

        this.figuraField = new EditBox(this.font, lx, 142, colW, 18, Component.literal("Avatar de Figura"));
        this.figuraField.setHint(Component.literal("Nombre del avatar (carpeta o .zip)"));
        this.figuraField.setMaxLength(64);
        this.addRenderableWidget(this.figuraField);
        int third = colW / 3;
        this.addRenderableWidget(Button.builder(Component.literal("Aplicar"), b -> sendFigura())
                .bounds(lx, 162, third - 2, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Siguiente"), b -> {
            List<String> all = FiguraBridge.list();
            if (!all.isEmpty()) {
                this.figuraCycle = (this.figuraCycle + 1) % all.size();
                this.figuraField.setValue(all.get(this.figuraCycle));
                sendFigura();
            }
        }).bounds(lx + third, 162, third - 2, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Quitar"), b -> {
            this.figuraField.setValue("");
            sendFigura();
        }).bounds(lx + third * 2, 162, colW - third * 2, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Vaciar equipo (te lo devuelve)"), b -> send(NpcActionPayload.CLEAR_EQUIP, "", false, 0))
                .bounds(lx, 196, colW, 20).build());

        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("mca")) {
            this.mcaButton = Button.builder(Component.empty(), b -> {
                long now = System.currentTimeMillis();
                if (now - this.mcaArmedAt < 4000) {
                    send(NpcActionPayload.CONVERT_MCA, "", false, 0);
                    Minecraft.getInstance().setScreen(null);
                } else {
                    this.mcaArmedAt = now;
                }
            }).bounds(lx, 244, colW, 20).build();
            this.addRenderableWidget(this.mcaButton);
        }

        // --- derecha: rutina ---
        this.routineToggle = Button.builder(Component.empty(), b -> {
            AnnisNpcEntity npc = npc();
            if (npc != null) {
                send(NpcActionPayload.ROUTINE_ENABLE, "", !npc.isRoutineOn(), 0);
            }
        }).bounds(rx, 42, colW, 20).build();
        this.addRenderableWidget(this.routineToggle);

        this.activityButton = Button.builder(Component.empty(), b ->
                this.activityIndex = (this.activityIndex + 1) % this.activities.size())
                .bounds(rx, 66, colW, 20).build();
        this.addRenderableWidget(this.activityButton);
        this.secondsButton = Button.builder(Component.empty(), b ->
                this.secondsIndex = (this.secondsIndex + 1) % SECONDS.length)
                .bounds(rx, 88, colW, 20).build();
        this.addRenderableWidget(this.secondsButton);

        this.addRenderableWidget(Button.builder(Component.literal("Anadir parada: donde esta el NPC"),
                b -> addStop(false)).bounds(rx, 112, colW, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Anadir parada: donde estoy yo"),
                b -> addStop(true)).bounds(rx, 134, colW, 20).build());
        int half = colW / 2;
        this.addRenderableWidget(Button.builder(Component.literal("Quitar ultima"),
                b -> send(NpcActionPayload.ROUTINE_REMOVE_LAST, "", false, 0)).bounds(rx, 156, half - 2, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Borrar todas"),
                b -> send(NpcActionPayload.ROUTINE_CLEAR, "", false, 0)).bounds(rx + half, 156, colW - half, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Listo"), b -> this.onClose())
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());
        this.loaded = false;
    }

    private void addStop(boolean atPlayer) {
        send(NpcActionPayload.ROUTINE_ADD, this.activities.get(this.activityIndex), atPlayer, SECONDS[this.secondsIndex]);
    }

    @Override
    public void tick() {
        this.age++;
        if (npc() == null && this.age > 60) {
            this.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        // volver al editor principal
        Minecraft.getInstance().setScreen(new NpcEditorScreen(this.npcId));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean enter = keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER;
        if (enter && this.dialogField.isFocused()) {
            sendDialog();
            return true;
        }
        if (enter && this.figuraField.isFocused()) {
            sendFigura();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        AnnisNpcEntity npc = npc();
        if (npc != null) {
            if (!this.loaded) {
                this.loaded = true;
                this.dialogField.setValue(npc.getDialog());
                this.figuraField.setValue(npc.getFigura());
            }
            this.modeButton.setMessage(Component.literal("Modo: " + MODES[Math.max(0, Math.min(2, npc.getMode()))]));
            this.routineToggle.setMessage(Component.literal("Rutina: " + (npc.isRoutineOn() ? "activada" : "apagada")));
        }
        if (this.mcaButton != null) {
            boolean armed = System.currentTimeMillis() - this.mcaArmedAt < 4000;
            this.mcaButton.setMessage(armed
                    ? Component.literal("¿Seguro? Pasa a ser aldeano de MCA").withStyle(ChatFormatting.RED)
                    : Component.literal("Convertir en persona de MCA").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        String act = this.activities.get(this.activityIndex);
        this.activityButton.setMessage(Component.literal("Actividad: " + (act.isEmpty() ? "solo caminar" : act)));
        this.secondsButton.setMessage(Component.literal("Duracion de la parada: " + SECONDS[this.secondsIndex] + " s"));

        super.render(context, mouseX, mouseY, delta);

        int colW = Math.min(210, (this.width - 36) / 2);
        int lx = this.width / 2 - colW - 6;
        int rx = this.width / 2 + 6;
        context.drawCenteredString(this.font, Component.literal("Opciones avanzadas"), this.width / 2, 10, 0xFFFFFF);
        context.drawString(this.font, Component.literal("Comportamiento").withStyle(ChatFormatting.GOLD), lx, 30, 0xFFFFFF);
        context.drawString(this.font, Component.literal("Dialogo (clic derecho al NPC)").withStyle(ChatFormatting.GOLD), lx, 68, 0xFFFFFF);
        String fig = FiguraBridge.available() ? "Avatar de Figura" : "Avatar de Figura (Figura no instalado)";
        context.drawString(this.font, Component.literal(fig).withStyle(ChatFormatting.GOLD), lx, 130, 0xFFFFFF);
        context.drawString(this.font, Component.literal("Equipo").withStyle(ChatFormatting.GOLD), lx, 184, 0xFFFFFF);
        context.drawString(this.font, Component.literal("Shift + clic derecho con un objeto en la mano").withStyle(ChatFormatting.GRAY), lx, 220, 0xFFFFFF);
        context.drawString(this.font, Component.literal("lo equipa (armadura, arma, escudo...)").withStyle(ChatFormatting.GRAY), lx, 231, 0xFFFFFF);

        context.drawString(this.font, Component.literal("Rutina (las paradas se repiten en bucle)").withStyle(ChatFormatting.GOLD), rx, 30, 0xFFFFFF);
        if (npc != null) {
            List<AnnisNpcEntity.Stop> stops = npc.getStops();
            int y = 182;
            if (stops.isEmpty()) {
                context.drawString(this.font, Component.literal("Sin paradas todavia.").withStyle(ChatFormatting.GRAY), rx, y, 0xFFFFFF);
            }
            for (int i = 0; i < stops.size() && y < this.height - 36; i++) {
                AnnisNpcEntity.Stop s = stops.get(i);
                String label = (i + 1) + ") " + (s.activity().isEmpty() ? "caminar" : s.activity()) + " " + s.seconds() + "s  ("
                        + Math.round(s.x()) + ", " + Math.round(s.y()) + ", " + Math.round(s.z()) + ")";
                context.drawString(this.font, Component.literal(label), rx, y, 0xDDDDDD);
                y += 10;
            }
        }
    }
}
