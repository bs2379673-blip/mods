package com.annis.npc.mca;

import com.annis.npc.AnnisNpc;
import com.annis.npc.entity.AnnisNpcEntity;
import com.annis.npc.link.EntityLinks;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import forge.net.conczin.mca.entity.VillagerEntityMCA;
import forge.net.conczin.mca.entity.VillagerFactory;
import forge.net.conczin.mca.entity.ai.Chore;
import forge.net.conczin.mca.entity.ai.relationship.Gender;
import forge.net.conczin.mca.entity.ai.relationship.RelationshipState;
import com.annis.npc.compat.ServerTickEvents;
import com.annis.npc.compat.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Integracion con Minecraft Comes Alive (solo se carga si MCA esta instalado).
 * - Acciones de MCA (dialogos, comandos, matrimonio, hijos, trabajo, hablar...) -> animaciones nuestras.
 * - Convertir un NPC Annis en persona de MCA (casarse, hijos, trabajos...) conservando su aspecto.
 */
public final class McaIntegration {
    /** Que animacion poner y cuanto dura (0 = mientras dure el estado). */
    public record Accion(String animacion, int segundos) {
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, Accion> ACCIONES = new LinkedHashMap<>();
    /** Estado de cada aldeano para detectar cambios. */
    private static final Map<UUID, Seen> SEEN = new HashMap<>();

    private static final class Seen {
        RelationshipState rel;
        boolean procreating;
        String state = "";
        long busyUntil;
    }

    private McaIntegration() {
    }

    public static void init() {
        load();
        ServerTickEvents.END_SERVER_TICK.register(McaIntegration::tick);
    }

    // ---------------------------------------------------------------- configuracion

    public static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("annisnpc").resolve("mca.json");
    }

    private static void defaults() {
        ACCIONES.clear();
        // estados que duran (0 s = mientras dure)
        ACCIONES.put("hablando", new Accion("charlar", 0));
        ACCIONES.put("trabajo.fishing", new Accion("pescar", 0));
        ACCIONES.put("trabajo.harvesting", new Accion("mercado", 0));
        ACCIONES.put("trabajo.chopping", new Accion("combate", 0));
        ACCIONES.put("trabajo.hunting", new Accion("combate", 0));
        ACCIONES.put("trabajo.prospecting", new Accion("combate", 0));
        ACCIONES.put("dormir", new Accion("", 0));
        // momentos de la relacion
        ACCIONES.put("promesa", new Accion("saludo_sombrero", 4));
        ACCIONES.put("compromiso", new Accion("aplaudir", 5));
        ACCIONES.put("boda", new Accion("bailar", 10));
        ACCIONES.put("procrear", new Accion("bailar", 6));
        ACCIONES.put("divorcio", new Accion("pensar", 6));
        // dialogos (la primera parte del id de la pregunta de MCA)
        ACCIONES.put("dialogo.greet", new Accion("saludar", 3));
        ACCIONES.put("dialogo.chat", new Accion("charlar", 4));
        ACCIONES.put("dialogo.joke", new Accion("aplaudir", 3));
        ACCIONES.put("dialogo.story", new Accion("charlar", 6));
        ACCIONES.put("dialogo.hug", new Accion("aplaudir", 3));
        ACCIONES.put("dialogo.kiss", new Accion("saludo_sombrero", 3));
        ACCIONES.put("dialogo.flirt", new Accion("bailar", 3));
        ACCIONES.put("dialogo.procreate", new Accion("pensar", 3));
        ACCIONES.put("dialogo.rock_paper_scissor", new Accion("pensar", 2));
        // reaccion a la respuesta (corazones ganados o perdidos)
        ACCIONES.put("reaccion.positiva", new Accion("aplaudir", 2));
        ACCIONES.put("reaccion.negativa", new Accion("pensar", 3));
        // botones del menu de MCA (comando.<id>)
        ACCIONES.put("comando.adopt", new Accion("saludar", 3));
        ACCIONES.put("comando.sethome", new Accion("saludar", 2));
    }

