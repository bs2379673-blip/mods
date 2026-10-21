package com.tunombre.jefes;

import com.tunombre.jefes.core.Model;
import com.tunombre.jefes.core.Spec;

/** Un jefe cargado: definicion, modelo y textura (bytes del PNG). */
public final class Boss {
    public final String specJson, modelJson;
    public final byte[] png;
    public final Spec spec;
    public final Model model;

    public Boss(String specJson, String modelJson, byte[] png) {
        this.specJson = specJson;
        this.modelJson = modelJson;
        this.png = png;
        this.spec = new Spec(specJson);
        this.model = Model.parse(modelJson);
    }

    public String id() { return spec.id; }
}
