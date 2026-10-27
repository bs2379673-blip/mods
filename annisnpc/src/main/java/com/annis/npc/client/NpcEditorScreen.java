package com.annis.npc.client;

import com.annis.npc.entity.AnnisNpcEntity;
import com.annis.npc.net.NpcActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
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
    private final List<ButtonWidget> skinButtons = new ArrayList<>();
    private final List<ButtonWidget> emoteButtons = new ArrayList<>();

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

    private TextFieldWidget skinSearch;
    private TextFieldWidget emoteSearch;
    private TextFieldWidget nameField;
    private TextFieldWidget urlField;
    private ButtonWidget slimButton;
    private ButtonWidget stationaryButton;
    private ButtonWidget protectedButton;
    private ButtonWidget deleteButton;
    private ButtonWidget holdButton;

    public NpcEditorScreen(int npcId) {
        super(Text.literal("Editor de NPC"));
        this.npcId = npcId;
    }

    private AnnisNpcEntity npc() {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) {
            return null;
        }
        Entity entity = world.getEntityById(this.npcId);
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
        this.emotes.addAll(Activities.NAMES);
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
        this.skinSearch = new TextFieldWidget(this.textRenderer, this.leftX, searchY, this.panelW, 18, Text.literal("Buscar skin"));
        this.skinSearch.setPlaceholder(Text.literal("Buscar skin..."));
        this.skinSearch.setText(this.skinFilter);
        this.skinSearch.setChangedListener(s -> {
            this.skinFilter = s;
            this.skinPage = 0;
            applyFilters();
        });
        this.addDrawableChild(this.skinSearch);

        // --- columna derecha: emotes ---
        this.emoteSearch = new TextFieldWidget(this.textRenderer, this.rightX, searchY, this.panelW, 18, Text.literal("Buscar emote"));
        this.emoteSearch.setPlaceholder(Text.literal("Buscar emote..."));
        this.emoteSearch.setText(this.emoteFilter);
        this.emoteSearch.setChangedListener(s -> {
            this.emoteFilter = s;
            this.emotePage = 0;
            applyFilters();
        });
        this.addDrawableChild(this.emoteSearch);

        for (int i = 0; i < this.rows; i++) {
            final int row = i;
            ButtonWidget skinButton = ButtonWidget.builder(Text.empty(), b -> clickSkin(row))
                    .dimensions(this.leftX, this.listTop + i * 22, this.panelW, 20).build();
            this.skinButtons.add(this.addDrawableChild(skinButton));
            ButtonWidget emoteButton = ButtonWidget.builder(Text.empty(), b -> clickEmote(row))
                    .dimensions(this.rightX, this.listTop + i * 22, this.panelW, 20).build();
            this.emoteButtons.add(this.addDrawableChild(emoteButton));
        }

        int pageY = this.listTop + this.rows * 22 + 2;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> {
            this.skinPage--;
            clampPages();
        }).dimensions(this.leftX, pageY, 24, 18).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> {
            this.skinPage++;
            clampPages();
        }).dimensions(this.leftX + this.panelW - 24, pageY, 24, 18).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> {
            this.emotePage--;
            clampPages();
        }).dimensions(this.rightX, pageY, 24, 18).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> {
            this.emotePage++;
            clampPages();
        }).dimensions(this.rightX + this.panelW - 24, pageY, 24, 18).build());

        int controlsY = pageY + 22;
        int half = this.panelW / 2;
        this.holdButton = ButtonWidget.builder(Text.empty(), b -> this.holdIndex = (this.holdIndex + 1) % HOLD_SECONDS.length)
                .dimensions(this.rightX, controlsY, half - 2, 20).build();
        this.addDrawableChild(this.holdButton);
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Detener emote"), b -> send(NpcActionPayload.STOP_EMOTE, "", false, 0))
                .dimensions(this.rightX + half + 2, controlsY, half - 2, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Recargar"), b -> {
            SkinManager.clear();
            EmoteLibrary.clear();
            reloadLists();
        }).dimensions(this.leftX, controlsY, this.panelW, 20).build());

        // --- centro: vista previa y opciones ---
        int previewH = Math.max(80, Math.min(190, this.height - 190));
        this.previewTop = 30;
        this.previewBottom = this.previewTop + previewH;
        int y = this.previewBottom + 6;
        this.nameField = new TextFieldWidget(this.textRenderer, this.centerX, y, this.centerW, 18, Text.literal("Nombre"));
        this.nameField.setPlaceholder(Text.literal("Nombre del NPC (Enter para guardar)"));
        this.nameField.setMaxLength(32);
        this.addDrawableChild(this.nameField);
        y += 22;

        this.urlField = new TextFieldWidget(this.textRenderer, this.centerX, y, this.centerW - 52, 18, Text.literal("URL de skin"));
        this.urlField.setPlaceholder(Text.literal("Skin por URL (https://...png)"));
        this.urlField.setMaxLength(400);
        this.addDrawableChild(this.urlField);
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Usar"), b -> applyUrl())
                .dimensions(this.centerX + this.centerW - 50, y - 1, 50, 20).build());
        y += 22;

        this.slimButton = ButtonWidget.builder(Text.empty(), b -> {
            AnnisNpcEntity npc = npc();
            if (npc != null) {
                send(NpcActionPayload.SET_SKIN, npc.getSkin(), !npc.isSlim(), 0);
            }
        }).dimensions(this.centerX, y, this.centerW, 20).build();
        this.addDrawableChild(this.slimButton);
        y += 22;

        this.stationaryButton = ButtonWidget.builder(Text.empty(), b -> {
            AnnisNpcEntity npc = npc();
            if (npc != null) {
                send(NpcActionPayload.SET_STATIONARY, "", !npc.isStationary(), 0);
            }
        }).dimensions(this.centerX, y, this.centerW, 20).build();
        this.addDrawableChild(this.stationaryButton);
        y += 22;

        this.protectedButton = ButtonWidget.builder(Text.empty(), b -> {
            AnnisNpcEntity npc = npc();
            if (npc != null) {
                send(NpcActionPayload.SET_PROTECTED, "", !npc.isProtected(), 0);
            }
        }).dimensions(this.centerX, y, this.centerW, 20).build();
        this.addDrawableChild(this.protectedButton);
        y += 22;

        int halfCenter = this.centerW / 2;
        this.deleteButton = ButtonWidget.builder(Text.empty(), b -> {
            long now = System.currentTimeMillis();
            if (now - this.deleteArmedAt < 3000) {
                send(NpcActionPayload.REMOVE, "", false, 0);
                this.close();
            } else {
                this.deleteArmedAt = now;
            }
        }).dimensions(this.centerX, y, halfCenter - 2, 20).build();
        this.addDrawableChild(this.deleteButton);
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Listo"), b -> this.close())
                .dimensions(this.centerX + halfCenter + 2, y, halfCenter - 2, 20).build());

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
        String url = this.urlField == null ? "" : this.urlField.getText().trim();
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
        if (Activities.has(emote) && EmoteLibrary.get(emote) == null) {
            hold = Math.max(hold, 3600); // las actividades no terminan solas
        }
        send(NpcActionPayload.PLAY_EMOTE, emote, false, hold);
    }

    private void sendName() {
        AnnisNpcEntity npc = npc();
        if (npc == null || this.nameField == null) {
            return;
        }
        String typed = this.nameField.getText().trim();
        String current = npc.hasCustomName() ? npc.getCustomName().getString() : "";
        if (!typed.equals(current)) {
            send(NpcActionPayload.SET_NAME, typed, false, 0);
        }
    }

    private void fillRows(List<ButtonWidget> buttons, List<String> view, int page, String current, boolean isSkin) {
        for (int i = 0; i < buttons.size(); i++) {
            int index = page * this.rows + i;
            ButtonWidget button = buttons.get(i);
            if (index < view.size()) {
                String value = view.get(index);
                String label = isSkin && value.isEmpty() ? "Predeterminada" : value;
                if (!isSkin && Activities.has(value) && EmoteLibrary.get(value) == null) {
                    label = "★ " + value;
                }
                boolean selected = value.equals(current) && (isSkin || !value.isEmpty());
                String shown = this.textRenderer.trimToWidth(label, this.panelW - 22);
                Text text = Text.literal((selected ? "» " : "") + shown);
                button.setMessage(selected ? text.copy().formatted(Formatting.YELLOW) : text);
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
        this.holdButton.setMessage(Text.literal(hold == 0 ? "Quieto: no" : "Quieto: " + hold + " s"));

        boolean armed = System.currentTimeMillis() - this.deleteArmedAt < 3000;
        this.deleteButton.setMessage(armed
                ? Text.literal("¿Seguro?").formatted(Formatting.RED)
                : Text.literal("Eliminar"));

        if (npc != null) {
            if (!this.nameLoaded) {
                this.nameLoaded = true;
                if (npc.hasCustomName()) {
                    this.nameField.setText(npc.getCustomName().getString());
                }
                if (SkinManager.isUrl(npc.getSkin())) {
                    this.urlField.setText(npc.getSkin());
                }
            }
            this.slimButton.setMessage(Text.literal("Modelo: " + (npc.isSlim() ? "brazos finos (slim)" : "normal")));
            this.stationaryButton.setMessage(Text.literal(npc.isStationary() ? "Camina: no (quieto)" : "Camina: si"));
            this.protectedButton.setMessage(Text.literal("Invencible: " + (npc.isProtected() ? "si" : "no")));
        }
    }

    @Override
    public void tick() {
        this.age++;
        // el NPC recien creado puede tardar un instante en llegar al cliente
        if (npc() == null && this.age > 60) {
            this.close();
        }
    }

    @Override
    public boolean shouldPause() {
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

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        int bottom = this.height - 22;
        context.fill(this.leftX - 4, 28, this.leftX + this.panelW + 4, bottom, 0x80000000);
        context.fill(this.rightX - 4, 28, this.rightX + this.panelW + 4, bottom, 0x80000000);
        context.fill(this.centerX - 2, 28, this.centerX + this.centerW + 2, this.previewBottom + 2, 0x80000000);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        refreshState();
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Editor de NPC"), this.width / 2, 10, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Skins").formatted(Formatting.GOLD), this.leftX, 22, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Emotes").formatted(Formatting.GOLD), this.rightX, 22, 0xFFFFFF);

        int pageY = this.listTop + this.rows * 22 + 2;
        String skinPages = (this.skinPage + 1) + "/" + pageCount(this.skinView);
        String emotePages = (this.emotePage + 1) + "/" + pageCount(this.emoteView);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(skinPages), this.leftX + this.panelW / 2, pageY + 5, 0xAAAAAA);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(emotePages), this.rightX + this.panelW / 2, pageY + 5, 0xAAAAAA);

        // ayudas cuando las carpetas estan vacias
        if (this.skins.size() <= 1) {
            context.drawTextWithShadow(this.textRenderer, Text.literal("Pone PNG 64x64 en"), this.leftX, pageY + 50, 0xFFAA55);
            context.drawTextWithShadow(this.textRenderer, Text.literal("config/annisnpc/skins"), this.leftX, pageY + 61, 0xFFAA55);
        }

        AnnisNpcEntity npc = npc();
        if (npc != null) {
            int size = Math.max(30, (this.previewBottom - this.previewTop) / 2 - 8);
            InventoryScreen.drawEntity(context, this.centerX, this.previewTop, this.centerX + this.centerW, this.previewBottom,
                    size, 0.0625f, mouseX, mouseY, npc);
        } else {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Cargando NPC..."),
                    this.centerX + this.centerW / 2, (this.previewTop + this.previewBottom) / 2, 0xAAAAAA);
        }

        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Clic en una skin la aplica. Clic en un emote o actividad (★) lo reproduce."),
                this.width / 2, this.height - 14, 0x999999);
    }
}
