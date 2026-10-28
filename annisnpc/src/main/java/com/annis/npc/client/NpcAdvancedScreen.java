package com.annis.npc.client;

import com.annis.npc.entity.AnnisNpcEntity;
import com.annis.npc.net.NpcActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
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

    private TextFieldWidget dialogField;
    private TextFieldWidget figuraField;
    private ButtonWidget modeButton;
    private ButtonWidget routineToggle;
    private ButtonWidget activityButton;
    private ButtonWidget secondsButton;
    private ButtonWidget mcaButton;
    private long mcaArmedAt;

    public NpcAdvancedScreen(int npcId) {
        super(Text.literal("Opciones avanzadas"));
        this.npcId = npcId;
        this.activities.add("");
        this.activities.addAll(Activities.names());
    }

    private AnnisNpcEntity npc() {
        if (MinecraftClient.getInstance().world == null) {
            return null;
        }
        Entity e = MinecraftClient.getInstance().world.getEntityById(this.npcId);
        return e instanceof AnnisNpcEntity n && !n.isRemoved() ? n : null;
    }

    private void send(int action, String text, boolean flag, int number) {
        ClientPlayNetworking.send(new NpcActionPayload(this.npcId, action, text, flag, number));
    }

    private void sendDialog() {
        send(NpcActionPayload.SET_DIALOG, this.dialogField.getText(), false, 0);
    }

    private void sendFigura() {
        send(NpcActionPayload.SET_FIGURA, this.figuraField.getText().trim(), false, 0);
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
        this.modeButton = ButtonWidget.builder(Text.empty(), b -> {
            AnnisNpcEntity npc = npc();
            if (npc != null) {
                send(NpcActionPayload.SET_MODE, "", false, (npc.getMode() + 1) % MODES.length);
            }
        }).dimensions(lx, 42, colW, 20).build();
        this.addDrawableChild(this.modeButton);

        this.dialogField = new TextFieldWidget(this.textRenderer, lx, 80, colW, 18, Text.literal("Dialogo"));
        this.dialogField.setPlaceholder(Text.literal("Frases separadas por |"));
        this.dialogField.setMaxLength(1500);
        this.addDrawableChild(this.dialogField);
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Guardar dialogo"), b -> sendDialog())
                .dimensions(lx, 100, colW, 20).build());

        this.figuraField = new TextFieldWidget(this.textRenderer, lx, 142, colW, 18, Text.literal("Avatar de Figura"));
        this.figuraField.setPlaceholder(Text.literal("Nombre del avatar (carpeta o .zip)"));
        this.figuraField.setMaxLength(64);
        this.addDrawableChild(this.figuraField);
        int third = colW / 3;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Aplicar"), b -> sendFigura())
                .dimensions(lx, 162, third - 2, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Siguiente"), b -> {
            List<String> all = FiguraBridge.list();
            if (!all.isEmpty()) {
                this.figuraCycle = (this.figuraCycle + 1) % all.size();
                this.figuraField.setText(all.get(this.figuraCycle));
                sendFigura();
            }
        }).dimensions(lx + third, 162, third - 2, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Quitar"), b -> {
            this.figuraField.setText("");
            sendFigura();
        }).dimensions(lx + third * 2, 162, colW - third * 2, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Vaciar equipo (te lo devuelve)"), b -> send(NpcActionPayload.CLEAR_EQUIP, "", false, 0))
                .dimensions(lx, 196, colW, 20).build());

        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("mca")) {
            this.mcaButton = ButtonWidget.builder(Text.empty(), b -> {
                long now = System.currentTimeMillis();
                if (now - this.mcaArmedAt < 4000) {
                    send(NpcActionPayload.CONVERT_MCA, "", false, 0);
                    MinecraftClient.getInstance().setScreen(null);
                } else {
                    this.mcaArmedAt = now;
                }
            }).dimensions(lx, 244, colW, 20).build();
            this.addDrawableChild(this.mcaButton);
        }

        // --- derecha: rutina ---
        this.routineToggle = ButtonWidget.builder(Text.empty(), b -> {
            AnnisNpcEntity npc = npc();
            if (npc != null) {
                send(NpcActionPayload.ROUTINE_ENABLE, "", !npc.isRoutineOn(), 0);
            }
        }).dimensions(rx, 42, colW, 20).build();
        this.addDrawableChild(this.routineToggle);

        this.activityButton = ButtonWidget.builder(Text.empty(), b ->
                this.activityIndex = (this.activityIndex + 1) % this.activities.size())
                .dimensions(rx, 66, colW, 20).build();
        this.addDrawableChild(this.activityButton);
        this.secondsButton = ButtonWidget.builder(Text.empty(), b ->
                this.secondsIndex = (this.secondsIndex + 1) % SECONDS.length)
                .dimensions(rx, 88, colW, 20).build();
        this.addDrawableChild(this.secondsButton);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Anadir parada: donde esta el NPC"),
                b -> addStop(false)).dimensions(rx, 112, colW, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Anadir parada: donde estoy yo"),
                b -> addStop(true)).dimensions(rx, 134, colW, 20).build());
        int half = colW / 2;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Quitar ultima"),
                b -> send(NpcActionPayload.ROUTINE_REMOVE_LAST, "", false, 0)).dimensions(rx, 156, half - 2, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Borrar todas"),
                b -> send(NpcActionPayload.ROUTINE_CLEAR, "", false, 0)).dimensions(rx + half, 156, colW - half, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Listo"), b -> this.close())
                .dimensions(this.width / 2 - 50, this.height - 28, 100, 20).build());
        this.loaded = false;
    }

    private void addStop(boolean atPlayer) {
        send(NpcActionPayload.ROUTINE_ADD, this.activities.get(this.activityIndex), atPlayer, SECONDS[this.secondsIndex]);
    }

    @Override
    public void tick() {
        this.age++;
        if (npc() == null && this.age > 60) {
            this.close();
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        // volver al editor principal
        MinecraftClient.getInstance().setScreen(new NpcEditorScreen(this.npcId));
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
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        AnnisNpcEntity npc = npc();
        if (npc != null) {
            if (!this.loaded) {
                this.loaded = true;
                this.dialogField.setText(npc.getDialog());
                this.figuraField.setText(npc.getFigura());
            }
            this.modeButton.setMessage(Text.literal("Modo: " + MODES[Math.max(0, Math.min(2, npc.getMode()))]));
            this.routineToggle.setMessage(Text.literal("Rutina: " + (npc.isRoutineOn() ? "activada" : "apagada")));
        }
        if (this.mcaButton != null) {
            boolean armed = System.currentTimeMillis() - this.mcaArmedAt < 4000;
            this.mcaButton.setMessage(armed
                    ? Text.literal("¿Seguro? Pasa a ser aldeano de MCA").formatted(Formatting.RED)
                    : Text.literal("Convertir en persona de MCA").formatted(Formatting.LIGHT_PURPLE));
        }
        String act = this.activities.get(this.activityIndex);
        this.activityButton.setMessage(Text.literal("Actividad: " + (act.isEmpty() ? "solo caminar" : act)));
        this.secondsButton.setMessage(Text.literal("Duracion de la parada: " + SECONDS[this.secondsIndex] + " s"));

        super.render(context, mouseX, mouseY, delta);

        int colW = Math.min(210, (this.width - 36) / 2);
        int lx = this.width / 2 - colW - 6;
        int rx = this.width / 2 + 6;
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Opciones avanzadas"), this.width / 2, 10, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Comportamiento").formatted(Formatting.GOLD), lx, 30, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Dialogo (clic derecho al NPC)").formatted(Formatting.GOLD), lx, 68, 0xFFFFFF);
        String fig = FiguraBridge.available() ? "Avatar de Figura" : "Avatar de Figura (Figura no instalado)";
        context.drawTextWithShadow(this.textRenderer, Text.literal(fig).formatted(Formatting.GOLD), lx, 130, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Equipo").formatted(Formatting.GOLD), lx, 184, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Shift + clic derecho con un objeto en la mano").formatted(Formatting.GRAY), lx, 220, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("lo equipa (armadura, arma, escudo...)").formatted(Formatting.GRAY), lx, 231, 0xFFFFFF);

        context.drawTextWithShadow(this.textRenderer, Text.literal("Rutina (las paradas se repiten en bucle)").formatted(Formatting.GOLD), rx, 30, 0xFFFFFF);
        if (npc != null) {
            List<AnnisNpcEntity.Stop> stops = npc.getStops();
            int y = 182;
            if (stops.isEmpty()) {
                context.drawTextWithShadow(this.textRenderer, Text.literal("Sin paradas todavia.").formatted(Formatting.GRAY), rx, y, 0xFFFFFF);
            }
            for (int i = 0; i < stops.size() && y < this.height - 36; i++) {
                AnnisNpcEntity.Stop s = stops.get(i);
                String label = (i + 1) + ") " + (s.activity().isEmpty() ? "caminar" : s.activity()) + " " + s.seconds() + "s  ("
                        + Math.round(s.x()) + ", " + Math.round(s.y()) + ", " + Math.round(s.z()) + ")";
                context.drawTextWithShadow(this.textRenderer, Text.literal(label), rx, y, 0xDDDDDD);
                y += 10;
            }
        }
    }
}
