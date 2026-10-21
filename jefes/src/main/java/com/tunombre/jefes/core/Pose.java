package com.tunombre.jefes.core;

/** Rotaciones/posiciones actuales de todos los cubos (partiendo del reposo del modelo) y su matriz en el mundo. */
public final class Pose {
    public final Model m;
    public final float[] rot, pos;      // n*3 (radianes / pixeles)
    private final Mat4[] world;

    public Pose(Model m) {
        this.m = m;
        rot = m.brot.clone();
        pos = m.bpos.clone();
        world = new Mat4[m.n];
        for (int i = 0; i < m.n; i++) world[i] = new Mat4();
    }

    public void reset() {
        System.arraycopy(m.brot, 0, rot, 0, rot.length);
        System.arraycopy(m.bpos, 0, pos, 0, pos.length);
    }

    /** suma una animacion del modelo en el instante {@code timeMs}; w = peso; skip = cubos que no se tocan (puede ser null) */
    public void addClip(Model.Clip c, double timeMs, float w, boolean[] skip) {
        addClipStep(c, (float) ((timeMs % c.dur) / c.dur * c.frames), w, skip);
    }

    /** igual que addClip pero con el paso (0..frames) dado: sirve para animaciones guiadas (cabeza) o de una sola vez */
    public void addClipStep(Model.Clip c, float step, float w, boolean[] skip) {
        if (w <= 0.0001F) return;
        for (int t = 0; t < c.cube.length; t++) {
            int i = c.cube[t];
            if (skip != null && skip[i]) continue;
            float[] r = c.rot[t], p = c.pos[t];
            for (int k = 0; k < 3; k++) {
                if (r != null) { float v = c.sample(r, k, step); rot[i * 3 + k] = c.add ? rot[i * 3 + k] + v * w : rot[i * 3 + k] + (v - rot[i * 3 + k]) * w; }
                if (p != null) { float v = c.sample(p, k, step); pos[i * 3 + k] = c.add ? pos[i * 3 + k] + v * w : pos[i * 3 + k] + (v - pos[i * 3 + k]) * w; }
            }
        }
    }

    public void addRot(int cubeId, float x, float y, float z) {
        int i = m.idx(cubeId);
        rot[i * 3] += x; rot[i * 3 + 1] += y; rot[i * 3 + 2] += z;
    }

    public void addPos(int cubeId, float x, float y, float z) {
        int i = m.idx(cubeId);
        pos[i * 3] += x; pos[i * 3 + 1] += y; pos[i * 3 + 2] += z;
    }

    /** acerca la rotacion del cubo a la dada (peso w): para posturas fijas (agarre) */
    public void setRot(int cubeId, float x, float y, float z, float w) {
        int i = m.idx(cubeId);
        rot[i * 3] += (x - rot[i * 3]) * w; rot[i * 3 + 1] += (y - rot[i * 3 + 1]) * w; rot[i * 3 + 2] += (z - rot[i * 3 + 2]) * w;
    }

    /** matrices de los cubos en el mundo; {@code root} lleva ya la escala (1/16 * escala del jefe) y el giro de la entidad */
    public Mat4[] world(Mat4 root) {
        for (int i : m.order()) {
            Mat4 w = world[i];
            if (m.parent[i] >= 0) w.set(world[m.parent[i]]);
            else {
                w.set(root);
                float[] ro = m.roots.get(m.rootId[i]);
                if (ro != null) w.translate(ro[0], ro[1], ro[2]);
            }
            w.translate(pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2]);
            w.rotateZYX(rot[i * 3], rot[i * 3 + 1], rot[i * 3 + 2]);
        }
        return world;
    }
}
