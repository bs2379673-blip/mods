package com.annis.npc.client;

import com.annis.npc.entity.AnnisNpcEntity;
import com.annis.npc.net.NpcActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

/**
 * Editor de accesorios: pegar objetos o modelos 3D (los de ejemplo del mod o los tuyos por resource pack)
 * a la cabeza, el torso, los brazos o las piernas, y ajustarlos con vista previa en vivo.
 * Shift + clic en +/- mueve en pasos grandes.
 */
public class NpcAccessoryScreen extends Screen {
    private final int npcId;
    private final List<Accessory> working = new ArrayList<>();
    private final List<ButtonWidget> listButtons = new ArrayList<>();
    private boolean loaded;
    private int selected = -1;
    private int age;

    private int leftW;
    private int rightX;
    private int rightW;
    private ButtonWidget boneButton;
    private ButtonWidget outfitButton;
    private int outfitIndex;

    public NpcAccessoryScreen(int npcId) {
        super(Text.literal("Accesorios"));
        this.npcId = npcId;
    }

    private AnnisNpcEntity npc() {
        if (MinecraftClient.getInstance().world == null) {
            return null;
        }
        Entity e = MinecraftClient.getInstance().world.getEntityById(this.npcId);
        return e instanceof AnnisNpcEntity n && !n.isRemoved() ? n : null;
    }

    private void sendIndex(int index) {
        String entry = index >= 0 && index < this.working.size() ? this.working.get(index).serialize() : "";
        ClientPlayNetworking.send(new NpcActionPayload(this.npcId, NpcActionPayload.SET_ACCESSORY, entry, false, index));
    }

    private Accessory sel() {
        return this.selected >= 0 && this.selected < this.working.size() ? this.working.get(this.selected) : null;
    }

    private static String handItemId(ItemStack stack) {
        return Registries.ITEM.getId(stack.getItem()).toString();
    }

    private static int handCmd(ItemStack stack) {
        CustomModelDataComponent c = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
        return c == null ? 0 : c.value();
    }

