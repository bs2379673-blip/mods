package com.starlevi.starscenes.emf;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Un modelo de EMF armado para un personaje: el esqueleto de jugador de Minecraft (head, body,
 * brazos, piernas y sus capas) con las piezas del .jem enganchadas, sus variables (var.*) y las
 * formulas aplicadas cada cuadro. Devuelve que caras dibujar y con que matriz.
 */
public final class EmfRig {
    static final class Node {
        final String name;
        final boolean vanilla;
        float x, y, z, rx, ry, rz, sx = 1, sy = 1, sz = 1;
        boolean visible = true, boxes = true;
        float dx, dy, dz, drx, dry, drz, ds = 1;
        final List<JemModel.Face> faces = new ArrayList<>();
        final List<Node> children = new ArrayList<>();

        Node(String name, boolean vanilla) {
            this.name = name;
            this.vanilla = vanilla;
        }

        void reset() {
            this.x = this.dx;
            this.y = this.dy;
            this.z = this.dz;
            this.rx = this.drx;
            this.ry = this.dry;
            this.rz = this.drz;
            this.sx = this.sy = this.sz = this.ds;
            this.visible = true;
            this.boxes = true;
        }
    }

    /** Lo que hay que dibujar: caras con su matriz (en pixeles del modelo). */
    public record Draw(Matrix4f matrix, List<JemModel.Face> faces) {
    }

    private final List<Node> roots = new ArrayList<>();
    private final Map<String, Node> byName = new HashMap<>();
    private final List<JemModel.Line> lines;
    private final Map<String, Float> vars = new HashMap<>();
    private final List<Node> all = new ArrayList<>();

    public EmfRig(JemModel m) {
        this.lines = m.lines;
        // esqueleto de jugador (como el de Minecraft), con nombres de OptiFine
        // (como en OptiFine: las capas de afuera son piezas aparte, en el mismo lugar que su parte, y
        // siguen a su parte; los paquetes copian el giro del brazo a la manga, por eso no van adentro)
        Node head = vanilla("head", 0, 0, 0, "-4,-8,-4,8,8,8", 0, 0, 0);
        Node hat = vanilla("headwear", 0, 0, 0, "-4,-8,-4,8,8,8", 32, 0, 0.5f);
        Node body = vanilla("body", 0, 0, 0, "-4,0,-2,8,12,4", 16, 16, 0);
        Node jacket = vanilla("jacket", 0, 0, 0, "-4,0,-2,8,12,4", 16, 32, 0.25f);
        Node ra = vanilla("right_arm", -5, 2, 0, "-3,-2,-2,4,12,4", 40, 16, 0);
        Node rs = vanilla("right_sleeve", -5, 2, 0, "-3,-2,-2,4,12,4", 40, 32, 0.25f);
        Node la = vanilla("left_arm", 5, 2, 0, "-1,-2,-2,4,12,4", 32, 48, 0);
        Node ls = vanilla("left_sleeve", 5, 2, 0, "-1,-2,-2,4,12,4", 48, 48, 0.25f);
        Node rl = vanilla("right_leg", -1.9f, 12, 0, "-2,0,-2,4,12,4", 0, 16, 0);
        Node rp = vanilla("right_pants", -1.9f, 12, 0, "-2,0,-2,4,12,4", 0, 32, 0.25f);
        Node ll = vanilla("left_leg", 1.9f, 12, 0, "-2,0,-2,4,12,4", 16, 48, 0);
        Node lp = vanilla("left_pants", 1.9f, 12, 0, "-2,0,-2,4,12,4", 0, 48, 0.25f);
        Node cape = vanilla("cape", 0, 0, 2, null, 0, 0, 0), ear = vanilla("ear", 0, 0, 0, null, 0, 0, 0);
        for (Node n : new Node[]{head, hat, body, jacket, ra, rs, la, ls, rl, rp, ll, lp, cape, ear}) {
            this.roots.add(n);
        }
        this.layers = new Node[][]{{head, hat}, {body, jacket}, {ra, rs}, {la, ls}, {rl, rp}, {ll, lp}};
        for (Node r : new ArrayList<>(this.roots)) {
            index(r);
        }
        this.byName.put("hat", hat);
        // las piezas del .jem, enganchadas a su parte (si no son "attach", reemplazan a la de Minecraft)
        for (JemModel.Part p : m.parts) {
            String v = p.vanilla.toLowerCase(Locale.ROOT);
            Node target = this.byName.get(v.equals("hat") ? "headwear" : v);
            if (target == null) {
                continue;
            }
            if (!p.attach) {
                target.faces.clear();
                for (Node c : target.children) {
                    if (c.vanilla) {
                        c.faces.clear();
                    }
                }
            }
            target.children.add(custom(p));
        }
        for (Node r : this.roots) {
            collect(r);
        }
    }

