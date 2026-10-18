package com.tunombre.concierto.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * stickers y GIF colgados del cielo. Cada uno se ubica con un "casillero" respecto al eje del escenario (azimut y altura),
 * asi que el conjunto siempre queda simetrico y ordenado, y no se mueve cuando caminas (como un cielo).
 */
public final class Sprites {
    private record Meta(int cols, int rows, int frames, float fps) { }

    /** casillero: azimut (grados a la derecha del publico mirando al escenario), altura, tamano angular */
    private record Slot(float az, float el, float size) { }

    private static final Map<String, Slot> SLOTS = new HashMap<>();
    static {
        SLOTS.put("C", new Slot(0, 38, 30));      // centro, bien visible
        SLOTS.put("T", new Slot(0, 64, 26));      // arriba de todo (titulos)
        SLOTS.put("B", new Slot(0, 20, 38));      // bajo, detras del escenario
        SLOTS.put("H", new Slot(0, 52, 20));      // centro alto, chico
        SLOTS.put("L1", new Slot(-34, 34, 20));
        SLOTS.put("R1", new Slot(34, 34, 20));
        SLOTS.put("L2", new Slot(-64, 27, 16));
        SLOTS.put("R2", new Slot(64, 27, 16));
        SLOTS.put("L3", new Slot(-96, 22, 15));
        SLOTS.put("R3", new Slot(96, 22, 15));
        SLOTS.put("LH", new Slot(-20, 56, 13));
        SLOTS.put("RH", new Slot(20, 56, 13));
        SLOTS.put("LU", new Slot(-46, 52, 12));
        SLOTS.put("RU", new Slot(46, 52, 12));
        SLOTS.put("X", new Slot(180, 30, 30));      // detras del publico
        SLOTS.put("XL", new Slot(-150, 28, 18));
        SLOTS.put("XR", new Slot(150, 28, 18));
    }

    private static Map<String, Meta> meta;

    private static final class Inst {
        String id;
        Meta m;
        float az, el, size, t0, dur, spin, grow = 1F, pulse, rot0, bob = 1F, phase;
        boolean flip, add;
        float[] tint = {1F, 1F, 1F};
        ResourceLocation tex;
    }

    private final List<Inst> list = new ArrayList<>();

    public boolean isEmpty() { return list.isEmpty(); }

    public void clear() { list.clear(); }

    private static synchronized void loadMeta() {
        if (meta != null) return;
        meta = new HashMap<>();
        try {
            var res = Minecraft.getInstance().getResourceManager().getResource(new ResourceLocation("concierto", "sprites.json"));
            if (res.isEmpty()) return;
            try (InputStream in = res.get().open()) {
                JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (var e : o.entrySet()) {
                    JsonObject m = e.getValue().getAsJsonObject();
                    meta.put(e.getKey(), new Meta(m.get("cols").getAsInt(), m.get("rows").getAsInt(), m.get("frames").getAsInt(), m.get("fps").getAsFloat()));
                }
            }
        } catch (Exception ignored) { }
    }

    private static boolean glowy(String id) {
        return id.startsWith("s_ring") || id.startsWith("s_sparkle") || id.startsWith("s_light") || id.startsWith("s_star") || id.startsWith("s_bolt");
    }

    /** cue: id, slot, dur, size?, spin?, grow?, pulse?, tint?, add?, flip?, bob? */
    public void spawn(JsonObject c, float songT) {
        loadMeta();
        String id = c.get("id").getAsString();
        Meta m = meta.get(id);
        if (m == null) return;
        String slot = c.has("slot") ? c.get("slot").getAsString() : "C";
        boolean pair = slot.startsWith("LR");
        List<String> names = new ArrayList<>();
        if (pair) { names.add("L" + slot.substring(2)); names.add("R" + slot.substring(2)); } else names.add(slot);
        for (String n : names) {
            Slot s = SLOTS.get(n);
            if (s == null) continue;
            Inst i = new Inst();
            i.id = id;
            i.m = m;
            i.az = s.az;
            i.el = s.el;
            i.size = c.has("size") ? c.get("size").getAsFloat() : s.size;
            i.t0 = songT;
            i.dur = c.has("dur") ? c.get("dur").getAsFloat() : 6F;
            i.spin = c.has("spin") ? c.get("spin").getAsFloat() : 0F;
            i.grow = c.has("grow") ? c.get("grow").getAsFloat() : 1F;
            i.pulse = c.has("pulse") ? c.get("pulse").getAsFloat() : 0.10F;
            i.bob = c.has("bob") ? c.get("bob").getAsFloat() : 1F;
            i.add = c.has("add") ? c.get("add").getAsBoolean() : glowy(id);
            if (c.has("tint")) i.tint = Script.hex(c.get("tint").getAsString());
            // los pares se espejan; los textos nunca (se leerian al reves)
            boolean text = id.startsWith("t_") || id.startsWith("u_mesmerizer") || id.startsWith("u3_");
            i.flip = c.has("flip") ? c.get("flip").getAsBoolean() : (pair && n.startsWith("L") && !text);
            i.phase = (i.az * 0.07F) + (i.el * 0.03F);
            i.tex = new ResourceLocation("concierto", "textures/spr/" + id + ".png");
            list.add(i);
        }
    }

    private static final float R = 72F;
    private static final float FADE_IN = 0.45F, FADE_OUT = 0.7F;

