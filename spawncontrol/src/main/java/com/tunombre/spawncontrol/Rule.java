package com.tunombre.spawncontrol;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Regla de spawn de un mob (o de todos los de un mod). Sin regla = comportamiento normal. */
public class Rule {
    /** spawn natural permitido */
    public volatile boolean enabled = true;
    /** si true, solo puede aparecer dentro de alguna zona permitida */
    public volatile boolean onlyZones = false;
    public List<Zone> zones = new CopyOnWriteArrayList<>();
    /** dia (del mundo, empezando en 1) en que se libera solo; -1 = sin programar */
    public volatile int releaseDay = -1;
    public volatile boolean released = false;
    /** maximo de ejemplares vivos de este mob (0 = sin limite) */
    public volatile int max = 0;
    /** porcentaje de spawns que se dejan pasar (0-100) */
    public volatile int prob = 100;
    /** tipos de spawn que controla esta regla (nombres de MobSpawnType) */
    public Set<String> types = ConcurrentHashMap.newKeySet();
    // aviso
    public String aImage = "";
    public float aSecs = 5F;
    public String aMode = "center";
    public String aSound = "";
    public String aText = "";

    public transient AtomicInteger alive = new AtomicInteger();

    public Rule() {
        types.add("NATURAL");
        types.add("CHUNK_GENERATION");
    }

    /** repara los campos tras leer el JSON */
    void fix() {
        if (zones == null) zones = new CopyOnWriteArrayList<>(); else zones = new CopyOnWriteArrayList<>(zones);
        Set<String> t = ConcurrentHashMap.newKeySet();
        if (types != null) t.addAll(types);
        if (t.isEmpty()) { t.add("NATURAL"); t.add("CHUNK_GENERATION"); }
        types = t;
        if (aImage == null) aImage = "";
        if (aMode == null) aMode = "center";
        if (aSound == null) aSound = "";
        if (aText == null) aText = "";
        if (prob < 0 || prob > 100) prob = 100;
        alive = new AtomicInteger();
    }

    public boolean hasAnnouncement() { return !aImage.isEmpty() || !aText.isEmpty(); }
}
