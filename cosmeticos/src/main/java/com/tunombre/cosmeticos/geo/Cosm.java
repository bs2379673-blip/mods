package com.tunombre.cosmeticos.geo;

/** un cosmetico: dibuja su malla en el espacio de un hueso (x lateral, y arriba, z delante, en bloques) */
public interface Cosm {
    /** hueso al que va pegado */
    Bone bone();

    void build(Ctx c);

    enum Bone {
        /** origen en la base del cuello, gira con la cabeza */
        HEAD,
        /** origen en la base del cuello, gira con el torso (la espalda cuelga hacia -y, -z) */
        CHEST,
        /** origen en la cadera */
        PELVIS
    }
}