    private void index(Node n) {
        this.byName.putIfAbsent(n.name, n);
        for (Node c : n.children) {
            index(c);
        }
    }

    private void collect(Node n) {
        this.all.add(n);
        for (Node c : n.children) {
            collect(c);
        }
    }

    private Node custom(JemModel.Part p) {
        Node n = new Node(p.id, false);
        n.dx = p.x;
        n.dy = p.y;
        n.dz = p.z;
        n.drx = p.rx;
        n.dry = p.ry;
        n.drz = p.rz;
        n.ds = p.scale;
        n.faces.addAll(p.faces);
        for (JemModel.Part c : p.children) {
            n.children.add(custom(c));
        }
        if (!p.id.isEmpty()) {
            // las propias se buscan por "EMF_id"; si el nombre esta libre, tambien por "id"
            this.byName.putIfAbsent("EMF_" + p.id, n);
            this.byName.putIfAbsent(p.id, n);
        }
        n.reset();
        return n;
    }

    private static Node vanilla(String name, float x, float y, float z, String box, int u, int v, float grow) {
        Node n = new Node(name, true);
        n.dx = x;
        n.dy = y;
        n.dz = z;
        if (box != null) {
            JemModel.Part tmp = new JemModel.Part();
            String[] s = box.split(",");
            float bx = Float.parseFloat(s[0]), by = Float.parseFloat(s[1]), bz = Float.parseFloat(s[2]);
            float w = Float.parseFloat(s[3]), h = Float.parseFloat(s[4]), d = Float.parseFloat(s[5]);
            JsonObject b = new JsonObject();
            JsonArray c = new JsonArray();
            for (float f : new float[]{bx, by, bz, w, h, d}) {
                c.add(f);
            }
            b.add("coordinates", c);
            JsonArray to = new JsonArray();
            to.add(u);
            to.add(v);
            b.add("textureOffset", to);
            b.addProperty("sizeAdd", grow);
            VanillaBox.add(tmp, b);
            n.faces.addAll(tmp.faces);
        }
        n.reset();
        return n;
    }

    // ---------------------------------------------------------------- cada cuadro

    /** Las variables del personaje que leen las formulas. */
    private final class Vars implements Expr.Scope {
        Entity e;
        float partial, limbSwing, limbSpeed, headYaw, headPitch, frameTime = 1 / 60f;

        @Override
        public String text(String path) {
            if (!(this.e instanceof LivingEntity le)) {
                return "";
            }
            String p = path.toLowerCase(Locale.ROOT);
            net.minecraft.world.item.ItemStack st = p.startsWith("selecteditem") || p.contains("mainhand") ? le.getMainHandItem()
                    : p.contains("offhand") ? le.getOffhandItem() : p.contains("head") ? le.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD)
                    : net.minecraft.world.item.ItemStack.EMPTY;
            return st.isEmpty() ? "" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
        }

        @Override
        public float get(String name) {
            int dot = name.indexOf('.');
            if (dot > 0) {
                String a = name.substring(0, dot), b = name.substring(dot + 1);
                if (a.equals("var") || a.equals("varb")) {
                    return EmfRig.this.vars.getOrDefault(b, 0f);
                }
                Node n = node(a);
                return n == null ? 0 : read(n, b);
            }
            return entity(name);
        }

