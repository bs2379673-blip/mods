package com.annis.npc.client;

import com.annis.npc.entity.AnnisNpcEntity;
import com.annis.npc.net.NpcActionPayload;
import com.annis.npc.compat.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Editor visual de un NPC: vista previa, lista de skins y de emotes con busqueda,
 * y opciones (modelo, quieto, invencible, nombre, eliminar). Todo con clics.
 */
public class NpcEditorScreen extends Screen {
    private static final int[] HOLD_SECONDS = {0, 2, 5, 10, 30, 60, 300};

    private final int npcId;
    private final List<String> skins = new ArrayList<>();
    private final List<String> emotes = new ArrayList<>();
    private List<String> skinView = new ArrayList<>();
    private List<String> emoteView = new ArrayList<>();
    private final List<Button> skinButtons = new ArrayList<>();
    private final List<Button> emoteButtons = new ArrayList<>();

    private String skinFilter = "";
    private String emoteFilter = "";
    private int skinPage;
    private int emotePage;
    private int holdIndex = 2;
    private int age;
    private int rows = 6;
    private boolean nameLoaded;
    private long deleteArmedAt;

    // layout calculado en init()
    private int panelW;
    private int leftX;
    private int rightX;
    private int centerX;
    private int centerW;
    private int previewTop;
    private int previewBottom;
    private int listTop;

    private EditBox skinSearch;
    private EditBox emoteSearch;
    private EditBox nameField;
    private EditBox urlField;
    private Button slimButton;
    private Button stationaryButton;
    private Button protectedButton;
    private Button deleteButton;
    private Button holdButton;

    public NpcEditorScreen(int npcId) {
        super(Component.literal("Editor de NPC"));
        this.npcId = npcId;
    }

    private AnnisNpcEntity npc() {
        ClientLevel world = Minecraft.getInstance().level;
        if (world == null) {
            return null;
        }
        Entity entity = world.getEntity(this.npcId);
        return entity instanceof AnnisNpcEntity npc && !npc.isRemoved() ? npc : null;
    }

    private void send(int action, String text, boolean flag, int number) {
        ClientPlayNetworking.send(new NpcActionPayload(this.npcId, action, text, flag, number));
    }

    private void reloadLists() {
        this.skins.clear();
        this.skins.add(""); // predeterminada
        this.skins.addAll(SkinManager.list());
        this.emotes.clear();
        this.emotes.addAll(Activities.names());
        for (String name : MoveLibrary.list()) {
            if (!this.emotes.contains(name)) {
                this.emotes.add(name);
            }
        }
        for (String name : EmoteLibrary.list()) {
            if (!this.emotes.contains(name)) {
                this.emotes.add(name);
            }
        }
        applyFilters();
    }

    private void applyFilters() {
        this.skinView = filter(this.skins, this.skinFilter);
        this.emoteView = filter(this.emotes, this.emoteFilter);
        clampPages();
    }

