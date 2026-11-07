package com.starlevi.starfest.server;

/** Duracion de un .ogg (Vorbis) leyendo solo las cabeceras: el servidor no necesita decodificar audio. */
public final class OggInfo {
    private OggInfo() {
    }

    public static long durationMs(byte[] d) {
        int rate = sampleRate(d);
        if (rate <= 0) {
            return -1;
        }
        // ultima pagina "OggS": su posicion de granulo es la cantidad total de muestras
        for (int i = d.length - 27; i >= 0; i--) {
            if (d[i] == 'O' && d[i + 1] == 'g' && d[i + 2] == 'g' && d[i + 3] == 'S') {
                long granule = 0;
                for (int b = 7; b >= 0; b--) {
                    granule = granule << 8 | d[i + 6 + b] & 0xFF;
                }
                if (granule > 0) {
                    return granule * 1000L / rate;
                }
            }
        }
        return -1;
    }

    /** Frecuencia de muestreo de la cabecera de identificacion de Vorbis. */
    private static int sampleRate(byte[] d) {
        if (d.length < 64 || d[0] != 'O' || d[1] != 'g' || d[2] != 'g' || d[3] != 'S') {
            return -1;
        }
        int segs = d[26] & 0xFF;
        int p = 27 + segs;
        if (p + 16 > d.length || d[p] != 1 || d[p + 1] != 'v' || d[p + 2] != 'o' || d[p + 3] != 'r') {
            return -1;
        }
        int q = p + 12;
        return d[q] & 0xFF | (d[q + 1] & 0xFF) << 8 | (d[q + 2] & 0xFF) << 16 | (d[q + 3] & 0xFF) << 24;
    }
}
