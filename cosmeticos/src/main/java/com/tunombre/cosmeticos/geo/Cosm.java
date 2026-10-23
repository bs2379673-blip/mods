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
        PELVIS,
        /**
         * origen en la cadera pero sin seguir la inclinación del torso: solo la posición y el giro del cuerpo. Para auras y efectos que
         * rodean al jugador (si siguieran el hueso se desfasarían con cada animación de Epic Fight).
         */
        ROOT,
        /** origen en la palma de la mano derecha; y sube por el brazo (con el brazo colgando, y = arriba), z hacia delante */
        HAND_R
    }
}