        private float entity(String n) {
            LivingEntity le = this.e instanceof LivingEntity l ? l : null;
            return switch (n) {
                case "limb_swing" -> this.limbSwing;
                case "limb_speed" -> this.limbSpeed;
                case "age" -> this.e.tickCount + this.partial;
                case "head_yaw" -> this.headYaw;
                case "head_pitch" -> this.headPitch;
                case "frame_time" -> this.frameTime;
                case "time" -> (this.e.level().getGameTime() % 24000) + this.partial;
                case "day_time" -> this.e.level().getDayTime() % 24000;
                case "day_count" -> (float) (this.e.level().getDayTime() / 24000);
                case "id" -> this.e.getUUID().hashCode();
                case "pos_x" -> (float) this.e.getX();
                case "pos_y" -> (float) this.e.getY();
                case "pos_z" -> (float) this.e.getZ();
                case "rot_x" -> (float) Math.toRadians(this.e.getXRot());
                case "rot_y" -> (float) Math.toRadians(this.e.getYRot());
                case "hurt_time" -> le == null ? 0 : Math.max(0, le.hurtTime - this.partial);
                case "death_time" -> le == null ? 0 : le.deathTime;
                case "swing_progress" -> le == null ? 0 : le.getAttackAnim(this.partial);
                case "health" -> le == null ? 0 : le.getHealth();
                case "max_health" -> le == null ? 0 : le.getMaxHealth();
                case "move_forward" -> this.limbSpeed;
                case "is_alive" -> b(this.e.isAlive());
                case "is_burning" -> b(this.e.isOnFire());
                case "is_child" -> b(le != null && le.isBaby());
                case "is_glowing" -> b(this.e.isCurrentlyGlowing());
                case "is_hurt" -> b(le != null && le.hurtTime > 0);
                case "is_in_lava" -> b(this.e.isInLava());
                case "is_in_water" -> b(this.e.isInWater());
                case "is_wet" -> b(this.e.isInWaterOrRain());
                case "is_invisible" -> b(this.e.isInvisible());
                case "is_on_ground" -> b(this.e.onGround());
                case "is_riding" -> b(this.e.isPassenger());
                case "is_ridden" -> b(this.e.isVehicle());
                case "is_sneaking", "is_crouching" -> b(this.e.isCrouching());
                case "is_sprinting" -> b(this.e.isSprinting());
                case "is_swimming" -> b(le != null && le.isVisuallySwimming());
                case "is_gliding" -> b(le != null && le.isFallFlying());
                case "is_climbing" -> b(le != null && le.onClimbable());
                case "is_sleeping" -> b(le != null && le.isSleeping());
                case "is_crawling" -> b(this.e.hasPose(Pose.SWIMMING) && !this.e.isInWater());
                case "is_jumping" -> b(!this.e.onGround() && this.e.getDeltaMovement().y > 0);
                case "is_aggressive" -> b(le instanceof net.minecraft.world.entity.Mob mob && mob.isAggressive());
                case "is_in_gui", "is_in_hand", "is_in_item_frame", "is_on_head", "is_on_shoulder", "is_first_person_hand", "is_paused", "is_tamed",
                     "is_sitting" -> 0;
                default -> 0;
            };
        }
    }

    private static float b(boolean v) {
        return v ? 1 : 0;
    }

    private Node node(String name) {
        Node n = this.byName.get(name);
        return n != null ? n : this.byName.get("EMF_" + name);
    }

    private static float read(Node n, String ch) {
        return switch (ch) {
            case "tx" -> n.x;
            case "ty" -> n.y;
            case "tz" -> n.z;
            case "rx" -> n.rx;
            case "ry" -> n.ry;
            case "rz" -> n.rz;
            case "sx" -> n.sx;
            case "sy" -> n.sy;
            case "sz" -> n.sz;
            case "visible" -> n.visible ? 1 : 0;
            case "visible_boxes" -> n.boxes ? 1 : 0;
            default -> 0;
        };
    }

    private static void write(Node n, String ch, float v) {
        if (Float.isNaN(v) || Float.isInfinite(v)) {
            return;
        }
        switch (ch) {
            case "tx" -> n.x = v;
            case "ty" -> n.y = v;
            case "tz" -> n.z = v;
            case "rx" -> n.rx = v;
            case "ry" -> n.ry = v;
            case "rz" -> n.rz = v;
            case "sx" -> n.sx = v;
            case "sy" -> n.sy = v;
            case "sz" -> n.sz = v;
            case "visible" -> n.visible = v != 0;
            case "visible_boxes" -> n.boxes = v != 0;
            default -> {
            }
        }
    }

    private final Vars scope = new Vars();
    /** {parte, capa de afuera}: la capa sigue a su parte. */
    private Node[][] layers = new Node[0][];

    private void follow() {
        for (Node[] l : this.layers) {
            Node a = l[0], b = l[1];
            b.x = a.x;
            b.y = a.y;
            b.z = a.z;
            b.rx = a.rx;
            b.ry = a.ry;
            b.rz = a.rz;
            b.sx = a.sx;
            b.sy = a.sy;
            b.sz = a.sz;
        }
    }
    private Matrix4f headM;
    private long lastNanos;

    /** La matriz de la cabeza del ultimo cuadro (en pixeles del modelo), o null. */
    public Matrix4f headMatrix() {
        return this.headM;
    }

    private Node chest;
    private boolean chestOn;
    private float chestBounce;

    /** El pecho (cuerpo de mujer) como pieza extra del cuerpo, con su rebote en radianes. */
    public void chest(boolean on, float bounce) {
        if (on && this.chest == null) {
            Node body = this.byName.get("body");
            if (body == null) {
                return;
            }
            // dos pechos chicos (como el Female Gender Mod)
            this.chest = vanilla("starscenes_chest", 0, 2.5f, -2, "-4,0,-2,4,4,2", 18, 21, -0.05f);
            this.chest.children.add(vanilla("starscenes_chest_l", 0, 0, 0, "0,0,-2,4,4,2", 22, 21, -0.05f));
            this.chest.children.add(vanilla("starscenes_chest_jacket", 0, 0, 0, "-4,0,-2,4,4,2", 18, 37, 0.2f));
            this.chest.children.add(vanilla("starscenes_chest_jacket_l", 0, 0, 0, "0,0,-2,4,4,2", 22, 37, 0.2f));
            this.chest.drx = -0.21f;
            body.children.add(this.chest);
        }
        this.chestOn = on;
        this.chestBounce = bounce;
    }

    /** Pose de este cuadro: la de Minecraft (caminar, mirar) y despues las formulas del paquete. */
    public List<Draw> pose(Entity e, float partial, float headYaw, float headPitch) {
        return pose(e, partial, headYaw, headPitch, java.util.Map.of());
    }

    /**
     * Igual, y encima un gesto de StarScenes (saludar, reir...): hueso -> {giro x, y, z en grados, peso}.
     * Solo toca las partes de Minecraft (head, body, brazos, piernas); el resto sigue con el paquete.
     */
    public List<Draw> pose(Entity e, float partial, float headYaw, float headPitch, java.util.Map<String, float[]> gesture) {
        for (Node n : this.all) {
            n.reset();
        }
        Vars s = this.scope;
        s.e = e;
        s.partial = partial;
        // el tiempo real entre cuadros de este personaje (los paquetes suavizan con esto)
        long nowN = System.nanoTime();
        s.frameTime = this.lastNanos == 0 ? 1 / 60f : Math.max(0.001f, Math.min(0.25f, (nowN - this.lastNanos) / 1e9f));
        this.lastNanos = nowN;
        s.headYaw = headYaw;
        s.headPitch = headPitch;
        if (e instanceof LivingEntity le) {
            s.limbSwing = le.walkAnimation.position(partial);
            s.limbSpeed = Math.min(1, le.walkAnimation.speed(partial));
        } else {
            s.limbSwing = s.limbSpeed = 0;
        }
        vanillaPose(e, s);
        follow();
        for (JemModel.Line l : this.lines) {
            float v;
            try {
                v = l.expr().eval(s);
            } catch (Exception ex) {
                continue;
            }
            if (l.target().equals("var") || l.target().equals("varb")) {
                this.vars.put(l.channel(), v);
            } else if (!l.target().equals("render")) {
                Node n = node(l.target());
                if (n != null) {
                    write(n, l.channel(), v);
                }
            }
        }
        if (this.chest != null) {
            this.chest.visible = this.chestOn;
            this.chest.rx = -0.21f - this.chestBounce;
        }
        for (var g : gesture.entrySet()) {
            Node n = this.byName.get(g.getKey());
            if (n == null || !n.vanilla) {
                continue;
            }
            float[] v = g.getValue();
            float w = v[3];
            float rx = (float) Math.toRadians(v[0]), ry = (float) Math.toRadians(v[1]), rz = (float) Math.toRadians(v[2]);
            if (g.getKey().equals("head")) {
                // la cabeza sigue mirando a donde mira, el gesto se suma
                rx += n.rx;
                ry += n.ry;
            }
            n.rx += (rx - n.rx) * w;
            n.ry += (ry - n.ry) * w;
            n.rz += (rz - n.rz) * w;
        }
        follow();
        List<Draw> out = new ArrayList<>();
        this.headM = null;
        Matrix4f root = new Matrix4f();
        for (Node r : this.roots) {
            walk(r, root, out);
        }
        return out;
    }

    private void walk(Node n, Matrix4f parent, List<Draw> out) {
        if (!n.visible) {
            return;
        }
        Matrix4f m = new Matrix4f(parent).translate(n.x, n.y, n.z).rotateZYX(n.rz, n.ry, n.rx).scale(n.sx, n.sy, n.sz);
        if (n.vanilla && n.name.equals("head")) {
            this.headM = m;
        }
        if (n.boxes && !n.faces.isEmpty()) {
            out.add(new Draw(m, n.faces));
        }
        for (Node c : n.children) {
            walk(c, m, out);
        }
    }

    /** Lo que hace el modelo de jugador de Minecraft por su cuenta (base para las formulas). */
    private void vanillaPose(Entity e, Vars s) {
        Node head = this.byName.get("head"), body = this.byName.get("body"), ra = this.byName.get("right_arm"), la = this.byName.get("left_arm"),
                rl = this.byName.get("right_leg"), ll = this.byName.get("left_leg");
        head.ry = s.headYaw * Mth.DEG_TO_RAD;
        head.rx = s.headPitch * Mth.DEG_TO_RAD;
        float sw = s.limbSwing, sp = s.limbSpeed, age = e.tickCount + s.partial;
        ra.rx = Mth.cos(sw * 0.6662f + Mth.PI) * 2 * sp * 0.5f;
        la.rx = Mth.cos(sw * 0.6662f) * 2 * sp * 0.5f;
        rl.rx = Mth.cos(sw * 0.6662f) * 1.4f * sp;
        ll.rx = Mth.cos(sw * 0.6662f + Mth.PI) * 1.4f * sp;
        ra.rz = Mth.cos(age * 0.09f) * 0.05f + 0.05f;
        la.rz = -(Mth.cos(age * 0.09f) * 0.05f + 0.05f);
        ra.rx += Mth.sin(age * 0.067f) * 0.05f;
        la.rx -= Mth.sin(age * 0.067f) * 0.05f;
        if (e.isCrouching()) {
            body.rx = 0.5f;
            ra.rx += 0.4f;
            la.rx += 0.4f;
            rl.z = ll.z = 4;
            rl.y = ll.y = 12.2f;
            head.y = 4.2f;
            body.y = 3.2f;
            ra.y = la.y = 5.2f;
        }
    }
}
