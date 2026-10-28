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

/** Grupo de escena (sincronizar), copiar/pegar NPC y grabador de movimientos. */
public class NpcSceneScreen extends Screen {
    private static final int[] SECONDS = {3, 5, 8, 10, 15, 20, 30, 60};
    /** Portapapeles: id del NPC copiado. */
    static int clipboard = -1;

    private final int npcId;
    private boolean loaded;
    private int age;
    private int secondsIndex = 2;
    private boolean loopMove = true;

    private TextFieldWidget sceneField;
    private TextFieldWidget recordName;
    private ButtonWidget secondsButton;
    private ButtonWidget loopButton;
    private ButtonWidget pasteButton;

    public NpcSceneScreen(int npcId) {
        super(Text.literal("Escena y grabacion"));
        this.npcId = npcId;
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

    @Override
    protected void init() {
        int w = Math.min(260, this.width - 40);
        int x = this.width / 2 - w / 2;
        int half = w / 2;

        this.sceneField = new TextFieldWidget(this.textRenderer, x, 44, w, 18, Text.literal("Grupo"));
        this.sceneField.setPlaceholder(Text.literal("Nombre del grupo (ej: banda, plaza)"));
        this.sceneField.setMaxLength(32);
        this.addDrawableChild(this.sceneField);
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Guardar grupo"), b ->
                send(NpcActionPayload.SET_SCENE, this.sceneField.getText(), false, 0)).dimensions(x, 64, half - 2, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("▶ Sincronizar grupo"), b -> {
            send(NpcActionPayload.SET_SCENE, this.sceneField.getText(), false, 0);
            send(NpcActionPayload.SYNC_SCENE, "", false, 0);
        }).dimensions(x + half, 64, w - half, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Copiar este NPC"), b -> {
            clipboard = this.npcId;
            if (MinecraftClient.getInstance().player != null) {
                MinecraftClient.getInstance().player.sendMessage(Text.literal("NPC copiado: abre otro NPC y pulsa Pegar"), true);
            }
        }).dimensions(x, 118, half - 2, 20).build());
        this.pasteButton = ButtonWidget.builder(Text.literal("Pegar aqui"), b -> {
            if (clipboard >= 0 && clipboard != this.npcId) {
                send(NpcActionPayload.COPY_FROM, "", false, clipboard);
            }
        }).dimensions(x + half, 118, w - half, 20).build();
        this.addDrawableChild(this.pasteButton);

        this.recordName = new TextFieldWidget(this.textRenderer, x, 162, w, 18, Text.literal("Nombre"));
        this.recordName.setPlaceholder(Text.literal("Nombre del movimiento (ej: tocar_tambor)"));
        this.recordName.setMaxLength(40);
        this.addDrawableChild(this.recordName);
        this.secondsButton = ButtonWidget.builder(Text.empty(), b -> this.secondsIndex = (this.secondsIndex + 1) % SECONDS.length)
                .dimensions(x, 182, half - 2, 20).build();
        this.addDrawableChild(this.secondsButton);
        this.loopButton = ButtonWidget.builder(Text.empty(), b -> this.loopMove = !this.loopMove)
                .dimensions(x + half, 182, w - half, 20).build();
        this.addDrawableChild(this.loopButton);
        this.addDrawableChild(ButtonWidget.builder(Text.literal("● Grabarme (cuenta atras de 3 s)"), b -> {
            String name = MoveRecorder.sanitize(this.recordName.getText());
            if (name.isEmpty()) {
                this.recordName.setFocused(true);
                return;
            }
            if (MoveRecorder.start(name, SECONDS[this.secondsIndex], this.loopMove, null)) {
                MinecraftClient.getInstance().setScreen(null);
            }
        }).dimensions(x, 204, w, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Volver"), b -> this.close())
                .dimensions(this.width / 2 - 50, this.height - 28, 100, 20).build());
        this.loaded = false;
    }

    @Override
    public void tick() {
        this.age++;
        if (npc() == null && this.age > 60) {
            MinecraftClient.getInstance().setScreen(null);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(new NpcEditorScreen(this.npcId));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && this.sceneField.isFocused()) {
            send(NpcActionPayload.SET_SCENE, this.sceneField.getText(), false, 0);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        AnnisNpcEntity npc = npc();
        if (npc != null && !this.loaded) {
            this.loaded = true;
            this.sceneField.setText(npc.getScene());
        }
        this.secondsButton.setMessage(Text.literal("Duracion: " + SECONDS[this.secondsIndex] + " s"));
        this.loopButton.setMessage(Text.literal(this.loopMove ? "En bucle: si" : "En bucle: no"));
        this.pasteButton.active = clipboard >= 0 && clipboard != this.npcId;

        super.render(context, mouseX, mouseY, delta);

        int w = Math.min(260, this.width - 40);
        int x = this.width / 2 - w / 2;
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Escena y grabacion"), this.width / 2, 10, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Grupo de escena").formatted(Formatting.GOLD), x, 32, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Pon a cada NPC su actividad y el mismo grupo;").formatted(Formatting.GRAY), x, 87, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Sincronizar los arranca a todos en el mismo tick.").formatted(Formatting.GRAY), x, 96, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Copiar / pegar (aspecto, equipo, accesorios, dialogo)").formatted(Formatting.GOLD), x, 108, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Grabador de movimientos").formatted(Formatting.GOLD), x, 150, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Graba tu cuerpo tal cual se ve (Fresh Animations,").formatted(Formatting.GRAY), x, 228, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("emotes...). Para otro jugador: /npcrecord ... <jugador>").formatted(Formatting.GRAY), x, 238, 0xFFFFFF);
    }
}