    private static List<String> filter(List<String> source, String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String s : source) {
            if (q.isEmpty() || s.isEmpty() || s.toLowerCase(Locale.ROOT).contains(q)) {
                out.add(s);
            }
        }
        return out;
    }

    private int pageCount(List<String> view) {
        return Math.max(1, (view.size() + this.rows - 1) / this.rows);
    }

    private void clampPages() {
        this.skinPage = Math.max(0, Math.min(this.skinPage, pageCount(this.skinView) - 1));
        this.emotePage = Math.max(0, Math.min(this.emotePage, pageCount(this.emoteView) - 1));
    }

    @Override
    protected void init() {
        reloadLists();
        int pad = 8;
        this.panelW = Math.max(110, Math.min(170, this.width / 3 - 12));
        this.leftX = pad;
        this.rightX = this.width - pad - this.panelW;
        this.centerX = this.leftX + this.panelW + pad;
        this.centerW = this.rightX - pad - this.centerX;
        int searchY = 34;
        this.listTop = 56;
        this.rows = Math.max(3, Math.min(10, (this.height - this.listTop - 58) / 22));
        this.skinButtons.clear();
        this.emoteButtons.clear();
        clampPages();

        // --- columna izquierda: skins ---
        this.skinSearch = new EditBox(this.font, this.leftX, searchY, this.panelW, 18, Component.literal("Buscar skin"));
        this.skinSearch.setHint(Component.literal("Buscar skin..."));
        this.skinSearch.setValue(this.skinFilter);
        this.skinSearch.setResponder(s -> {
            this.skinFilter = s;
            this.skinPage = 0;
            applyFilters();
        });
        this.addRenderableWidget(this.skinSearch);

        // --- columna derecha: emotes ---
        this.emoteSearch = new EditBox(this.font, this.rightX, searchY, this.panelW, 18, Component.literal("Buscar emote"));
        this.emoteSearch.setHint(Component.literal("Buscar emote..."));
        this.emoteSearch.setValue(this.emoteFilter);
        this.emoteSearch.setResponder(s -> {
            this.emoteFilter = s;
            this.emotePage = 0;
            applyFilters();
        });
        this.addRenderableWidget(this.emoteSearch);

        for (int i = 0; i < this.rows; i++) {
            final int row = i;
            Button skinButton = Button.builder(Component.empty(), b -> clickSkin(row))
                    .bounds(this.leftX, this.listTop + i * 22, this.panelW, 20).build();
            this.skinButtons.add(this.addRenderableWidget(skinButton));
            Button emoteButton = Button.builder(Component.empty(), b -> clickEmote(row))
                    .bounds(this.rightX, this.listTop + i * 22, this.panelW, 20).build();
            this.emoteButtons.add(this.addRenderableWidget(emoteButton));
        }

        int pageY = this.listTop + this.rows * 22 + 2;
        this.addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            this.skinPage--;
            clampPages();
        }).bounds(this.leftX, pageY, 24, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            this.skinPage++;
            clampPages();
        }).bounds(this.leftX + this.panelW - 24, pageY, 24, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            this.emotePage--;
            clampPages();
        }).bounds(this.rightX, pageY, 24, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            this.emotePage++;
            clampPages();
        }).bounds(this.rightX + this.panelW - 24, pageY, 24, 18).build());

        int controlsY = pageY + 22;
        int half = this.panelW / 2;
        this.holdButton = Button.builder(Component.empty(), b -> this.holdIndex = (this.holdIndex + 1) % HOLD_SECONDS.length)
                .bounds(this.rightX, controlsY, half - 2, 20).build();
        this.addRenderableWidget(this.holdButton);
        this.addRenderableWidget(Button.builder(Component.literal("Detener emote"), b -> send(NpcActionPayload.STOP_EMOTE, "", false, 0))
                .bounds(this.rightX + half + 2, controlsY, half - 2, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Recargar"), b -> {
            SkinManager.clear();
            EmoteLibrary.clear();
            MoveLibrary.clear();
            reloadLists();
        }).bounds(this.leftX, controlsY, this.panelW, 20).build());

        // --- centro: vista previa y opciones ---
        int previewH = Math.max(80, Math.min(190, this.height - 190));
        this.previewTop = 30;
        this.previewBottom = this.previewTop + previewH;
        int y = this.previewBottom + 6;
        this.nameField = new EditBox(this.font, this.centerX, y, this.centerW, 18, Component.literal("Nombre"));
        this.nameField.setHint(Component.literal("Nombre del NPC (Enter para guardar)"));
        this.nameField.setMaxLength(32);
        this.addRenderableWidget(this.nameField);
        y += 22;

        this.urlField = new EditBox(this.font, this.centerX, y, this.centerW - 52, 18, Component.literal("URL de skin"));
        this.urlField.setHint(Component.literal("Skin por URL (https://...png)"));
        this.urlField.setMaxLength(400);
        this.addRenderableWidget(this.urlField);
        this.addRenderableWidget(Button.builder(Component.literal("Usar"), b -> applyUrl())
                .bounds(this.centerX + this.centerW - 50, y - 1, 50, 20).build());
        y += 22;

        this.slimButton = Button.builder(Component.empty(), b -> {
            AnnisNpcEntity npc = npc();
            if (npc != null) {
                // ciclo: normal -> slim -> normal articulado -> slim articulado
                int state = (npc.isJointed() ? 2 : 0) + (npc.isSlim() ? 1 : 0);
                int next = (state + 1) % 4;
                send(NpcActionPayload.SET_SKIN, npc.getSkin(), (next & 1) != 0, 0);
                send(NpcActionPayload.SET_JOINTED, "", (next & 2) != 0, 0);
            }
        }).bounds(this.centerX, y, this.centerW, 20).build();
        this.addRenderableWidget(this.slimButton);
        y += 22;

        this.stationaryButton = Button.builder(Component.empty(), b -> {
            AnnisNpcEntity npc = npc();
            if (npc != null) {
                send(NpcActionPayload.SET_STATIONARY, "", !npc.isStationary(), 0);
            }
        }).bounds(this.centerX, y, this.centerW, 20).build();
        this.addRenderableWidget(this.stationaryButton);
        y += 22;

        this.protectedButton = Button.builder(Component.empty(), b -> {
            AnnisNpcEntity npc = npc();
            if (npc != null) {
                send(NpcActionPayload.SET_PROTECTED, "", !npc.isProtected(), 0);
            }
        }).bounds(this.centerX, y, this.centerW, 20).build();
        this.addRenderableWidget(this.protectedButton);
        y += 22;

        int halfCenter = this.centerW / 2;
        this.deleteButton = Button.builder(Component.empty(), b -> {
            long now = System.currentTimeMillis();
            if (now - this.deleteArmedAt < 3000) {
                send(NpcActionPayload.REMOVE, "", false, 0);
                this.onClose();
            } else {
                this.deleteArmedAt = now;
            }
        }).bounds(this.centerX, y, halfCenter - 2, 20).build();
        this.addRenderableWidget(this.deleteButton);
        this.addRenderableWidget(Button.builder(Component.literal("Listo"), b -> this.onClose())
                .bounds(this.centerX + halfCenter + 2, y, halfCenter - 2, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Mas opciones..."), b -> {
            sendName();
            Minecraft.getInstance().setScreen(new NpcAdvancedScreen(this.npcId));
        }).bounds(8, 4, 92, 16).build());
        this.addRenderableWidget(Button.builder(Component.literal("Accesorios..."), b -> {
            sendName();
            Minecraft.getInstance().setScreen(new NpcAccessoryScreen(this.npcId));
        }).bounds(104, 4, 80, 16).build());
        this.addRenderableWidget(Button.builder(Component.literal("Escena y grabar..."), b -> {
            sendName();
            Minecraft.getInstance().setScreen(new NpcSceneScreen(this.npcId));
        }).bounds(188, 4, 100, 16).build());

        this.nameLoaded = false;
    }

    private void clickSkin(int row) {
        int index = this.skinPage * this.rows + row;
        AnnisNpcEntity npc = npc();
        if (npc == null || index >= this.skinView.size()) {
            return;
        }
        String skin = this.skinView.get(index);
        String lower = skin.toLowerCase(Locale.ROOT);
        boolean slim = lower.contains("slim") || lower.contains("alex") ? true : npc.isSlim();
        send(NpcActionPayload.SET_SKIN, skin, slim, 0);
    }

    private void applyUrl() {
        AnnisNpcEntity npc = npc();
        String url = this.urlField == null ? "" : this.urlField.getValue().trim();
        if (npc == null || !SkinManager.isUrl(url)) {
            return;
        }
        send(NpcActionPayload.SET_SKIN, url, npc.isSlim(), 0);
    }

    private void clickEmote(int row) {
        int index = this.emotePage * this.rows + row;
        if (index >= this.emoteView.size()) {
            return;
        }
        String emote = this.emoteView.get(index);
        int hold = HOLD_SECONDS[this.holdIndex];
        if (NpcAnimState.isProcedural(emote)) {
            hold = Math.max(hold, 3600); // las actividades no terminan solas
        }
        send(NpcActionPayload.PLAY_EMOTE, emote, false, hold);
    }

    private void sendName() {
        AnnisNpcEntity npc = npc();
        if (npc == null || this.nameField == null) {
            return;
        }
        String typed = this.nameField.getValue().trim();
        String current = npc.hasCustomName() ? npc.getCustomName().getString() : "";
        if (!typed.equals(current)) {
            send(NpcActionPayload.SET_NAME, typed, false, 0);
        }
    }

    private void fillRows(List<Button> buttons, List<String> view, int page, String current, boolean isSkin) {
        for (int i = 0; i < buttons.size(); i++) {
            int index = page * this.rows + i;
            Button button = buttons.get(i);
            if (index < view.size()) {
                String value = view.get(index);
                String label = isSkin && value.isEmpty() ? "Predeterminada" : value;
                if (!isSkin && EmoteLibrary.get(value) == null) {
                    if (MoveLibrary.has(value)) {
                        label = "● " + value;
                    } else if (Activities.has(value)) {
                        label = "★ " + value;
                    }
                }
                boolean selected = value.equals(current) && (isSkin || !value.isEmpty());
                String shown = this.font.plainSubstrByWidth(label, this.panelW - 22);
                Component text = Component.literal((selected ? "» " : "") + shown);
                button.setMessage(selected ? text.copy().withStyle(ChatFormatting.YELLOW) : text);
                button.visible = true;
                button.active = true;
            } else {
                button.visible = false;
                button.active = false;
            }
        }
    }

    private void refreshState() {
        AnnisNpcEntity npc = npc();
        String currentSkin = npc == null ? "" : npc.getSkin();
        String currentEmote = npc == null ? "" : npc.getEmote();
        fillRows(this.skinButtons, this.skinView, this.skinPage, currentSkin, true);
        fillRows(this.emoteButtons, this.emoteView, this.emotePage, currentEmote, false);

        int hold = HOLD_SECONDS[this.holdIndex];
        this.holdButton.setMessage(Component.literal(hold == 0 ? "Quieto: no" : "Quieto: " + hold + " s"));

        boolean armed = System.currentTimeMillis() - this.deleteArmedAt < 3000;
        this.deleteButton.setMessage(armed
                ? Component.literal("¿Seguro?").withStyle(ChatFormatting.RED)
                : Component.literal("Eliminar"));

        if (npc != null) {
            if (!this.nameLoaded) {
                this.nameLoaded = true;
                if (npc.hasCustomName()) {
                    this.nameField.setValue(npc.getCustomName().getString());
                }
                if (SkinManager.isUrl(npc.getSkin())) {
                    this.urlField.setValue(npc.getSkin());
                }
            }
            this.slimButton.setMessage(Component.literal("Modelo: " + (npc.isSlim() ? "slim" : "normal")
                    + (npc.isJointed() ? " + articulaciones" : " (rigido)")));
            this.stationaryButton.setMessage(Component.literal(npc.isStationary() ? "Camina: no (quieto)" : "Camina: si"));
            this.protectedButton.setMessage(Component.literal("Invencible: " + (npc.isProtected() ? "si" : "no")));
        }
    }

    @Override
    public void tick() {
        this.age++;
        // el NPC recien creado puede tardar un instante en llegar al cliente
        if (npc() == null && this.age > 60) {
            this.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.nameField != null && this.nameField.isFocused()
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            sendName();
            this.nameField.setFocused(false);
            return true;
        }
        if (this.urlField != null && this.urlField.isFocused()
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            applyUrl();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        sendName();
        super.removed();
    }

    private void drawBackground(GuiGraphics context) {
        super.renderBackground(context);
        int bottom = this.height - 22;
        context.fill(this.leftX - 4, 28, this.leftX + this.panelW + 4, bottom, 0x80000000);
        context.fill(this.rightX - 4, 28, this.rightX + this.panelW + 4, bottom, 0x80000000);
        context.fill(this.centerX - 2, 28, this.centerX + this.centerW + 2, this.previewBottom + 2, 0x80000000);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        refreshState();
        drawBackground(context);
        super.render(context, mouseX, mouseY, delta);

        if (this.width > 420) {
            context.drawString(this.font, Component.literal("Editor de NPC"), this.width - 8 - this.font.width("Editor de NPC"), 8, 0xFFFFFF);
        }
        context.drawString(this.font, Component.literal("Skins").withStyle(ChatFormatting.GOLD), this.leftX, 22, 0xFFFFFF);
        context.drawString(this.font, Component.literal("Emotes").withStyle(ChatFormatting.GOLD), this.rightX, 22, 0xFFFFFF);

        int pageY = this.listTop + this.rows * 22 + 2;
        String skinPages = (this.skinPage + 1) + "/" + pageCount(this.skinView);
        String emotePages = (this.emotePage + 1) + "/" + pageCount(this.emoteView);
        context.drawCenteredString(this.font, Component.literal(skinPages), this.leftX + this.panelW / 2, pageY + 5, 0xAAAAAA);
        context.drawCenteredString(this.font, Component.literal(emotePages), this.rightX + this.panelW / 2, pageY + 5, 0xAAAAAA);

        // ayudas cuando las carpetas estan vacias
        if (this.skins.size() <= 1) {
            context.drawString(this.font, Component.literal("Pone PNG 64x64 en"), this.leftX, pageY + 50, 0xFFAA55);
            context.drawString(this.font, Component.literal("config/annisnpc/skins"), this.leftX, pageY + 61, 0xFFAA55);
        }

        AnnisNpcEntity npc = npc();
        if (npc != null) {
            int size = Math.max(30, (this.previewBottom - this.previewTop) / 2 - 8);
            renderNpcPreview(context, this.centerX, this.previewTop, this.centerX + this.centerW, this.previewBottom, size, mouseX, mouseY, npc);
        } else {
            context.drawCenteredString(this.font, Component.literal("Cargando NPC..."),
                    this.centerX + this.centerW / 2, (this.previewTop + this.previewBottom) / 2, 0xAAAAAA);
        }

        context.drawCenteredString(this.font,
                Component.literal("Clic en una skin la aplica. Emotes: ★ actividad, ● movimiento grabado."),
                this.width / 2, this.height - 14, 0x999999);
    }

    /** En 1.20.1 la vista del muneco se ubica por los pies (centro abajo del recuadro). */
    private static void renderNpcPreview(GuiGraphics ctx, int x1, int y1, int x2, int y2, int size, int mouseX, int mouseY,
                                         net.minecraft.world.entity.LivingEntity entity) {
        int cx = (x1 + x2) / 2;
        int feet = y2 - 6;
        InventoryScreen.renderEntityInInventoryFollowsMouse(ctx, cx, feet, size, (float) (cx - mouseX),
                (float) (feet - size * 1.6f - mouseY), entity);
    }
}