    public static synchronized void load() {
        defaults();
        Path f = file();
        try {
            if (Files.isRegularFile(f)) {
                JsonObject root = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
                JsonObject acc = root.getAsJsonObject("acciones");
                if (acc != null) {
                    for (String k : acc.keySet()) {
                        JsonObject o = acc.getAsJsonObject(k);
                        ACCIONES.put(k, new Accion(o.has("animacion") ? o.get("animacion").getAsString() : "",
                                o.has("segundos") ? o.get("segundos").getAsInt() : 0));
                    }
                }
            }
            save();
        } catch (Exception e) {
            AnnisNpc.LOGGER.warn("No se pudo leer {}", f, e);
        }
    }

    public static synchronized void save() {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("_ayuda", "Cada accion de MCA pone una animacion de Annis NPC (actividad integrada, "
                    + "emote de Emotecraft o movimiento grabado/importado). segundos = 0: mientras dure el estado. "
                    + "animacion vacia = no hacer nada. Claves: hablando, trabajo.<fishing|harvesting|chopping|hunting|prospecting>, "
                    + "dormir, promesa, compromiso, boda, procrear, divorcio, dialogo.<pregunta>, reaccion.<positiva|negativa>, comando.<boton>.");
            JsonObject acc = new JsonObject();
            for (Map.Entry<String, Accion> e : ACCIONES.entrySet()) {
                JsonObject o = new JsonObject();
                o.addProperty("animacion", e.getValue().animacion());
                o.addProperty("segundos", e.getValue().segundos());
                acc.add(e.getKey(), o);
            }
            root.add("acciones", acc);
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(root));
        } catch (Exception e) {
            AnnisNpc.LOGGER.warn("No se pudo guardar {}", file(), e);
        }
    }

    public static synchronized Map<String, Accion> acciones() {
        return new LinkedHashMap<>(ACCIONES);
    }

    public static synchronized void setAccion(String key, String anim, int segundos) {
        ACCIONES.put(key.toLowerCase(Locale.ROOT), new Accion(anim, Math.max(0, segundos)));
        save();
    }

    private static synchronized Accion get(String key) {
        return ACCIONES.get(key);
    }

    // ---------------------------------------------------------------- eventos

    /** Momento puntual: pone la animacion configurada durante sus segundos. */
    public static void event(VillagerEntityMCA v, String key) {
        Accion a = get(key);
        if (a == null || a.animacion().isEmpty()) {
            return;
        }
        int secs = a.segundos() > 0 ? a.segundos() : 3;
        EntityLinks.play(v, a.animacion(), secs * 20);
        Seen s = SEEN.computeIfAbsent(v.getUUID(), u -> new Seen());
        s.busyUntil = v.level().getGameTime() + secs * 20L;
        s.state = "";
    }

    /** Desde el mixin de los dialogos de MCA. */
    public static void onDialogue(VillagerEntityMCA v, String question) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        if (get("dialogo." + q) != null) {
            event(v, "dialogo." + q);
            return;
        }
        int dot = q.indexOf('.');
        event(v, "dialogo." + (dot > 0 ? q.substring(0, dot) : q));
    }

    public static void onReaction(VillagerEntityMCA v, boolean positive, boolean negative) {
        if (positive) {
            event(v, "reaccion.positiva");
        } else if (negative) {
            event(v, "reaccion.negativa");
        }
    }

    public static void onCommand(VillagerEntityMCA v, String command) {
        if (command == null || command.isEmpty()) {
            return;
        }
        String c = command.toLowerCase(Locale.ROOT);
        int dot = c.indexOf('.');
        event(v, "comando." + (dot > 0 ? c.substring(0, dot) : c));
    }

    // ---------------------------------------------------------------- estados (sondeo)

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0) {
            return;
        }
        for (ServerLevel world : server.getAllLevels()) {
            if (world.players().isEmpty()) {
                continue;
            }
            for (Entity e : world.getAllEntities()) {
                if (e instanceof VillagerEntityMCA v && v.isAlive()) {
                    try {
                        check(world, v);
                    } catch (Throwable t) {
                        // una version distinta de MCA no debe tumbar el servidor
                        AnnisNpc.LOGGER.debug("MCA: {}", t.toString());
                    }
                }
            }
        }
        if (server.getTickCount() % 1200 == 0) {
            SEEN.keySet().removeIf(id -> {
                for (ServerLevel w : server.getAllLevels()) {
                    if (w.getEntity(id) != null) {
                        return false;
                    }
                }
                return true;
            });
        }
    }

    private static void check(ServerLevel world, VillagerEntityMCA v) {
        Seen s = SEEN.computeIfAbsent(v.getUUID(), u -> new Seen());
        RelationshipState rel = v.getRelationships().getRelationshipState();
        boolean procreating = v.getRelationships().isProcreating();
        if (s.rel != null && rel != s.rel) {
            if (rel == RelationshipState.PROMISED) {
                event(v, "promesa");
            } else if (rel == RelationshipState.ENGAGED) {
                event(v, "compromiso");
            } else if (rel == RelationshipState.MARRIED_TO_PLAYER || rel == RelationshipState.MARRIED_TO_VILLAGER) {
                event(v, "boda");
            } else if (s.rel == RelationshipState.MARRIED_TO_PLAYER || s.rel == RelationshipState.MARRIED_TO_VILLAGER
                    || s.rel == RelationshipState.ENGAGED) {
                event(v, "divorcio");
            }
        }
        s.rel = rel;
        if (procreating && !s.procreating) {
            event(v, "procrear");
        }
        s.procreating = procreating;

        if (world.getGameTime() < s.busyUntil) {
            return;
        }
        // estado que dura: hablando > dormir > trabajando (quieto)
        String state = "";
        if (v.getInteractions().getInteractingPlayer().isPresent()) {
            state = "hablando";
        } else if (v.isSleeping()) {
            state = "dormir";
        } else {
            Chore job = v.getVillagerBrain().getCurrentJob();
            if (job != null && job != Chore.NONE && v.getDeltaMovement().horizontalDistanceSqr() < 0.0004) {
                state = "trabajo." + job.name().toLowerCase(Locale.ROOT).replace("prospect", "prospecting")
                        .replace("harvest", "harvesting").replace("chop", "chopping").replace("hunt", "hunting")
                        .replace("fish", "fishing");
            }
        }
        Accion a = state.isEmpty() ? null : get(state);
        String wanted = a == null ? "" : a.animacion();
        if (!state.equals(s.state)) {
            String current = EntityLinks.currentAnim(v);
            Accion old = s.state.isEmpty() ? null : get(s.state);
            boolean oursPlaying = old != null && current.equals(old.animacion());
            if (!wanted.isEmpty()) {
                EntityLinks.play(v, wanted, a.segundos() > 0 ? a.segundos() * 20 : 0);
            } else if (oursPlaying) {
                EntityLinks.stop(v);
            }
            s.state = state;
        }
    }

    // ---------------------------------------------------------------- conversion

    /**
     * Convierte un NPC Annis en persona de MCA: mismo sitio, nombre, equipo y aspecto (skin, modelo,
     * articulaciones, accesorios, Figura). Slim = mujer, normal = hombre. Devuelve el aldeano o null.
     */
    public static VillagerEntityMCA convert(AnnisNpcEntity npc) {
        if (!(npc.level() instanceof ServerLevel world)) {
            return null;
        }
        VillagerFactory f = VillagerFactory.newVillager(world)
                .withGender(npc.isSlim() ? Gender.FEMALE : Gender.MALE)
                .withPosition(npc);
        if (npc.hasCustomName()) {
            f = f.withName(npc.getCustomName().getString());
        }
        VillagerEntityMCA v = f.spawn(MobSpawnType.COMMAND);
        if (v == null) {
            return null;
        }
        v.moveTo(npc.getX(), npc.getY(), npc.getZ(), npc.getYRot(), npc.getXRot());
        v.setYHeadRot(npc.getYHeadRot());
        v.setYBodyRot(npc.getVisualRotationYInDegrees());
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!npc.getItemBySlot(slot).isEmpty()) {
                v.setItemSlot(slot, npc.getItemBySlot(slot).copy());
            }
        }
        EntityLinks.setLook(v, EntityLinks.lookOf(npc));
        npc.discard();
        return v;
    }

    /** Para mensajes: si una entidad es aldeano de MCA. */
    public static boolean isVillager(Entity e) {
        return e instanceof VillagerEntityMCA;
    }

    public static void playerTalked(ServerPlayer p) {
        // reservado para futuras reacciones
    }
}
