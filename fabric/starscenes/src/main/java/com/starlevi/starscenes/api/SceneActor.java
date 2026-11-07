package com.starlevi.starscenes.api;

import net.minecraft.world.item.ItemStack;

/**
 * Cualquier entidad que StarScenes sabe dibujar y animar con un modelo de Blockbench: los NPCs de
 * StarScenes y las de otros mods (por ejemplo aldeanos) que quieran usar el mismo motor.
 * <p>
 * Lo que devuelve cada metodo tiene que estar sincronizado con el cliente (EntityData), porque el
 * cliente lo lee para dibujar.
 */
public interface SceneActor {
    int MAIN_HAND = 0, OFF_HAND = 1, HEAD = 2;

    /** Id del modelo (archivo .bbmodel de config/starscenes/modelos, sin extension). */
    String sceneModel();

    /** Id de la skin (config/starscenes/skins), o "" para la del modelo. */
    default String sceneSkin() {
        return "";
    }

    /** Variante de animaciones: idle1, walk2...; 0 = la primera que haya. */
    default int sceneStyle() {
        return 1;
    }

    default float sceneScale() {
        return 1f;
    }

    /** Si gira la cabeza hacia los jugadores cercanos. */
    default boolean sceneLooks() {
        return true;
    }

    /** A cuantos bloques "te ve" (animacion seeplayer). */
    default float sceneRadius() {
        return 5f;
    }

    /**
     * Voz de los dialogos (soniditos al escribirse el texto): "masculina", "femenina", "grave",
     * "aguda", "simple", "robot" o "ninguna".
     */
    default String sceneVoice() {
        return "simple";
    }

    /** Tono de la voz (1 = normal; 0.8 mas grave, 1.2 mas aguda). */
    default float sceneVoicePitch() {
        return 1f;
    }

    /** Item en la mano derecha, la izquierda o la cabeza. */
    default ItemStack sceneItem(int slot) {
        return ItemStack.EMPTY;
    }
}
