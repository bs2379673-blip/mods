package com.starlevi.starscenes.emf;

import com.google.gson.JsonObject;

/** Arma un cubo como los del modelo de jugador de Minecraft (caja con UV de skin). */
final class VanillaBox {
    private VanillaBox() {
    }

    static void add(JemModel.Part p, JsonObject box) {
        JemModel.boxInto(p, box);
    }
}
