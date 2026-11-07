package com.starlevi.starbody;

import java.util.BitSet;

/**
 * Los rasgos marcados sobre una skin de 64x64: que pixeles son pelo y cuanto se mueve. Se guardan
 * por skin (no por jugador), asi cualquier personaje con esa skin los usa: jugadores y aldeanos.
 */
public final class Traits {
    public static final int SIZE = 64;
    /** 0 suave, 1 normal, 2 mucho. */
    public int motion = 1;
    /** Ojos para Just Expressions: 0 no, 1 de 1 pixel de alto, 2 de 2; y la fila de la cara (desde arriba) donde empiezan. */
    public int eyes, eyeRow = 4;
    /** Cejas: 0 no, 1 en la capa de adentro, 2 en la de afuera; y su fila. */
    public int brows, browRow = 3;
    public final BitSet hair = new BitSet(SIZE * SIZE);

    public boolean hair(int u, int v) {
        return u >= 0 && v >= 0 && u < SIZE && v < SIZE && this.hair.get(v * SIZE + u);
    }

    public void hair(int u, int v, boolean on) {
        if (u >= 0 && v >= 0 && u < SIZE && v < SIZE) {
            this.hair.set(v * SIZE + u, on);
        }
    }

    public boolean empty() {
        return this.hair.isEmpty() && this.eyes == 0 && this.brows == 0;
    }

    public byte[] encode() {
        byte[] bits = this.hair.toByteArray();
        byte[] out = new byte[6 + SIZE * SIZE / 8];
        out[0] = 2;
        out[1] = (byte) this.motion;
        out[2] = (byte) this.eyes;
        out[3] = (byte) this.eyeRow;
        out[4] = (byte) this.brows;
        out[5] = (byte) this.browRow;
        System.arraycopy(bits, 0, out, 6, Math.min(bits.length, out.length - 6));
        return out;
    }

    public static Traits decode(byte[] b) {
        Traits t = new Traits();
        if (b == null || b.length < 2 || b[0] < 1 || b[0] > 2) {
            return t;
        }
        t.motion = Math.max(0, Math.min(2, b[1]));
        int head = 2;
        if (b[0] == 2 && b.length >= 6) {
            t.eyes = Math.max(0, Math.min(2, b[2]));
            t.eyeRow = b[3];
            t.brows = Math.max(0, Math.min(2, b[4]));
            t.browRow = b[5];
            head = 6;
        }
        byte[] bits = new byte[b.length - head];
        System.arraycopy(b, head, bits, 0, bits.length);
        t.hair.or(BitSet.valueOf(bits));
        return t;
    }

    public Traits copy() {
        return decode(encode());
    }
}
