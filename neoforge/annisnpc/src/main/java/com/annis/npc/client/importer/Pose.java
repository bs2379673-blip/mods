package com.annis.npc.client.importer;

import java.util.HashMap;
import java.util.Map;

/** Pose de un fotograma: posiciones de las articulaciones por rol y la rotacion global de la cabeza. */
final class Pose {
    final Map<String, double[]> pos = new HashMap<>();
    double[] headRot = V.identity();
}
