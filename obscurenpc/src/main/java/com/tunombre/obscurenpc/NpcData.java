package com.tunombre.obscurenpc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.List;

/** Toda la configuracion de un NPC. Se guarda como JSON dentro del NBT y se copia tal cual a plantillas y spawners. */
public class NpcData {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    // ---- identidad ----
    public String id = "";                 // clave para buscar dialogos / conocimiento / rutinas (si esta vacia se usa el nombre)
    public String nombre = "NPC";
    public boolean mostrar_nombre = true;
    public String color_nombre = "FFFFFF";
    public String skin = "";               // nombre de un jugador, o "espacio:ruta" de una textura de resource pack
    public boolean brazos_finos = false;
    public float tamano = 1.0F;

    // ---- comportamiento ----
    public String mirar = "jugadores";     // no | jugadores | todos
    public float mirar_rango = 8F;
    public boolean quieto = false;         // no se mueve de su sitio (puede girar la cabeza)
    public boolean vagar = false;
    public int vagar_radio = 6;
    public boolean fijar_giro = false;     // mantiene el cuerpo mirando a "giro"
    public float giro = 0F;
    public int postura = 0;                // ver Stances
    public boolean invulnerable = false;
    public boolean empujable = true;
    public String hostil = "ninguno";      // ninguno | jugadores | mobs | ambos
    public boolean represalia = true;      // se defiende si lo atacan
    public String faccion = "";            // los de la misma faccion no se atacan entre si
    public boolean suelta_equipo = false;
    public boolean persistente = true;
    public double regeneracion = 0.0;      // vida por segundo

    // ---- atributos ----
    public double vida = 20.0;
    public double velocidad = 0.25;
    public double dano = 3.0;
    public double armadura = 0.0;
    public double empuje_res = 0.0;
    public double rango_vision = 24.0;

    // ---- equipo (ItemStack en SNBT; las plantillas y spawners lo llevan consigo) ----
    public String eq_mano = "";
    public String eq_mano2 = "";
    public String eq_cabeza = "";
    public String eq_pecho = "";
    public String eq_piernas = "";
    public String eq_pies = "";

    // ---- drops (lo que suelta al morir, ademas del equipo) ----
    public static class Drop {
        public String item = "";           // ItemStack en SNBT
        public float prob = 100F;          // 0 a 100
        public int min = 1;
        public int max = 1;
    }

    public List<Drop> drops = new ArrayList<>();
    public boolean drops_solo_jugador = false;   // solo suelta si lo mato un jugador
    public int xp = 0;                           // experiencia que suelta

    // ---- ordenes por chat ("sigueme", "quedate aqui", "protegeme", "defiende esta zona") ----
    public boolean acepta_ordenes = false;
    public String ordenes_si = "";               // condicion para obedecer (igual que en los dialogos: afinidad>=3, bandera, g:bandera...)
    public int zona_radio = 14;                  // radio de "defiende esta zona"

    // ---- armas (TACZ) ----
    public int arma_alcance = 24;
    public int arma_cadencia = 6;          // ticks entre disparos
    public float arma_dispersion = 2.0F;   // grados de error al apuntar
    public boolean arma_infinita = true;

    // ---- dialogo ----
    public String dialogo = "";            // archivo config/obscurenpc/dialogos/<dialogo>.json
    public String conocimiento = "";       // ids de config/obscurenpc/conocimiento separados por coma (vacio = id del NPC + _mundo)
    public boolean charla_ia = true;       // responde a lo que se le escribe en el chat
    public List<String> lineas = new ArrayList<>(); // dialogo simple si no hay archivo
    public boolean burbuja = true;
    public boolean chat = true;
    public int velocidad_texto = 100;      // % (mas alto = mas rapido)
    public String gestos = "talk_a,talk_b,talk_c,talk_d,talk_e";
    public boolean mirar_al_hablar = true;
    public boolean saludo_auto = false;
    public int saludo_rango = 5;
    public String saludo = "";
    public String personalidad = "neutral"; // neutral | amable | seria | burlona | fria

    // ---- charla entre NPCs ----
    public boolean charla_npc = false;
    public List<String> charla_lineas = new ArrayList<>();

    // ---- sonidos ----
    public String snd_idle = "";
    public String snd_hurt = "";
    public String snd_death = "";
    public String snd_talk = "";
    public float snd_vol = 1F;
    public float snd_tono = 1F;
    public int snd_idle_cada = 300;

    // ---- rutina / objetivos ----
    public String rutina = "";
    public boolean rutina_auto = false;

    public static NpcData fromJson(String json) {
        try {
            NpcData d = GSON.fromJson(json, NpcData.class);
            if (d == null) return new NpcData();
            if (d.lineas == null) d.lineas = new ArrayList<>();
            if (d.charla_lineas == null) d.charla_lineas = new ArrayList<>();
            if (d.drops == null) d.drops = new ArrayList<>();
            return d;
        } catch (RuntimeException e) {
            return new NpcData();
        }
    }

    public String toJson() { return GSON.toJson(this); }

    public NpcData copy() { return fromJson(toJson()); }

    public String key() { return (id == null || id.isBlank() ? nombre : id).toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_]", "_"); }
}
