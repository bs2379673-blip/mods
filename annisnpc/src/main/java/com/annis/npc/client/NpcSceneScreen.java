package com.annis.npc.client;

import com.annis.npc.client.importer.MotionImporter;
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

import java.util.List;

/** Escena: grupo sincronizado, musica, copiar/pegar, grabador e importador de bailes (BVH / MMD). */
public class NpcSceneScreen extends Screen {
    private static final int[] SECONDS = {3, 5, 8, 10, 15, 20, 30, 60};
    /** Portapapeles: id del NPC copiado. */
    static int clipboard = -1;
    private static volatile boolean importing;

    private final int npcId;
    private boolean loaded;
    private int age;
    private int secondsIndex = 2;
    private boolean loopMove = true;
    private int fileIndex;
    private List<String> files = List.of();

    private TextFieldWidget sceneField;
    private TextFieldWidget musicField;
    private TextFieldWidget recordName;
    private ButtonWidget secondsButton;
    private ButtonWidget loopButton;
    private ButtonWidget pasteButton;
    private ButtonWidget fileButton;
    private ButtonWidget importButton;
    private int lx;
    private int rx;
    private int w;

    public NpcSceneScreen(int npcId) {
        super(Text.literal("Escena"));
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

    private static void tell(String msg, boolean actionBar) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal(msg), actionBar);
        }
    }

    @Override
    protected void init() {
        this.w = Math.min(220, (this.width - 36) / 2);
        this.lx = this.width / 2 - this.w - 6;
        this.rx = this.width / 2 + 6;
        int half = this.w / 2;
        this.files = MotionImporter.list();
        this.fileIndex = Math.min(this.fileIndex, Math.max(0, this.files.size() - 1));

        // ---- izquierda: grupo y copiar ----
        this.sceneField = new TextFieldWidget(this.textRenderer, lx, 44, w, 18, Text.literal("Grupo"));
        this.sceneField.setPlaceholder(Text.literal("Grupo (ej: banda, plaza)"));
        this.sceneField.setMaxLength(32);
        this.addDrawableChild(this.sceneField);
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Guardar grupo"), b ->
                send(NpcActionPayload.SET_SCENE, this.sceneField.getText(), false, 0)).dimensions(lx, 64, half - 2, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("▶ Sincronizar"), b -> {
            send(NpcActionPayload.SET_SCENE, this.sceneField.getText(), false, 0);
            send(NpcActionPayload.SET_MUSIC, this.musicField.getText(), false, 0);
            send(NpcActionPayload.SYNC_SCENE, "", false, 0);
        }).dimensions(lx + half, 64, w - half, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Copiar este NPC"), b -> {
            clipboard = this.npcId;
            tell("NPC copiado: abre otro NPC y pulsa Pegar", true);
        }).dimensions(lx, 128, half - 2, 20).build());
        this.pasteButton = ButtonWidget.builder(Text.literal("Pegar aqui"), b -> {
            if (clipboard >= 0 && clipboard != this.npcId) {
                send(NpcActionPayload.COPY_FROM, "", false, clipboard);
            }
        }).dimensions(lx + half, 128, w - half, 20).build();
        this.addDrawableChild(this.pasteButton);

        // ---- izquierda abajo: grabador ----
        this.recordName = new TextFieldWidget(this.textRenderer, lx, 172, w, 18, Text.literal("Nombre"));
        this.recordName.setPlaceholder(Text.literal("Nombre del movimiento"));
        this.recordName.setMaxLength(40);
        this.addDrawableChild(this.recordName);
        this.secondsButton = ButtonWidget.builder(Text.empty(), b -> this.secondsIndex = (this.secondsIndex + 1) % SECONDS.length)
                .dimensions(lx, 192, half - 2, 20).build();
        this.addDrawableChild(this.secondsButton);
        this.loopButton = ButtonWidget.builder(Text.empty(), b -> this.loopMove = !this.loopMove)
                .dimensions(lx + half, 192, w - half, 20).build();
        this.addDrawableChild(this.loopButton);
        this.addDrawableChild(ButtonWidget.builder(Text.literal("● Grabarme (3 s de cuenta atras)"), b -> {
            String name = MoveRecorder.sanitize(this.recordName.getText());
            if (name.isEmpty()) {
                this.setFocused(this.recordName);
                return;
            }
            if (MoveRecorder.start(name, SECONDS[this.secondsIndex], this.loopMove, null)) {
                MinecraftClient.getInstance().setScreen(null);
            }
        }).dimensions(lx, 214, w, 20).build());

        // ---- derecha: musica ----
        this.musicField = new TextFieldWidget(this.textRenderer, rx, 44, w, 18, Text.literal("Musica"));
        this.musicField.setPlaceholder(Text.literal("annisnpc:musica.mi_cancion"));
        this.musicField.setMaxLength(100);
        this.addDrawableChild(this.musicField);
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Guardar musica"), b ->
                send(NpcActionPayload.SET_MUSIC, this.musicField.getText(), false, 0)).dimensions(rx, 64, half - 2, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("■ Parar musica"), b ->
                send(NpcActionPayload.STOP_MUSIC, "", false, 0)).dimensions(rx + half, 64, w - half, 20).build());

        // ---- derecha abajo: importar ----
        this.fileButton = ButtonWidget.builder(Text.empty(), b -> {
            this.files = MotionImporter.list();
            if (!this.files.isEmpty()) {
                this.fileIndex = (this.fileIndex + 1) % this.files.size();
            }
        }).dimensions(rx, 128, w, 20).build();
        this.addDrawableChild(this.fileButton);
        this.importButton = ButtonWidget.builder(Text.literal("⇩ Importar como movimiento"), b -> startImport())
                .dimensions(rx, 150, w, 20).build();
        this.addDrawableChild(this.importButton);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("< Volver"), b -> this.close())
                .dimensions(8, 4, 70, 16).build());
        this.loaded = false;
    }

    private void startImport() {
        if (this.files.isEmpty() || importing) {
            return;
        }
        String file = this.files.get(this.fileIndex);
        boolean loop = this.loopMove;
        importing = true;
        tell("Importando " + file + "...", true);
        Thread t = new Thread(() -> {
            String msg;
            try {
                msg = "Baile " + MotionImporter.importFile(file, null, loop) + ". Ya esta en la lista de emotes (●).";
            } catch (Exception e) {
                msg = "No se pudo importar " + file + ": " + e.getMessage();
                com.annis.npc.AnnisNpc.LOGGER.warn("Error importando {}", file, e);
            }
            final String m = msg;
            MinecraftClient.getInstance().execute(() -> {
                importing = false;
                tell(m, false);
            });
        }, "annisnpc-import");
        t.setDaemon(true);
        t.start();
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
        boolean enter = keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER;
        if (enter && this.sceneField.isFocused()) {
            send(NpcActionPayload.SET_SCENE, this.sceneField.getText(), false, 0);
            return true;
        }
        if (enter && this.musicField.isFocused()) {
            send(NpcActionPayload.SET_MUSIC, this.musicField.getText(), false, 0);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void label(DrawContext c, String s, int x, int y, boolean title) {
        c.drawTextWithShadow(this.textRenderer, title ? Text.literal(s).formatted(Formatting.GOLD) : Text.literal(s).formatted(Formatting.GRAY), x, y, 0xFFFFFF);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        AnnisNpcEntity npc = npc();
        if (npc != null && !this.loaded) {
            this.loaded = true;
            this.sceneField.setText(npc.getScene());
            this.musicField.setText(npc.getMusic());
        }
        this.secondsButton.setMessage(Text.literal("Duracion: " + SECONDS[this.secondsIndex] + " s"));
        this.loopButton.setMessage(Text.literal(this.loopMove ? "En bucle: si" : "En bucle: no"));
        this.pasteButton.active = clipboard >= 0 && clipboard != this.npcId;
        if (this.files.isEmpty()) {
            this.fileButton.setMessage(Text.literal("(no hay archivos)"));
            this.fileButton.active = false;
        } else {
            String f = this.textRenderer.trimToWidth("Archivo: " + this.files.get(this.fileIndex), this.w - 10);
            this.fileButton.setMessage(Text.literal(f));
            this.fileButton.active = true;
        }
        this.importButton.active = !this.files.isEmpty() && !importing;

        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Escena"), this.width / 2, 10, 0xFFFFFF);
        label(context, "Grupo de escena", lx, 32, true);
        label(context, "Mismo grupo + Sincronizar = todos", lx, 88, false);
        label(context, "empiezan a la vez (y suena la musica).", lx, 98, false);
        label(context, "Copiar / pegar NPC", lx, 116, true);
        label(context, "Grabador de movimientos", lx, 160, true);
        label(context, "Graba tu cuerpo tal cual se ve.", lx, 238, false);

        label(context, "Musica de la escena", rx, 32, true);
        label(context, "Id de un sonido de tu resource pack;", rx, 88, false);
        label(context, "suena desde el NPC al sincronizar.", rx, 98, false);
        label(context, "Importar baile (BVH o MMD .vmd)", rx, 116, true);
        label(context, "Pon los archivos en", rx, 174, false);
        label(context, "config/annisnpc/import", rx, 184, false);
        label(context, "Clic en Archivo para elegir otro.", rx, 194, false);
    }
}