    private static void put(BufferBuilder bb, double x, double y, double z, float r, float g, float b, float a, float u, float v) {
        bb.vertex(x, y, z).color(r, g, b, a).uv(u, v).endVertex();
    }

    /** dibuja todos. Las coordenadas son relativas a la camara: el cielo esta a distancia fija. */
    public void draw(ShaderInstance fx, ShaderInstance fxAdd, float sdx, float sdz, float songT, float kick, float alpha) {
        if (list.isEmpty()) return;
        list.removeIf(i -> songT > i.t0 + i.dur + FADE_OUT);
        for (int pass = 0; pass < 2; pass++) {
            boolean addPass = pass == 0; // primero los brillos aditivos (detras) y encima los stickers opacos, asi nada los lava a blanco
            ShaderInstance sh = addPass ? fxAdd : fx;
            boolean any = false;
            for (Inst i : list) if (i.add == addPass) { any = true; break; }
            if (!any) continue;
            RenderSystem.setShader(() -> sh);
            sh.safeGetUniform("Kind").set(5F);
            for (Inst i : list) {
                if (i.add != addPass) continue;
                float age = songT - i.t0;
                if (age < 0F) continue;
                float aIn = Math.min(1F, age / FADE_IN), aOut = Math.min(1F, Math.max(0F, (i.t0 + i.dur + FADE_OUT - songT) / FADE_OUT));
                float a = aIn * aOut * alpha;
                if (a <= 0.003F) continue;
                // aparece con un pequeno rebote
                float pop = aIn < 1F ? (float) (1F - Math.pow(1F - aIn, 3) * Math.cos(aIn * 5.0)) * 0.35F + 0.65F * aIn : 1F;
                float growK = i.grow >= 1F ? 1F : i.grow + (1F - i.grow) * Math.min(1F, age / Math.max(0.5F, i.dur));
                float scale = pop * growK * (1F + i.pulse * kick);
                float el = (float) Math.toRadians(i.el + (age < 0 ? 0 : 1.1F * i.bob * Math.sin(songT * 1.3F + i.phase)));
                float az = (float) Math.toRadians(i.az);
                // direccion: base (sdx,sdz) girada az hacia la derecha del publico
                float rxv = -sdz, rzv = sdx;
                float hx = sdx * (float) Math.cos(az) + rxv * (float) Math.sin(az), hz = sdz * (float) Math.cos(az) + rzv * (float) Math.sin(az);
                double ce = Math.cos(el), se = Math.sin(el);
                double dx = hx * ce, dy = se, dz = hz * ce;
                // base de la lamina: derecha y arriba vistos desde la camara
                double ux = 0, uy = 1, uz = 0;
                double rx = dy * uz - dz * uy, ry = dz * ux - dx * uz, rz = dx * uy - dy * ux;
                double rl = Math.sqrt(rx * rx + ry * ry + rz * rz);
                rx /= rl; ry /= rl; rz /= rl;
                double vx = ry * dz - rz * dy, vy = rz * dx - rx * dz, vz = rx * dy - ry * dx;
                float rot = (float) Math.toRadians(i.rot0 + i.spin * age);
                double cr = Math.cos(rot), sr = Math.sin(rot);
                double hs = R * Math.tan(Math.toRadians(i.size * scale * 0.5));
                double ax = (rx * cr + vx * sr) * hs, ay = (ry * cr + vy * sr) * hs, az2 = (rz * cr + vz * sr) * hs;
                double bx = (-rx * sr + vx * cr) * hs, by = (-ry * sr + vy * cr) * hs, bz = (-rz * sr + vz * cr) * hs;
                double cx = dx * R, cy = dy * R, cz = dz * R;
                // cuadro de la hoja
                int f = 0;
                if (i.m.frames() > 1 && i.m.fps() > 0F) f = (int) Math.floor(age * i.m.fps()) % i.m.frames();
                int col = f % i.m.cols(), row = f / i.m.cols();
                float texel = i.m.frames() > 1 ? 128F : 256F;
                float eu = 0.6F / (texel * i.m.cols()), ev = 0.6F / (texel * i.m.rows());
                float u0 = (float) col / i.m.cols() + eu, u1 = (float) (col + 1) / i.m.cols() - eu;
                float v0 = (float) row / i.m.rows() + ev, v1 = (float) (row + 1) / i.m.rows() - ev;
                if (i.flip) { float t = u0; u0 = u1; u1 = t; }
                RenderSystem.setShaderTexture(0, i.tex);
                sh.safeGetUniform("Alpha").set(1F);
                BufferBuilder bb = Tesselator.getInstance().getBuilder();
                bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
                put(bb, cx - ax - bx, cy - ay - by, cz - az2 - bz, i.tint[0], i.tint[1], i.tint[2], a, u0, v1);
                put(bb, cx + ax - bx, cy + ay - by, cz + az2 - bz, i.tint[0], i.tint[1], i.tint[2], a, u1, v1);
                put(bb, cx + ax + bx, cy + ay + by, cz + az2 + bz, i.tint[0], i.tint[1], i.tint[2], a, u1, v0);
                put(bb, cx - ax + bx, cy - ay + by, cz - az2 + bz, i.tint[0], i.tint[1], i.tint[2], a, u0, v0);
                BufferUploader.drawWithShader(bb.end());
            }
        }
    }
}
