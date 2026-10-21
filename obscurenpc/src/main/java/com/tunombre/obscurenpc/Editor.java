package com.tunombre.obscurenpc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.util.UUID;

/** Lado servidor del editor in-game (el panel de CotiiUI manda ordenes JSON y aqui se validan y aplican). */
public final class Editor {
    private Editor() { }

    private static boolean allowed(ServerPlayer p) { return p.hasPermissions(2) || p.isCreative(); }

    public static JsonObject catalog() {
        JsonObject c = new JsonObject();
        c.add("dialogos", arr(Dialogs.fileIds()));
        c.add("rutinas", arr(Routine.fileIds()));
        c.add("conocimientos", arr(Brain.fileIds()));
        JsonArray g = new JsonArray();
        for (String s : Gestures.ONESHOT) g.add(s);
        c.add("gestos", g);
        JsonArray lp = new JsonArray();
        for (String s : Gestures.LOOPS) lp.add(s);
        c.add("posturas_gesto", lp);
        JsonArray st = new JsonArray();
        for (String s : Stances.NAMES) st.add(s);
        c.add("posturas", st);
        c.addProperty("tacz", TaczBridge.available());
        return c;
    }

    private static JsonArray arr(java.util.List<String> l) {
        JsonArray a = new JsonArray();
        for (String s : l) a.add(s);
        return a;
    }

    public static void open(ServerPlayer p, Actor a) {
        if (!allowed(p)) { p.sendSystemMessage(Component.literal("Necesitas ser operador para editar NPCs.").withStyle(ChatFormatting.RED)); return; }
        JsonObject o = new JsonObject();
        o.addProperty("tipo", "npc");
        o.addProperty("uuid", a.getUUID().toString());
        o.addProperty("motor", a.isEf() ? "ef" : "normal");
        o.add("data", JsonParser.parseString(a.data().toJson()));
        o.add("cat", catalog());
        addCtx(o, a.data().key());
        o.addProperty("vida_actual", a.getHealth());
        Net.toClient(p, "editor", o.toString());
    }

    public static void openSpawner(ServerPlayer p, BlockPos pos) {
        if (!allowed(p)) return;
        if (!(p.level().getBlockEntity(pos) instanceof SpawnerEntity be)) return;
        JsonObject o = new JsonObject();
        o.addProperty("tipo", "spawner");
        o.addProperty("x", pos.getX());
        o.addProperty("y", pos.getY());
        o.addProperty("z", pos.getZ());
        o.addProperty("motor", be.motor);
        o.addProperty("intervalo", be.intervalo);
        o.addProperty("maximo", be.maximo);
        o.addProperty("radio", be.radio);
        o.addProperty("activo", be.activo);
        o.addProperty("rango", be.rango);
        o.add("data", JsonParser.parseString(be.template.toJson()));
        o.add("cat", catalog());
        addCtx(o, be.template.key());
        Net.toClient(p, "editor", o.toString());
    }

    private static void addCtx(JsonObject o, String key) {
        o.addProperty("ctx_key", Ctx.safe(key));
        o.addProperty("ctx_npc", Ctx.read(key, false));
        o.addProperty("ctx_mundo", Ctx.read(key, true));
    }

    private static final java.util.Map<UUID, StringBuilder> CTX_BUF = new java.util.HashMap<>();

    private static void ctxOp(ServerPlayer p, String op, JsonObject o, Actor a, SpawnerEntity be) {
        String key = a != null ? a.data().key() : be.template.key();
        if (op.equals("ctx_get")) {
            JsonObject r = new JsonObject();
            r.addProperty("key", Ctx.safe(key));
            r.addProperty("npc", Ctx.read(key, false));
            Net.toClient(p, "ctx", r.toString());
            return;
        }
        int i = o.has("i") ? o.get("i").getAsInt() : 0, n = o.has("n") ? o.get("n").getAsInt() : 1;
        if (i == 0) CTX_BUF.put(p.getUUID(), new StringBuilder());
        StringBuilder sb = CTX_BUF.get(p.getUUID());
        if (sb == null) return;
        if (sb.length() < Ctx.MAX_CHARS + 10000) sb.append(str(o, "texto"));
        if (i >= n - 1) {
            CTX_BUF.remove(p.getUUID());
            boolean shared = str(o, "dest").equals("mundo");
            msg(p, Ctx.write(key, shared, sb.toString()));
        }
    }

    private static void msg(ServerPlayer p, String s) { Net.toClient(p, "msg", s); }

    private static Actor find(ServerPlayer p, String uuid) {
        try {
            UUID id = UUID.fromString(uuid);
            for (ServerLevel l : p.server.getAllLevels()) {
                Entity e = l.getEntity(id);
                if (e instanceof Actor a) return a;
            }
        } catch (IllegalArgumentException ignored) { }
        return null;
    }

