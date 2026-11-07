package com.starlevi.starscenes.api;

import com.starlevi.starscenes.net.Net;
import com.starlevi.starscenes.talk.Actions;
import com.starlevi.starscenes.talk.Dialogues;
import com.starlevi.starscenes.talk.PlayerStore;
import com.starlevi.starscenes.talk.Quests;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Lo que StarScenes ofrece a otros mods (por ejemplo el de aldeanos): animar entidades, abrir
 * dialogos, registrar acciones y condiciones nuevas para los dialogos y las misiones, guardar datos
 * por jugador y escuchar cuando alguien toca a un NPC.
 * <p>
 * Todo se llama del lado del servidor.
 */
public final class StarScenesAPI {
    private StarScenesAPI() {
    }

    /** Una accion de dialogo o recompensa: "tipo:valor". El hablante puede ser null. */
    @FunctionalInterface
    public interface Action {
        void run(ServerPlayer player, Entity speaker, String value);
    }

    /** Una condicion para mostrar una opcion de dialogo: "tipo:valor" ("!tipo:valor" la niega). */
    @FunctionalInterface
    public interface Condition {
        boolean test(ServerPlayer player, Entity speaker, String value);
    }

    /** Un %marcador% para los textos de dialogos (por ejemplo los corazones de amistad). */
    @FunctionalInterface
    public interface Placeholder {
        String get(ServerPlayer player, Entity speaker);
    }

    /**
     * Registra un %marcador% para los textos. El marcador especial "titulo" se muestra al lado del
     * nombre del que habla (por ejemplo, los corazones).
     */
    public static void registerPlaceholder(String key, Placeholder placeholder) {
        com.starlevi.starscenes.talk.Actions.registerPlaceholder(key, placeholder);
    }

    /** Alguien toco un NPC (o una entidad de otro mod que avise con {@link #interacted}). */
    @FunctionalInterface
    public interface Interact {
        /** true si ya lo manejaste (no se abre el dialogo ni se anima). */
        boolean onInteract(ServerPlayer player, Entity npc);
    }

    public static final Event<Interact> INTERACT = EventFactory.createArrayBacked(Interact.class, ls -> (p, e) -> {
        for (Interact l : ls) {
            if (l.onInteract(p, e)) {
                return true;
            }
        }
        return false;
    });

    /** Avisa que un jugador toco esta entidad; true si algun mod lo manejo. */
    public static boolean interacted(ServerPlayer player, Entity npc) {
        return INTERACT.invoker().onInteract(player, npc);
    }

    /** Hace que todos los que ven la entidad la animen ("interact", "talk", "wave" o el nombre exacto). */
    public static void play(Entity actor, String animation) {
        Net.Play msg = new Net.Play(actor.getId(), animation);
        for (ServerPlayer p : PlayerLookup.tracking(actor)) {
            ServerPlayNetworking.send(p, msg);
        }
    }

    /** Un globito de texto arriba del personaje (lo ven todos los que estan cerca) por unos segundos. */
    public static void say(Entity actor, String text, float seconds) {
        Net.Say msg = new Net.Say(actor.getId(), text.replace('&', '§'), Math.max(10, (int) (seconds * 20)));
        for (ServerPlayer p : PlayerLookup.tracking(actor)) {
            ServerPlayNetworking.send(p, msg);
        }
    }

    /**
     * Hace que un jugador haga un gesto del modelo humano de StarScenes (wave, hug, kiss, high_five,
     * hold_left...), encima de su animacion normal. Los de ronda siguen hasta los segundos dados (volver
     * a mandarlo los alarga); seconds 0 lo corta.
     */
    public static void playerGesture(ServerPlayer player, String anim, float seconds) {
        Net.Gesture msg = new Net.Gesture(player.getId(), anim, Math.max(0, (int) (seconds * 20)));
        ServerPlayNetworking.send(player, msg);
        for (ServerPlayer p : PlayerLookup.tracking(player)) {
            if (p != player) {
                ServerPlayNetworking.send(p, msg);
            }
        }
    }

    /** Abre un dialogo (config/starscenes/dialogos/&lt;id&gt;.json) con este hablante (o null). */
    public static boolean talk(ServerPlayer player, Entity speaker, String dialogue) {
        return Dialogues.open(player, speaker, dialogue);
    }

    /** Pone una escena (config/starscenes/escenas/&lt;id&gt;.json) para el jugador. */
    public static boolean playScene(ServerPlayer player, Entity speaker, String id) {
        return com.starlevi.starscenes.talk.Cutscenes.play(player, speaker, id);
    }

    /** Pone una escena armada en el momento (mismo formato que los .json de escenas). */
    public static boolean playScene(ServerPlayer player, Entity speaker, String id, com.google.gson.JsonObject scene) {
        return com.starlevi.starscenes.talk.Cutscenes.play(player, speaker, id, scene);
    }

    /** Si el jugador esta viendo una escena. */
    public static boolean inScene(ServerPlayer player) {
        return com.starlevi.starscenes.talk.Cutscenes.playing(player);
    }

    /** Registra una accion nueva para dialogos y recompensas, ej. "regalo" o "casarse". */
    public static void registerAction(String type, Action action) {
        Actions.register(type, action);
    }

    /** Registra una condicion nueva, ej. "amistad" para "amistad:>=50". */
    public static void registerCondition(String type, Condition condition) {
        Actions.registerCondition(type, condition);
    }

    /** Corre una lista de acciones como las de los dialogos ("dar:minecraft:bread 3", "mision:leña"...). */
    public static void run(ServerPlayer player, Entity speaker, java.util.List<String> actions) {
        Actions.runAll(player, speaker, actions);
    }

    /** Prueba una condicion como las de los dialogos. */
    public static boolean test(ServerPlayer player, Entity speaker, String condition) {
        return Actions.test(player, speaker, condition);
    }

    /** Marcas y numeros que se guardan por jugador (y se pueden usar desde los dialogos). */
    public static boolean flag(ServerPlayer player, String name) {
        return PlayerStore.of(player).flags.contains(name);
    }

    public static void setFlag(ServerPlayer player, String name, boolean on) {
        PlayerStore.setFlag(player, name, on);
    }

    public static int number(ServerPlayer player, String name) {
        return PlayerStore.of(player).vars.getOrDefault(name, 0);
    }

    public static void setNumber(ServerPlayer player, String name, int value) {
        PlayerStore.setVar(player, name, value);
    }

    /** Estado de una mision para este jugador: "nueva", "activa", "lista", "hecha" o "no existe". */
    public static String quest(ServerPlayer player, String quest) {
        return Quests.state(player, quest);
    }
}
