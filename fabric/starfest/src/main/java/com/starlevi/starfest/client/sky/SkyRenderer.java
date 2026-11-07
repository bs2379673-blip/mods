package com.starlevi.starfest.client.sky;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.starlevi.starfest.client.Beat;
import com.starlevi.starfest.client.Palette;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Dibuja un cielo de StarFest sobre el cielo del juego, capa por capa. Solo usa los programas de
 * dibujo del propio Minecraft (posicion + color, posicion + textura + color): nada de shaders propios,
 * asi se ve en cualquier placa de video, en Mac, y con Iris y paquetes de shaders. El cielo es una
 * cupula grande alrededor de la camara, mas lejos que el terreno que se ve: el terreno queda adelante.
 */
public final class SkyRenderer {
    private static float R = 100.0f;
    /** Estado de las capas que lo necesitan (anillos que salen con el bombo). */
    private static final Map<SkyDef.Layer, List<float[]>> RINGS = new IdentityHashMap<>();
    private static final Map<SkyDef.Layer, Float> LAST_REACT = new IdentityHashMap<>();

    private static RenderPipeline pipe(String name, boolean textured, boolean additive) {
        RenderPipeline.Builder b = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("starfest", "pipeline/" + name))
                .withVertexShader(textured ? "core/position_tex_color" : "core/position_color")
                .withFragmentShader(textured ? "core/position_tex_color" : "core/position_color")
                .withBlend(additive ? BlendFunction.LIGHTNING : BlendFunction.TRANSLUCENT)
                .withDepthWrite(false)
                .withCull(false)
                .withVertexFormat(textured ? DefaultVertexFormat.POSITION_TEX_COLOR : DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS);
        if (textured) {
            b = b.withSampler("Sampler0");
        }
        return b.build();
    }

    private static final RenderPipeline COLOR = pipe("cielo_color", false, false), COLOR_ADD = pipe("cielo_color_suma", false, true),
            TEX = pipe("cielo_textura", true, false), TEX_ADD = pipe("cielo_textura_suma", true, true);
    static {
        com.starlevi.starfest.client.IrisCompat.assign(COLOR, "SKY_BASIC");
        com.starlevi.starfest.client.IrisCompat.assign(COLOR_ADD, "SKY_BASIC");
        com.starlevi.starfest.client.IrisCompat.assign(TEX, "SKY_TEXTURED");
        com.starlevi.starfest.client.IrisCompat.assign(TEX_ADD, "SKY_TEXTURED");
    }

    private static final RenderType COLOR_T = RenderType.create("starfest_cielo_color", RenderSetup.builder(COLOR).bufferSize(1 << 18).createRenderSetup());
    private static final RenderType COLOR_ADD_T = RenderType.create("starfest_cielo_color_suma", RenderSetup.builder(COLOR_ADD).bufferSize(1 << 18).createRenderSetup());
    private static final Map<Identifier, RenderType[]> TEXTURED = new HashMap<>();

    private static RenderType type(Batch b) {
        if (!b.textured || b.tex == null) {
            return b.additive ? COLOR_ADD_T : COLOR_T;
        }
        RenderType[] t = TEXTURED.computeIfAbsent(b.tex, id -> new RenderType[]{
                RenderType.create("starfest_cielo_" + id.getPath().replace('/', '_'), RenderSetup.builder(TEX).withTexture("Sampler0", id).bufferSize(1 << 18).createRenderSetup()),
                RenderType.create("starfest_cielo_suma_" + id.getPath().replace('/', '_'), RenderSetup.builder(TEX_ADD).withTexture("Sampler0", id).bufferSize(1 << 18).createRenderSetup())});
        return t[b.additive ? 1 : 0];
    }

    /** Lo que antes era un Batch: los vertices de una tanda, con su textura y su mezcla. */
    static final class Batch {
        final boolean textured, additive;
        Identifier tex;
        float[] data = new float[9 * 256];
        int n;

        Batch(boolean textured, boolean additive) {
            this.textured = textured;
            this.additive = additive;
        }

        void add(float x, float y, float z, float u, float v, float r, float g, float b, float a) {
            if ((this.n + 1) * 9 > this.data.length) {
                this.data = java.util.Arrays.copyOf(this.data, this.data.length * 2);
            }
            int o = this.n * 9;
            this.data[o] = x;
            this.data[o + 1] = y;
            this.data[o + 2] = z;
            this.data[o + 3] = u;
            this.data[o + 4] = v;
            this.data[o + 5] = r;
            this.data[o + 6] = g;
            this.data[o + 7] = b;
            this.data[o + 8] = a;
            this.n++;
        }
    }

    private static final List<Batch> OUT = new ArrayList<>();
    private static boolean additiveNow;
    private static Identifier texNow;

    private SkyRenderer() {
    }

    public static void render(SubmitNodeCollector collector, PoseStack ps, float radius, SkyDef def, Beat b, float weight,
                              Function<String, SkyImages.Img> images) {
        R = radius;
        float t = (System.nanoTime() % 3_600_000_000_000L) / 1.0e9f;
        Matrix4f m = new Matrix4f();
        OUT.clear();
        additiveNow = false;
        background(m, def, weight);
        for (SkyDef.Layer l : def.layers) {
            float s = l.strength(b) * weight;
            if (s <= 0.003f) {
                if (!l.type.equals("anillos")) {
                    continue;
                }
            }
            additiveNow = l.additive;
            try {
                switch (l.type) {
                    case "panorama" -> panorama(m, l, b, s, t, images);
                    case "estrellas" -> stars(m, l, b, s, t);
                    case "imagen" -> image(m, l, b, s, t, images);
                    case "aurora" -> aurora(m, l, b, s, t);
                    case "espectro" -> spectrum(m, l, b, s, t);
                    case "rayos" -> rays(m, l, b, s, t);
                    case "anillos" -> rings(m, l, b, weight, t);
                    case "tunel" -> tunnel(m, l, b, s, t);
                    case "caleidoscopio" -> kaleido(m, l, b, s, t, images);
                    case "destello" -> flash(m, l, b, weight);
                    default -> {
                    }
                }
            } catch (Exception ignored) {
                // una capa mal escrita no debe romper el resto del cielo
            }
        }
        // cada tanda en orden (el orden de las capas importa con transparencias)
        for (Batch bt : new ArrayList<>(OUT)) {
            if (bt.n == 0) {
                continue;
            }
            final Batch fb = bt;
            collector.submitCustomGeometry(ps, type(bt), (pose, vc) -> {
                float[] d = fb.data;
                for (int i = 0; i < fb.n; i++) {
                    int o = i * 9;
                    if (fb.textured) {
                        vc.addVertex(pose, d[o], d[o + 1], d[o + 2]).setUv(d[o + 3], d[o + 4]).setColor(d[o + 5], d[o + 6], d[o + 7], d[o + 8]);
                    } else {
                        vc.addVertex(pose, d[o], d[o + 1], d[o + 2]).setColor(d[o + 5], d[o + 6], d[o + 7], d[o + 8]);
                    }
                }
            });
        }
        OUT.clear();
    }

    // ------------------------------------------------------------------ geometria

    /** Direccion: azimut (0 = norte, 90 = este) y altura sobre el horizonte, en grados. */
    static Vector3f dir(float az, float el) {
        float a = az * Mth.DEG_TO_RAD, e = el * Mth.DEG_TO_RAD;
        return new Vector3f(Mth.sin(a) * Mth.cos(e), Mth.sin(e), -Mth.cos(a) * Mth.cos(e));
    }

    /** Ejes perpendiculares a una direccion (derecha, arriba). */
    static Vector3f[] basis(Vector3f d) {
        Vector3f ref = Math.abs(d.y) > 0.95f ? new Vector3f(0, 0, -1) : new Vector3f(0, 1, 0);
        Vector3f right = new Vector3f(d).cross(ref).normalize();
        Vector3f up = new Vector3f(right).cross(d).normalize();
        return new Vector3f[]{right, up};
    }

    /** Punto de la esfera a "a" grados de la direccion d, girando "phi" grados alrededor de ella. */
    static Vector3f around(Vector3f d, Vector3f[] b, float aDeg, float phiDeg) {
        float a = aDeg * Mth.DEG_TO_RAD, p = phiDeg * Mth.DEG_TO_RAD;
        float sa = Mth.sin(a), ca = Mth.cos(a);
        return new Vector3f(d).mul(ca).add(new Vector3f(b[0]).mul(sa * Mth.cos(p))).add(new Vector3f(b[1]).mul(sa * Mth.sin(p))).mul(R);
    }

    private static Batch begin(boolean textured) {
        return new Batch(textured, additiveNow);
    }

    private static void end(Batch bb) {
        bb.tex = texNow;
        OUT.add(bb);
    }

    private static void tex(Identifier id) {
        texNow = id;
    }

    private static final Vector3f TMP = new Vector3f();

    private static void v(Batch bb, Matrix4f m, Vector3f p, float u, float vv, int rgb, float a) {
        m.transformPosition(p.x, p.y, p.z, TMP);
        bb.add(TMP.x, TMP.y, TMP.z, u, vv, Palette.r(rgb), Palette.g(rgb), Palette.b(rgb), Math.max(0, Math.min(1, a)));
    }

    private static void c(Batch bb, Matrix4f m, Vector3f p, int rgb, float a) {
        m.transformPosition(p.x, p.y, p.z, TMP);
        bb.add(TMP.x, TMP.y, TMP.z, 0, 0, Palette.r(rgb), Palette.g(rgb), Palette.b(rgb), Math.max(0, Math.min(1, a)));
    }

    /** Cuadro mirando al centro, en la direccion d, de "size" grados, girado "rot" grados. */
    private static void sprite(Batch bb, Matrix4f m, Vector3f d, float sizeDeg, float aspect, float rot, int rgb, float a) {
        Vector3f[] b = basis(d);
        float h = R * (float) Math.tan(Math.min(80, sizeDeg) * 0.5f * Mth.DEG_TO_RAD);
        float w = h * aspect;
        float cr = Mth.cos(rot * Mth.DEG_TO_RAD), sr = Mth.sin(rot * Mth.DEG_TO_RAD);
        Vector3f center = new Vector3f(d).mul(R);
        float[][] k = {{-1, -1, 0, 1}, {1, -1, 1, 1}, {1, 1, 1, 0}, {-1, 1, 0, 0}};
        for (float[] q : k) {
            float x = q[0] * w, y = q[1] * h;
            float rx = x * cr - y * sr, ry = x * sr + y * cr;
            Vector3f p = new Vector3f(center).add(new Vector3f(b[0]).mul(rx)).add(new Vector3f(b[1]).mul(ry));
            v(bb, m, p, q[2], q[3], rgb, a);
        }
    }

    // ------------------------------------------------------------------ fondo

    private static void background(Matrix4f m, SkyDef def, float weight) {
        if (def.darken <= 0) {
            return;
        }
        Batch bb = begin(false);
        int lon = 24;
        float[] els = {-90, -20, -5, 0, 8, 25, 50, 90};
        for (int i = 0; i < els.length - 1; i++) {
            for (int j = 0; j < lon; j++) {
                float a0 = 360f * j / lon, a1 = 360f * (j + 1) / lon;
                float e0 = els[i], e1 = els[i + 1];
                int c0 = grad(def, e0), c1 = grad(def, e1);
                float al = def.darken * weight;
                c(bb, m, dir(a0, e0).mul(R), c0, al);
                c(bb, m, dir(a1, e0).mul(R), c0, al);
                c(bb, m, dir(a1, e1).mul(R), c1, al);
                c(bb, m, dir(a0, e1).mul(R), c1, al);
            }
        }
        end(bb);
    }

    private static int grad(SkyDef def, float el) {
        if (el <= 0) {
            return lerpRgb(def.horizon, def.bottom, Math.min(1, -el / 25f));
        }
        return lerpRgb(def.horizon, def.top, (float) Math.pow(Math.min(1, el / 70f), 0.7));
    }

    static int lerpRgb(int a, int b, float f) {
        int r = (int) Mth.lerp(f, a >> 16 & 0xFF, b >> 16 & 0xFF);
        int g = (int) Mth.lerp(f, a >> 8 & 0xFF, b >> 8 & 0xFF);
        int bl = (int) Mth.lerp(f, a & 0xFF, b & 0xFF);
        return r << 16 | g << 8 | bl;
    }

    // ------------------------------------------------------------------ capas

    private static SkyImages.Img img(SkyDef.Layer l, String def, Function<String, SkyImages.Img> images) {
        String file = l.str("archivo", null);
        if (file != null) {
            return images.apply(file.toLowerCase(java.util.Locale.ROOT));
        }
        return SkyImages.builtin(l.str("textura", def));
    }

    /** Imagen que envuelve todo el cielo (nebulosa, nubes...), girando despacio. */
    private static void panorama(Matrix4f m, SkyDef.Layer l, Beat b, float s, float t, Function<String, SkyImages.Img> images) {
        SkyImages.Img im = img(l, "nebulosa", images);
        if (im == null) {
            return;
        }
        tex(im.frame((long) (t * 1000)));
        int tint = l.color(0).get(b);
        float rot = t * l.num("giro", 1.5f) * l.speed;
        float tiltRad = l.num("inclinacion", 0) * Mth.DEG_TO_RAD;
        float pulse = 1 + l.num("latido", 0.15f) * l.react(b);
        Batch bb = begin(true);
        int lon = 48, lat = 24;
        Matrix4f mm = new Matrix4f(m).rotateX(tiltRad);
        for (int i = 0; i < lat; i++) {
            float e0 = -90 + 180f * i / lat, e1 = -90 + 180f * (i + 1) / lat;
            float v0 = 1 - (i / (float) lat), v1 = 1 - ((i + 1) / (float) lat);
            for (int j = 0; j < lon; j++) {
                float a0 = 360f * j / lon + rot, a1 = 360f * (j + 1) / lon + rot;
                float u0 = j / (float) lon, u1 = (j + 1) / (float) lon;
                float al = s * pulse;
                v(bb, mm, dir(a0, e0).mul(R), u0, v0, tint, al);
                v(bb, mm, dir(a1, e0).mul(R), u1, v0, tint, al);
                v(bb, mm, dir(a1, e1).mul(R), u1, v1, tint, al);
                v(bb, mm, dir(a0, e1).mul(R), u0, v1, tint, al);
            }
        }
        end(bb);
    }

    private static float hash(int i, int k) {
        int h = i * 374761393 + k * 668265263;
        h = (h ^ h >>> 13) * 1274126177;
        return ((h ^ h >>> 16) & 0xFFFFFF) / (float) 0xFFFFFF;
    }

    /** Estrellas con destello que titilan (mas con los agudos). */
    private static void stars(Matrix4f m, SkyDef.Layer l, Beat b, float s, float t) {
        tex(SkyImages.builtin("estrella").frame(0));
        int n = Math.min(4000, l.integer("cantidad", 900));
        float size = l.num("tamano", 1.4f);
        int seed = l.integer("semilla", 7);
        float spin = t * l.num("giro", 0.4f) * l.speed;
        float hi = b.playing() ? b.high() : 0;
        Batch bb = begin(true);
        for (int i = 0; i < n; i++) {
            float az = hash(i, seed) * 360 + spin;
            float el = (float) Math.toDegrees(Math.asin(hash(i, seed + 1) * 1.1f - 0.1f));
            float tw = 0.55f + 0.45f * Mth.sin(t * (1.5f + 4 * hash(i, seed + 2)) + i);
            float big = hash(i, seed + 3);
            float sz = size * (0.35f + big * big * 1.4f) * (1 + hi * 0.6f * big);
            int col = l.color(i).get(b);
            sprite(bb, m, dir(az, el), sz, 1, 0, col, s * tw * (0.4f + 0.6f * big));
        }
        end(bb);
    }

    /** Imagen o GIF en un punto del cielo, que puede girar y latir con la musica. */
    private static void image(Matrix4f m, SkyDef.Layer l, Beat b, float s, float t, Function<String, SkyImages.Img> images) {
        SkyImages.Img im = img(l, "galaxia", images);
        if (im == null) {
            return;
        }
        tex(im.frame((long) (t * 1000 * l.speed)));
        float size = l.num("tamano", 40) * (1 + l.num("latido", 0.12f) * l.react(b));
        float rot = l.num("rotacion", 0) + t * l.num("giro", 0) * l.speed;
        Batch bb = begin(true);
        float az = l.num("azimut", 0) + t * l.num("orbita", 0);
        sprite(bb, m, dir(az, l.num("altura", 60)), size, im.aspect, rot, l.color(0).get(b), s);
        end(bb);
    }

    /** Cortinas de aurora alrededor del horizonte, ondulando. */
    private static void aurora(Matrix4f m, SkyDef.Layer l, Beat b, float s, float t) {
        tex(SkyImages.builtin("aurora").frame(0));
        float base = l.num("altura", 12), tall = l.num("alto", 40) * (0.8f + 0.4f * l.react(b));
        float waves = l.num("ondas", 3), sp = l.speed * 0.25f;
        int seg = 96;
        Batch bb = begin(true);
        for (int j = 0; j < seg; j++) {
            float f0 = j / (float) seg, f1 = (j + 1) / (float) seg;
            float a0 = 360 * f0, a1 = 360 * f1;
            float w0 = Mth.sin(a0 * Mth.DEG_TO_RAD * waves + t * sp * 3) * 6 + Mth.sin(a0 * Mth.DEG_TO_RAD * 7 - t * sp * 5) * 2;
            float w1 = Mth.sin(a1 * Mth.DEG_TO_RAD * waves + t * sp * 3) * 6 + Mth.sin(a1 * Mth.DEG_TO_RAD * 7 - t * sp * 5) * 2;
            int c0 = gradient(l, b, f0 + t * 0.01f), c1 = gradient(l, b, f1 + t * 0.01f);
            float u0 = f0 * 4 + t * sp * 0.05f, u1 = f1 * 4 + t * sp * 0.05f;
            float k0 = 0.75f + 0.25f * b.band(f0), k1 = 0.75f + 0.25f * b.band(f1);
            v(bb, m, dir(a0, base + w0).mul(R), u0, 1, c0, s * k0);
            v(bb, m, dir(a1, base + w1).mul(R), u1, 1, c1, s * k1);
            v(bb, m, dir(a1, base + w1 + tall).mul(R), u1, 0, c1, s * k1);
            v(bb, m, dir(a0, base + w0 + tall).mul(R), u0, 0, c0, s * k0);
        }
        end(bb);
    }

    /** Color a lo largo de la lista "colores" (0..1, se repite). */
    private static int gradient(SkyDef.Layer l, Beat b, float f) {
        int n = l.colors.size();
        if (n == 1) {
            return l.color(0).get(b);
        }
        float x = (f - (float) Math.floor(f)) * n;
        int i = (int) x;
        return lerpRgb(l.color(i).get(b), l.color(i + 1).get(b), x - i);
    }

    /** Anillo de barras del ecualizador alrededor de un punto del cielo (por defecto el cenit). */
    private static void spectrum(Matrix4f m, SkyDef.Layer l, Beat b, float s, float t) {
        tex(SkyImages.builtin("haz").frame(0));
        Vector3f d = dir(l.num("azimut", 0), l.num("altura", 90));
        Vector3f[] bs = basis(d);
        int n = l.integer("barras", 64);
        float r0 = l.num("radio", 18) * (1 + 0.06f * b.kick()), len = l.num("largo", 30);
        float rot = t * l.num("giro", 3) * l.speed;
        float gap = 360f / n * 0.22f;
        Batch bb = begin(true);
        for (int i = 0; i < n; i++) {
            float x = i / (float) n;
            float mirror = Math.abs(2 * x - 1);
            float h = 0.04f + 0.96f * (float) Math.pow(b.band(1 - mirror), 1.4);
            float p0 = 360f * i / n + rot + gap, p1 = 360f * (i + 1) / n + rot - gap;
            int cb = gradient(l, b, 0), ct = gradient(l, b, 0.5f + 0.5f * h);
            float top = r0 + len * h;
            v(bb, m, around(d, bs, r0, p0), 0, 0, cb, s);
            v(bb, m, around(d, bs, r0, p1), 1, 0, cb, s);
            v(bb, m, around(d, bs, top, p1), 1, 0.85f, ct, s);
            v(bb, m, around(d, bs, top, p0), 0, 0.85f, ct, s);
        }
        end(bb);
        // aro brillante que late con el bombo
        Batch ring = begin(true);
        tex(SkyImages.builtin("brillo").frame(0));
        int seg = 96;
        int col = gradient(l, b, 0);
        for (int i = 0; i < seg; i++) {
            float p0 = 360f * i / seg, p1 = 360f * (i + 1) / seg;
            float w = 1.2f + 2.5f * b.kick();
            v(ring, m, around(d, bs, r0 - w, p0), 0.5f, 0, col, s);
            v(ring, m, around(d, bs, r0 - w, p1), 0.5f, 0, col, s);
            v(ring, m, around(d, bs, r0 + w, p1), 0.5f, 1, col, s);
            v(ring, m, around(d, bs, r0 + w, p0), 0.5f, 1, col, s);
        }
        end(ring);
    }

    /** Rayos de luz que salen de un punto y giran (sol de escenario). */
    private static void rays(Matrix4f m, SkyDef.Layer l, Beat b, float s, float t) {
        tex(SkyImages.builtin("haz").frame(0));
        Vector3f d = dir(l.num("azimut", 0), l.num("altura", 20));
        Vector3f[] bs = basis(d);
        int n = l.integer("cantidad", 16);
        float len = l.num("largo", 75), wid = l.num("ancho", 5);
        float rot = t * l.num("giro", 8) * l.speed;
        Batch bb = begin(true);
        int seg = 10;
        for (int i = 0; i < n; i++) {
            float phi = 360f * i / n + rot;
            float k = 0.35f + 0.65f * b.band(i / (float) n);
            int col = l.color(i).get(b);
            float L = len * (0.6f + 0.4f * k);
            for (int j = 0; j < seg; j++) {
                float a0 = 0.5f + L * j / seg, a1 = 0.5f + L * (j + 1) / seg;
                float hw0 = wid * (0.2f + 0.8f * j / seg) / Math.max(0.2f, Mth.sin(Math.max(1, a0) * Mth.DEG_TO_RAD)) * 0.5f;
                float hw1 = wid * (0.2f + 0.8f * (j + 1) / seg) / Math.max(0.2f, Mth.sin(Math.max(1, a1) * Mth.DEG_TO_RAD)) * 0.5f;
                hw0 = Math.min(hw0, 40);
                hw1 = Math.min(hw1, 40);
                float v0 = j / (float) seg, v1 = (j + 1) / (float) seg;
                v(bb, m, around(d, bs, a0, phi - hw0), 0, v0, col, s * k);
                v(bb, m, around(d, bs, a0, phi + hw0), 1, v0, col, s * k);
                v(bb, m, around(d, bs, a1, phi + hw1), 1, v1, col, s * k);
                v(bb, m, around(d, bs, a1, phi - hw1), 0, v1, col, s * k);
            }
        }
        end(bb);
    }

    /** Anillos que salen con cada golpe del bombo y se agrandan desapareciendo. */
    private static void rings(Matrix4f m, SkyDef.Layer l, Beat b, float weight, float t) {
        List<float[]> list = RINGS.computeIfAbsent(l, k -> new ArrayList<>());
        float r = l.react(b);
        float last = LAST_REACT.getOrDefault(l, 0f);
        float thr = l.num("umbral", 0.6f);
        boolean allowed = !l.onlyDrop || b.playing() && b.drop();
        if (b.playing() && allowed && r > thr && last <= thr && list.size() < 12) {
            list.add(new float[]{t, list.size() % Math.max(1, l.colors.size())});
        }
        LAST_REACT.put(l, r);
        if (list.isEmpty()) {
            return;
        }
        tex(SkyImages.builtin("anillo").frame(0));
        float dur = l.num("duracion", 1.6f), max = l.num("tamano", 90);
        Vector3f d = dir(l.num("azimut", 0), l.num("altura", 90));
        Batch bb = begin(true);
        list.removeIf(x -> t - x[0] > dur || t < x[0]);
        for (float[] x : list) {
            float f = (t - x[0]) / dur;
            float size = max * (0.15f + 0.85f * (float) Math.sqrt(f));
            sprite(bb, m, d, size, 1, 0, l.color((int) x[1]).get(b), l.opacity * weight * (1 - f) * (1 - f));
        }
        end(bb);
    }

    /** Hiperespacio: estelas que salen de un punto hacia afuera, mas rapido cuanto mas fuerte. */
    private static void tunnel(Matrix4f m, SkyDef.Layer l, Beat b, float s, float t) {
        tex(SkyImages.builtin("haz").frame(0));
        Vector3f d = dir(l.num("azimut", 0), l.num("altura", 15));
        Vector3f[] bs = basis(d);
        int n = Math.min(600, l.integer("cantidad", 220));
        float speed = l.speed * (0.25f + 0.9f * (b.playing() ? b.level() : 0.2f));
        Batch bb = begin(true);
        for (int i = 0; i < n; i++) {
            float phi = hash(i, 91) * 360;
            float ph = (hash(i, 92) + t * speed * (0.5f + hash(i, 93))) % 1.0f;
            float a = 2 + ph * ph * 85;
            float len = 2 + ph * 14;
            float w = 0.12f + ph * 0.5f;
            int col = l.color(i).get(b);
            float al = s * Math.min(1, ph * 4) * (1 - ph * 0.3f);
            v(bb, m, around(d, bs, a, phi - w), 0, 1, col, 0);
            v(bb, m, around(d, bs, a, phi + w), 1, 1, col, 0);
            v(bb, m, around(d, bs, a + len, phi + w), 1, 0, col, al);
            v(bb, m, around(d, bs, a + len, phi - w), 0, 0, col, al);
        }
        end(bb);
    }

    /** Caleidoscopio: gajos espejados de una imagen, girando alrededor de un punto. */
    private static void kaleido(Matrix4f m, SkyDef.Layer l, Beat b, float s, float t, Function<String, SkyImages.Img> images) {
        SkyImages.Img im = img(l, "nebulosa", images);
        if (im == null) {
            return;
        }
        tex(im.frame((long) (t * 1000)));
        Vector3f d = dir(l.num("azimut", 0), l.num("altura", 90));
        Vector3f[] bs = basis(d);
        int n = Math.max(2, l.integer("gajos", 12));
        float radius = l.num("radio", 55) * (1 + 0.08f * b.kick());
        float rot = t * l.num("giro", 6) * l.speed;
        float drift = t * 0.01f * l.speed;
        int tint = l.color(0).get(b);
        int rings = 6;
        Batch bb = begin(true);
        for (int i = 0; i < n; i++) {
            float p0 = 360f * i / n + rot, p1 = 360f * (i + 1) / n + rot;
            boolean flip = i % 2 == 1;
            for (int k = 0; k < rings; k++) {
                float a0 = radius * k / rings, a1 = radius * (k + 1) / rings;
                float v0 = 0.25f + 0.5f * k / rings, v1 = 0.25f + 0.5f * (k + 1) / rings;
                float uA = drift + (flip ? 0.12f : 0), uB = drift + (flip ? 0 : 0.12f);
                float edge0 = s * (1 - (float) k / rings * 0.6f), edge1 = s * (1 - (float) (k + 1) / rings * 0.6f);
                v(bb, m, around(d, bs, a0, p0), uA, v0, tint, edge0);
                v(bb, m, around(d, bs, a0, p1), uB, v0, tint, edge0);
                v(bb, m, around(d, bs, a1, p1), uB, v1, tint, edge1);
                v(bb, m, around(d, bs, a1, p0), uA, v1, tint, edge1);
            }
        }
        end(bb);
    }

    /** Destello de todo el cielo cuando la reaccion pasa el umbral. */
    private static void flash(Matrix4f m, SkyDef.Layer l, Beat b, float weight) {
        if (l.onlyDrop && !(b.playing() && b.drop())) {
            return;
        }
        float thr = l.num("umbral", 0.7f);
        float r = l.react(b);
        if (r <= thr) {
            return;
        }
        float a = (r - thr) / (1 - thr) * l.opacity * weight;
        int col = l.color(0).get(b);
        Batch bb = begin(false);
        int lon = 16;
        float[] els = {-10, 10, 35, 60, 90};
        for (int i = 0; i < els.length - 1; i++) {
            for (int j = 0; j < lon; j++) {
                float a0 = 360f * j / lon, a1 = 360f * (j + 1) / lon;
                c(bb, m, dir(a0, els[i]).mul(R), col, a);
                c(bb, m, dir(a1, els[i]).mul(R), col, a);
                c(bb, m, dir(a1, els[i + 1]).mul(R), col, a);
                c(bb, m, dir(a0, els[i + 1]).mul(R), col, a);
            }
        }
        end(bb);
    }
}