    private ItemStack hand() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player == null ? ItemStack.EMPTY : mc.player.getMainHandStack();
    }

    private void tell(String msg) {
        if (MinecraftClient.getInstance().player != null) {
            MinecraftClient.getInstance().player.sendMessage(Text.literal(msg), true);
        }
    }

    @Override
    protected void init() {
        this.leftW = Math.max(120, Math.min(150, this.width / 4));
        this.rightW = Math.max(150, Math.min(180, this.width / 3));
        this.rightX = this.width - 8 - this.rightW;
        this.listButtons.clear();

        // --- izquierda: lista ---
        this.addDrawableChild(ButtonWidget.builder(Text.literal("+ Anadir objeto de mi mano"), b -> {
            ItemStack stack = hand();
            if (stack.isEmpty()) {
                tell("Ten en la mano el objeto que quieres usar como accesorio");
                return;
            }
            if (this.working.size() >= AnnisNpcEntity.MAX_ACCESSORIES) {
                tell("Maximo de accesorios alcanzado");
                return;
            }
            this.working.add(Accessory.defaultFor("head", handItemId(stack), handCmd(stack)));
            this.selected = this.working.size() - 1;
            sendIndex(-1);
        }).dimensions(8, 30, this.leftW, 20).build());

        int rows = Math.max(3, Math.min(AnnisNpcEntity.MAX_ACCESSORIES, (this.height - 160) / 20));
        for (int i = 0; i < rows; i++) {
            final int row = i;
            this.listButtons.add(this.addDrawableChild(ButtonWidget.builder(Text.empty(), b -> this.selected = row)
                    .dimensions(8, 56 + i * 20, this.leftW, 18).build()));
        }
        this.outfitButton = ButtonWidget.builder(Text.empty(), b -> this.outfitIndex = (this.outfitIndex + 1) % Outfits.ALL.size())
                .dimensions(8, this.height - 100, this.leftW, 20).build();
        this.addDrawableChild(this.outfitButton);
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Aplicar conjunto").formatted(Formatting.GREEN), b -> {
            Outfits.Outfit o = Outfits.ALL.get(this.outfitIndex);
            ClientPlayNetworking.send(new NpcActionPayload(this.npcId, NpcActionPayload.SET_ALL_ACCESSORIES, o.accessories(), false, 0));
            ClientPlayNetworking.send(new NpcActionPayload(this.npcId, NpcActionPayload.PLAY_EMOTE, o.activity(), false, 3600));
            this.working.clear();
            for (Accessory a : Accessory.parseAll(o.accessories())) {
                this.working.add(a.copy());
            }
            this.selected = this.working.isEmpty() ? -1 : 0;
        }).dimensions(8, this.height - 78, this.leftW, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Quitar seleccionado").formatted(Formatting.RED), b -> {
            if (sel() != null) {
                int idx = this.selected;
                this.working.remove(idx);
                ClientPlayNetworking.send(new NpcActionPayload(this.npcId, NpcActionPayload.SET_ACCESSORY, "", false, idx));
                this.selected = Math.min(idx, this.working.size() - 1);
            }
        }).dimensions(8, this.height - 52, this.leftW, 20).build());

        // --- derecha: ajustes del seleccionado ---
        int x = this.rightX;
        this.boneButton = ButtonWidget.builder(Text.empty(), b -> edit(a -> {
            int i = (Accessory.boneIndex(a.bone) + 1) % Accessory.BONES.length;
            Accessory d = Accessory.defaultFor(Accessory.BONES[i], a.item, a.cmd);
            a.bone = d.bone;
            a.x = d.x;
            a.y = d.y;
            a.z = d.z;
            a.rx = d.rx;
            a.ry = d.ry;
            a.rz = d.rz;
            a.scale = d.scale;
        })).dimensions(x, 30, this.rightW, 20).build();
        this.addDrawableChild(this.boneButton);
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Usar el objeto de mi mano"), b -> {
            ItemStack stack = hand();
            if (stack.isEmpty()) {
                tell("Ten el objeto en la mano");
                return;
            }
            edit(a -> {
                a.item = handItemId(stack);
                a.cmd = handCmd(stack);
            });
        }).dimensions(x, 52, this.rightW, 20).build());

        row(x, 80, (a, d) -> a.x += d * 0.5f);
        row(x, 100, (a, d) -> a.y += d * 0.5f);
        row(x, 120, (a, d) -> a.z += d * 0.5f);
        row(x, 146, (a, d) -> a.rx = wrap(a.rx + d * 15f));
        row(x, 166, (a, d) -> a.ry = wrap(a.ry + d * 15f));
        row(x, 186, (a, d) -> a.rz = wrap(a.rz + d * 15f));
        row(x, 212, (a, d) -> a.scale = Math.max(0.05f, Math.min(4.0f, a.scale + d * 0.05f)));

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Volver"), b -> this.close())
                .dimensions(this.width / 2 - 50, this.height - 28, 100, 20).build());
    }

    private static float wrap(float deg) {
        float d = deg % 360f;
        if (d > 180f) {
            d -= 360f;
        }
        if (d < -180f) {
            d += 360f;
        }
        return d;
    }

    private void row(int x, int y, BiConsumer<Accessory, Float> change) {
        this.addDrawableChild(ButtonWidget.builder(Text.literal("-"), b ->
                edit(a -> change.accept(a, hasShiftDown() ? -4f : -1f))).dimensions(x + this.rightW - 44, y, 20, 18).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("+"), b ->
                edit(a -> change.accept(a, hasShiftDown() ? 4f : 1f))).dimensions(x + this.rightW - 22, y, 20, 18).build());
    }

    private void edit(java.util.function.Consumer<Accessory> change) {
        Accessory a = sel();
        if (a == null) {
            return;
        }
        change.accept(a);
        sendIndex(this.selected);
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

    private static String itemName(Accessory a) {
        String id = a.item.startsWith("minecraft:") ? a.item.substring(10) : a.item;
        if (id.startsWith("annisnpc:accesorio_")) {
            id = id.substring(19);
        }
        return a.cmd != 0 ? id + " #" + a.cmd : id;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        AnnisNpcEntity npc = npc();
        if (npc != null && !this.loaded) {
            this.loaded = true;
            this.working.clear();
            for (Accessory a : Accessory.parseAll(npc.getAccessories())) {
                this.working.add(a.copy());
            }
            this.selected = this.working.isEmpty() ? -1 : 0;
        }
        for (int i = 0; i < this.listButtons.size(); i++) {
            ButtonWidget b = this.listButtons.get(i);
            if (i < this.working.size()) {
                Accessory a = this.working.get(i);
                String label = (i + 1) + ". " + itemName(a) + " (" + Accessory.BONE_NAMES[Accessory.boneIndex(a.bone)] + ")";
                label = this.textRenderer.trimToWidth(label, this.leftW - 10);
                b.setMessage(i == this.selected ? Text.literal(label).formatted(Formatting.YELLOW) : Text.literal(label));
                b.visible = true;
            } else {
                b.visible = false;
            }
        }
        this.outfitButton.setMessage(Text.literal("Conjunto: " + Outfits.ALL.get(this.outfitIndex).name()));
        Accessory a = sel();
        this.boneButton.setMessage(Text.literal("Hueso: " + (a == null ? "-" : Accessory.BONE_NAMES[Accessory.boneIndex(a.bone)])));

        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Accesorios"), this.width / 2, 10, 0xFFFFFF);
        int x = this.rightX;
        if (a != null) {
            String[] labels = {"Pos X", "Pos Y", "Pos Z", "Rot X", "Rot Y", "Rot Z", "Escala"};
            float[] values = {a.x, a.y, a.z, a.rx, a.ry, a.rz, a.scale};
            int[] ys = {80, 100, 120, 146, 166, 186, 212};
            for (int i = 0; i < labels.length; i++) {
                String v = i < 3 ? String.format(Locale.ROOT, "%.1f px", values[i])
                        : i < 6 ? String.format(Locale.ROOT, "%.0f°", values[i]) : String.format(Locale.ROOT, "x%.2f", values[i]);
                context.drawTextWithShadow(this.textRenderer, Text.literal(labels[i]).formatted(Formatting.GOLD), x, ys[i] + 5, 0xFFFFFF);
                context.drawTextWithShadow(this.textRenderer, Text.literal(v), x + 44, ys[i] + 5, 0xFFFFFF);
            }
            context.drawTextWithShadow(this.textRenderer, Text.literal("Shift + clic: pasos grandes").formatted(Formatting.GRAY), x, 236, 0xFFFFFF);
        } else {
            context.drawTextWithShadow(this.textRenderer, Text.literal("Anade un accesorio con el").formatted(Formatting.GRAY), x, 84, 0xFFFFFF);
            context.drawTextWithShadow(this.textRenderer, Text.literal("objeto en la mano.").formatted(Formatting.GRAY), x, 95, 0xFFFFFF);
        }

        int cx1 = 8 + this.leftW + 8;
        int cx2 = this.rightX - 8;
        if (npc != null && cx2 - cx1 > 40) {
            int size = Math.max(30, Math.min((cx2 - cx1) / 2, (this.height - 80) / 3));
            InventoryScreen.drawEntity(context, cx1, 30, cx2, this.height - 34, size, 0.0625f, mouseX, mouseY, npc);
        }
    }
}
