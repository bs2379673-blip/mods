package com.annis.npc.client;

import com.annis.npc.client.importer.MotionImporter;
import com.annis.npc.entity.AnnisNpcEntity;
import com.annis.npc.net.NpcActionPayload;
import com.annis.npc.compat.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
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

    private EditBox sceneField;
    private EditBox musicField;
    private EditBox recordName;
    private Button secondsButton;
    private Button loopButton;
    private Button pasteButton;
    private Button fileButton;
    private Button importButton;
    private int lx;
    private int rx;
    private int w;

    public NpcSceneScreen(int npcId) {
        super(Component.literal("Escena"));
        this.npcId = npcId;
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

    private static void tell(String msg, boolean actionBar) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(msg), actionBar);
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
        this.sceneField = new EditBox(this.font, lx, 44, w, 18, Component.literal("Grupo"));
        this.sceneField.setHint(Component.literal("Grupo (ej: banda, plaza)"));
        this.sceneField.setMaxLength(32);
        this.addRenderableWidget(this.sceneField);
        this.addRenderableWidget(Button.builder(Component.literal("Guardar grupo"), b ->
                send(NpcActionPayload.SET_SCENE, this.sceneField.getValue(), false, 0)).bounds(lx, 64, half - 2, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("▶ Sincronizar"), b -> {
            send(NpcActionPayload.SET_SCENE, this.sceneField.getValue(), false, 0);
            send(NpcActionPayload.SET_MUSIC, this.musicField.getValue(), false, 0);
            send(NpcActionPayload.SYNC_SCENE, "", false, 0);
        }).bounds(lx + half, 64, w - half, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Copiar este NPC"), b -> {
            clipboard = this.npcId;
            tell("NPC copiado: abre otro NPC y pulsa Pegar", true);
        }).bounds(lx, 128, half - 2, 20).build());
        this.pasteButton = Button.builder(Component.literal("Pegar aqui"), b -> {
            if (clipboard >= 0 && clipboard != this.npcId) {
                send(NpcActionPayload.COPY_FROM, "", false, clipboard);
            }
        }).bounds(lx + half, 128, w - half, 20).build();
        this.addRenderableWidget(this.pasteButton);

        // ---- izquierda abajo: grabador ----
        this.recordName = new EditBox(this.font, lx, 172, w, 18, Component.literal("Nombre"));
        this.recordName.setHint(Component.literal("Nombre del movimiento"));
        this.recordName.setMaxLength(40);
        this.addRenderableWidget(this.recordName);
        this.secondsButton = Button.builder(Component.empty(), b -> this.secondsIndex = (this.secondsIndex + 1) % SECONDS.length)
                .bounds(lx, 192, half - 2, 20).build();
        this.addRenderableWidget(this.secondsButton);
        this.loopButton = Button.builder(Component.empty(), b -> this.loopMove = !this.loopMove)
                .bounds(lx + half, 192, w - half, 20).build();
        this.addRenderableWidget(this.loopButton);
        this.addRenderableWidget(Button.builder(Component.literal("● Grabarme (3 s de cuenta atras)"), b -> {
            String name = MoveRecorder.sanitize(this.recordName.getValue());
            if (name.isEmpty()) {
                this.setFocused(this.recordName);
                return;
            }
            if (MoveRecorder.start(name, SECONDS[this.secondsIndex], this.loopMove, null)) {
                Minecraft.getInstance().setScreen(null);
            }
        }).bounds(lx, 214, w, 20).build());

        // ---- derecha: musica ----
        this.musicField = new EditBox(this.font, rx, 44, w, 18, Component.literal("Musica"));
        this.musicField.setHint(Component.literal("annisnpc:musica.mi_cancion"));
        this.musicField.setMaxLength(100);
        this.addRenderableWidget(this.musicField);
        this.addRenderableWidget(Button.builder(Component.literal("Guardar musica"), b ->
                send(NpcActionPayload.SET_MUSIC, this.musicField.getValue(), false, 0)).bounds(rx, 64, half - 2, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("■ Parar musica"), b ->
                send(NpcActionPayload.STOP_MUSIC, "", false, 0)).bounds(rx + half, 64, w - half, 20).build());

        // ---- derecha abajo: importar ----
        this.fileButton = Button.builder(Component.empty(), b -> {
            this.files = MotionImporter.list();
            if (!this.files.isEmpty()) {
                this.fileIndex = (this.fileIndex + 1) % this.files.size();
            }
        }).bounds(rx, 128, w, 20).build();
        this.addRenderableWidget(this.fileButton);
        this.importButton = Button.builder(Component.literal("⇩ Importar como movimiento"), b -> startImport())
                .bounds(rx, 150, w, 20).build();
        this.addRenderableWidget(this.importButton);

        this.addRenderableWidget(Button.builder(Component.literal("< Volver"), b -> this.onClose())
                .bounds(8, 4, 70, 16).build());
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
            Minecraft.getInstance().execute(() -> {
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
            Minecraft.getInstance().setScreen(null);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(new NpcEditorScreen(this.npcId));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean enter = keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER;
        if (enter && this.sceneField.isFocused()) {
            send(NpcActionPayload.SET_SCENE, this.sceneField.getValue(), false, 0);
            return true;
        }
        if (enter && this.musicField.isFocused()) {
            send(NpcActionPayload.SET_MUSIC, this.musicField.getValue(), false, 0);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void label(GuiGraphics c, String s, int x, int y, boolean title) {
        c.drawString(this.font, title ? Component.literal(s).withStyle(ChatFormatting.GOLD) : Component.literal(s).withStyle(ChatFormatting.GRAY), x, y, 0xFFFFFF);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        AnnisNpcEntity npc = npc();
        if (npc != null && !this.loaded) {
            this.loaded = true;
            this.sceneField.setValue(npc.getScene());
            this.musicField.setValue(npc.getMusic());
        }
        this.secondsButton.setMessage(Component.literal("Duracion: " + SECONDS[this.secondsIndex] + " s"));
        this.loopButton.setMessage(Component.literal(this.loopMove ? "En bucle: si" : "En bucle: no"));
        this.pasteButton.active = clipboard >= 0 && clipboard != this.npcId;
        if (this.files.isEmpty()) {
            this.fileButton.setMessage(Component.literal("(no hay archivos)"));
            this.fileButton.active = false;
        } else {
            String f = this.font.plainSubstrByWidth("Archivo: " + this.files.get(this.fileIndex), this.w - 10);
            this.fileButton.setMessage(Component.literal(f));
            this.fileButton.active = true;
        }
        this.importButton.active = !this.files.isEmpty() && !importing;

        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredString(this.font, Component.literal("Escena"), this.width / 2, 10, 0xFFFFFF);
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
