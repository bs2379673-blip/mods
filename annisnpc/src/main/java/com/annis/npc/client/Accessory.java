package com.annis.npc.client;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Un accesorio pegado a un hueso: "hueso|objeto|customModelData|x|y|z|rotX|rotY|rotZ|escala".
 * x/y/z en pixeles del modelo, rotaciones en grados.
 */
public final class Accessory {
    public static final String[] BONES = {"head", "body", "right_arm", "left_arm", "right_leg", "left_leg"};
    public static final String[] BONE_NAMES = {"Cabeza", "Torso", "Brazo derecho", "Brazo izquierdo", "Pierna derecha", "Pierna izquierda"};

    public String bone = "head";
    public String item = "minecraft:air";
    public int cmd = 0;
    public float x;
    public float y;
    public float z;
    public float rx;
    public float ry;
    public float rz;
    public float scale = 0.6f;

    private static final Map<String, List<Accessory>> PARSED = new HashMap<>();
    private static final Map<String, ItemStack> STACKS = new HashMap<>();

    public Accessory copy() {
        Accessory a = new Accessory();
        a.bone = bone;
        a.item = item;
        a.cmd = cmd;
        a.x = x;
        a.y = y;
        a.z = z;
        a.rx = rx;
        a.ry = ry;
        a.rz = rz;
        a.scale = scale;
        return a;
    }

    /** Posicion inicial razonable segun el hueso. */
    public static Accessory defaultFor(String bone, String item, int cmd) {
        Accessory a = new Accessory();
        a.bone = bone;
        a.item = item;
        a.cmd = cmd;
        switch (bone) {
            case "head" -> {
                a.y = -10.0f;
                a.scale = 0.62f;
            }
            case "body" -> {
                a.y = 7.0f;
                a.z = -4.0f;
                a.scale = 0.55f;
            }
            case "right_arm" -> {
                a.x = -1.0f;
                a.y = 10.0f;
                a.z = -1.0f;
                a.rx = -90.0f;
                a.scale = 0.5f;
            }
            case "left_arm" -> {
                a.x = 1.0f;
                a.y = 10.0f;
                a.z = -1.0f;
                a.rx = -90.0f;
                a.scale = 0.5f;
            }
            default -> {
                a.y = 6.0f;
                a.scale = 0.5f;
            }
        }
        return a;
    }

    private static String f(float v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    public String serialize() {
        return bone + "|" + item + "|" + cmd + "|" + f(x) + "|" + f(y) + "|" + f(z) + "|" + f(rx) + "|" + f(ry) + "|" + f(rz) + "|" + f(scale);
    }

    public static Accessory parse(String raw) {
        String[] p = raw.split("\\|");
        if (p.length < 10) {
            return null;
        }
        try {
            Accessory a = new Accessory();
            a.bone = p[0];
            a.item = p[1];
            a.cmd = Integer.parseInt(p[2]);
            a.x = Float.parseFloat(p[3]);
            a.y = Float.parseFloat(p[4]);
            a.z = Float.parseFloat(p[5]);
            a.rx = Float.parseFloat(p[6]);
            a.ry = Float.parseFloat(p[7]);
            a.rz = Float.parseFloat(p[8]);
            a.scale = Math.max(0.05f, Math.min(4.0f, Float.parseFloat(p[9])));
            return a;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static List<Accessory> parseAll(String raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<Accessory> cached = PARSED.get(raw);
        if (cached != null) {
            return cached;
        }
        List<Accessory> out = new ArrayList<>();
        for (String e : raw.split(";")) {
            Accessory a = parse(e);
            if (a != null) {
                out.add(a);
            }
        }
        if (PARSED.size() > 256) {
            PARSED.clear();
        }
        PARSED.put(raw, out);
        return out;
    }

    public ItemStack stack() {
        String key = item + "#" + cmd;
        ItemStack s = STACKS.get(key);
        if (s == null) {
            Identifier id = Identifier.tryParse(item);
            s = id == null ? ItemStack.EMPTY : new ItemStack(Registries.ITEM.get(id));
            if (cmd != 0 && !s.isEmpty()) {
                s.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(cmd));
            }
            STACKS.put(key, s);
        }
        return s;
    }

    public static int boneIndex(String bone) {
        for (int i = 0; i < BONES.length; i++) {
            if (BONES[i].equals(bone)) {
                return i;
            }
        }
        return 0;
    }
}
