package com.tunombre.cosmeticos.geo;

/** cadena de segmentos con muelles: cada segmento se desvia (yaw/pitch) segun el movimiento y arrastra al siguiente */
public final class Chain {
    public final int n;
    public final float[] bx, bz, vx, vz;
    public float lim = 0.7F;

    public Chain(int n) { this.n = n; bx = new float[n]; bz = new float[n]; vx = new float[n]; vz = new float[n]; }

    /**
     * @param dt ticks transcurridos
     * @param dx,dz empuje objetivo (radianes por segmento al final de la cadena) en los ejes lado/arriba de la cadena
     * @param k rigidez, c amortiguacion, follow cuanto arrastra el segmento anterior
     */
    public void step(float dt, float dx, float dz, float k, float c, float follow) {
        int sub = Math.max(1, (int) Math.ceil(dt / 0.5F));
        float h = dt / sub;
        for (int s = 0; s < sub; s++) {
            for (int i = 0; i < n; i++) {
                float w = (i + 1F) / n;
                float tx = dx * w, tz = dz * w;
                float px = i > 0 ? vx[i - 1] : 0, pz = i > 0 ? vz[i - 1] : 0;
                float ax = -k * (bx[i] - tx) - c * vx[i] + follow * (px - vx[i]);
                float az = -k * (bz[i] - tz) - c * vz[i] + follow * (pz - vz[i]);
                vx[i] += ax * h; vz[i] += az * h;
                bx[i] += vx[i] * h; bz[i] += vz[i] * h;
                bx[i] = Noise.clamp(bx[i], -lim, lim); bz[i] = Noise.clamp(bz[i], -lim, lim);
            }
        }
    }

    /** igual que step pero con objetivos propios por segmento */
    public void stepTo(float dt, float[] tx, float[] tz, float k, float c, float follow) {
        int sub = Math.max(1, (int) Math.ceil(dt / 0.5F));
        float h = dt / sub;
        for (int s = 0; s < sub; s++) {
            for (int i = 0; i < n; i++) {
                float px = i > 0 ? vx[i - 1] : 0, pz = i > 0 ? vz[i - 1] : 0;
                float ax = -k * (bx[i] - tx[i]) - c * vx[i] + follow * (px - vx[i]);
                float az = -k * (bz[i] - tz[i]) - c * vz[i] + follow * (pz - vz[i]);
                vx[i] += ax * h; vz[i] += az * h;
                bx[i] += vx[i] * h; bz[i] += vz[i] * h;
                bx[i] = Noise.clamp(bx[i], -lim, lim); bz[i] = Noise.clamp(bz[i], -lim, lim);
            }
        }
    }
}
