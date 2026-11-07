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
        return this.hair.isEmpty();
    }

    public byte[] encode() {
        byte[] bits = this.hair.toByteArray();
        byte[] out = new byte[2 + SIZE * SIZE / 8];
        out[0] = 1;
        out[1] = (byte) this.motion;
        System.arraycopy(bits, 0, out, 2, Math.min(bits.length, out.length - 2));
        return out;
    }

    public static Traits decode(byte[] b) {
        Traits t = new Traits();
        if (b == null || b.length < 2 || b[0] != 1) {
            return t;
        }
        t.motion = Math.max(0, Math.min(2, b[1]));
        byte[] bits = new byte[b.length - 2];
        System.arraycopy(b, 2, bits, 0, bits.length);
        t.hair.or(BitSet.valueOf(bits));
        return t;
    }

    public Traits copy() {
        return decode(encode());
    }
}