    // ------------------------------------------------------------------ ordenes ----
    public static void handle(ServerPlayer p, String kind, String json) {
        if (!allowed(p)) return;
        JsonObject o;
        try { o = JsonParser.parseString(json).getAsJsonObject(); } catch (RuntimeException e) { return; }
        String op = str(o, "op");
        boolean sp = o.has("x") && o.has("y") && o.has("z");
        Actor a = o.has("uuid") ? find(p, o.get("uuid").getAsString()) : null;
        SpawnerEntity be = sp && p.level().getBlockEntity(new BlockPos(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt())) instanceof SpawnerEntity s ? s : null;
        if (a == null && be == null && !op.equals("recargar")) { msg(p, "No encuentro ese NPC (¿se alejó o murió?)"); return; }
        switch (op) {
            case "set" -> set(p, a, be, str(o, "k"), o.get("v").getAsString());
            case "ctx_get", "ctx_save" -> ctxOp(p, op, o, a, be);
            case "preguntar" -> {
                if (a != null) {
                    Brain.Reply r = Brain.reply(a, p, null, str(o, "texto"));
                    a.speak((r.gesture() != null ? "[gesto:" + r.gesture() + "]" : "") + r.text(), p, true);
                }
            }
            case "recargar" -> { Dialogs.reload(); msg(p, "Diálogos, contexto y rutinas recargados"); }
            case "equipo" -> equipo(p, a, be, str(o, "que"));
            case "vaciar_equipo" -> {
                NpcData d = a != null ? a.data() : be.template;
                d.eq_mano = d.eq_mano2 = d.eq_cabeza = d.eq_pecho = d.eq_piernas = d.eq_pies = "";
                if (a != null) {
                    for (net.minecraft.world.entity.EquipmentSlot s : net.minecraft.world.entity.EquipmentSlot.values()) a.setItemSlot(s, ItemStack.EMPTY);
                    a.applyData();
                } else be.setChanged();
                msg(p, "Equipo vaciado");
            }
            case "motor" -> motor(p, a, be, str(o, "motor"));
            case "gesto" -> { if (a != null) a.gesture(str(o, "id")); }
            case "hablar" -> { if (a != null) a.speak(str(o, "texto"), p, true); }
            case "tp_aqui" -> { if (a != null) { a.teleportTo(p.getX(), p.getY(), p.getZ()); msg(p, "NPC teletransportado a ti"); } }
            case "tp_a_el" -> { if (a != null) p.teleportTo((ServerLevel) a.level(), a.getX(), a.getY(), a.getZ(), p.getYRot(), p.getXRot()); }
            case "borrar" -> { if (a != null) { a.discard(); msg(p, "NPC borrado"); } }
            case "duplicar" -> {
                if (a != null) {
                    Actor c = ObscureNpc.create((ServerLevel) a.level(), a.isEf() ? "ef" : "normal", a.data().copy());
                    if (c != null) { c.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0); ((ServerLevel) a.level()).addFreshEntity(c); msg(p, "NPC duplicado a tu lado"); }
                }
            }
            case "plantilla" -> {
                ItemStack hand = p.getMainHandItem().getItem() instanceof NpcItems.EditorItem ? p.getMainHandItem() : p.getOffhandItem();
                NpcData d = a != null ? a.data() : be.template;
                String motor = a != null ? (a.isEf() ? "ef" : "normal") : be.motor;
                if (hand.getItem() instanceof NpcItems.EditorItem || hand.getItem() instanceof NpcItems.CreatorItem) {
                    NpcItems.putTemplate(hand, d, motor);
                    msg(p, "Plantilla copiada al ítem de tu mano");
                } else msg(p, "Sostén el Editor o el Creador de NPC en la mano");
            }
            case "plantilla_aplicar" -> {
                ItemStack hand = p.getMainHandItem().getItem() instanceof NpcItems.EditorItem ? p.getMainHandItem() : p.getOffhandItem();
                NpcData t = NpcItems.getTemplate(hand);
                if (t == null) { msg(p, "El ítem de tu mano no tiene plantilla"); return; }
                if (a != null) { a.setData(t.copy()); msg(p, "Plantilla aplicada al NPC"); open(p, a); }
                else { be.template = t.copy(); be.setChanged(); msg(p, "Plantilla aplicada al spawner"); openSpawner(p, be.getBlockPos()); }
            }
            case "rutina" -> {
                if (a == null) return;
                if (str(o, "accion").equals("parar")) { a.routine().stop(); msg(p, "Rutina detenida"); }
                else msg(p, a.routine().start(a.data().rutina) ? "Rutina iniciada" : "No existe la rutina '" + a.data().rutina + "'");
            }
            case "dialogo" -> { if (a != null) Dialogs.start(a, p, ""); }
            case "fijar_giro" -> {
                NpcData d = a != null ? a.data() : be.template;
                d.fijar_giro = true;
                d.giro = p.getYRot() + 180F;
                if (a != null) a.applyData(); else be.setChanged();
                msg(p, "El NPC mirará hacia donde tú miras");
            }
            case "hogar_aqui" -> { msg(p, "Las coordenadas de hogar se toman donde aparece el NPC"); }
            default -> { }
        }
    }

    private static String str(JsonObject o, String k) { return o.has(k) ? o.get(k).getAsString() : ""; }

    private static void equipo(ServerPlayer p, Actor a, SpawnerEntity be, String que) {
        NpcData d = a != null ? a.data() : be.template;
        switch (que) {
            case "mano" -> d.eq_mano = Actor.stackToSnbt(p.getMainHandItem());
            case "mano2" -> d.eq_mano2 = Actor.stackToSnbt(p.getOffhandItem());
            case "armadura" -> {
                d.eq_cabeza = Actor.stackToSnbt(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD));
                d.eq_pecho = Actor.stackToSnbt(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST));
                d.eq_piernas = Actor.stackToSnbt(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS));
                d.eq_pies = Actor.stackToSnbt(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET));
            }
            default -> { return; }
        }
        if (a != null) a.applyData(); else be.setChanged();
        msg(p, "Equipo copiado de lo que llevas puesto/en la mano");
    }

    private static void motor(ServerPlayer p, Actor a, SpawnerEntity be, String motor) {
        if (a == null) { be.motor = motor; be.setChanged(); msg(p, "Motor del spawner: " + motor); return; }
        if (a.isEf() == motor.equals("ef")) return;
        ServerLevel lvl = (ServerLevel) a.level();
        Actor n = ObscureNpc.create(lvl, motor, a.data().copy());
        if (n == null) return;
        n.moveTo(a.getX(), a.getY(), a.getZ(), a.getYRot(), a.getXRot());
        n.setHealth(Math.min(n.getMaxHealth(), a.getHealth()));
        for (net.minecraft.world.entity.EquipmentSlot s : net.minecraft.world.entity.EquipmentSlot.values()) n.setItemSlot(s, a.getItemBySlot(s).copy());
        if (a.spawner() != null) n.setSpawner(a.spawner());
        lvl.addFreshEntity(n);
        a.discard();
        msg(p, "Motor cambiado a " + (motor.equals("ef") ? "Epic Fight" : "huesos normales"));
        open(p, n);
    }

    private static void set(ServerPlayer p, Actor a, SpawnerEntity be, String k, String v) {
        if (k.startsWith("@")) {
            if (be == null) return;
            try {
                switch (k) {
                    case "@intervalo" -> be.intervalo = Math.max(20, Integer.parseInt(v));
                    case "@maximo" -> be.maximo = Math.max(1, Math.min(64, Integer.parseInt(v)));
                    case "@radio" -> be.radio = Math.max(0, Math.min(32, Integer.parseInt(v)));
                    case "@rango" -> be.rango = Math.max(8, Math.min(256, Integer.parseInt(v)));
                    case "@activo" -> be.activo = Boolean.parseBoolean(v);
                    default -> { }
                }
            } catch (NumberFormatException ignored) { }
            be.setChanged();
            return;
        }
        NpcData d = a != null ? a.data() : be.template;
        if (!setField(d, k, v)) return;
        if (a != null) {
            if (k.equals("postura")) a.setStance(d.postura);
            a.applyData();
            if (k.equals("vida")) a.setHealth(a.getMaxHealth());
        } else be.setChanged();
    }

    /** asigna un campo de NpcData por reflexion convirtiendo el texto al tipo del campo */
    public static boolean setField(NpcData d, String k, String v) {
        try {
            Field f = NpcData.class.getField(k);
            Class<?> t = f.getType();
            if (t == boolean.class) f.setBoolean(d, Boolean.parseBoolean(v) || v.equals("1"));
            else if (t == int.class) f.setInt(d, (int) Math.round(Double.parseDouble(v)));
            else if (t == float.class) f.setFloat(d, Float.parseFloat(v));
            else if (t == double.class) f.setDouble(d, Double.parseDouble(v));
            else if (t == String.class) f.set(d, v);
            else if (t == java.util.List.class) {
                java.util.List<String> l = new java.util.ArrayList<>();
                for (String s : v.split("\n")) if (!s.isBlank()) l.add(s);
                f.set(d, l);
            } else return false;
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
