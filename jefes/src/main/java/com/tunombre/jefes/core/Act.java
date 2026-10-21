package com.tunombre.jefes.core;

/** Acciones del jefe (estado sincronizado). Duraciones en ticks; 0 = hasta que el servidor la cambie. */
public enum Act {
    NONE(0), ATTACK_L(18), ATTACK_R(18), SMASH(36), ROAR(56), REACH(16), HOLD(0), KILL(80), THROW(24), LEAP(0), STAGGER(60), SNIFF(120), LAND(14), RECOVER(30);

    public final int ticks;

    Act(int ticks) { this.ticks = ticks; }

    public static final Act[] ALL = values();

    public static Act of(int i) { return i >= 0 && i < ALL.length ? ALL[i] : NONE; }

    /** durante estas acciones el cuerpo adopta una postura fija (igual en servidor y cliente) para que la mano coincida con la victima */
    public boolean stance() { return this == REACH || this == HOLD || this == KILL || this == THROW || this == RECOVER; }
}
