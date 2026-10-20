package com.tunombre.visionfx;

import java.util.LinkedHashMap;
import java.util.Map;

/** modos de evento: efectos de pantalla completos y aparte del filtro de color (los activa un admin) */
public final class Modes {
    private Modes() { }

    /** nombre -> id (el id es el que usa el shader) */
    public static final Map<String, Integer> IDS = new LinkedHashMap<>();
    public static final Map<String, String> DESC = new LinkedHashMap<>();

    private static void add(int id, String name, String desc) { IDS.put(name, id); DESC.put(name, desc); }

    static {
        add(1, "vhs", "camara de video VHS: lineas, ruido, color corrido y barra de tracking");
        add(2, "cine_antiguo", "pelicula vieja: sepia que parpadea, grano y rayones");
        add(3, "nocturna", "vision nocturna verde con grano y visor");
        add(4, "termica", "camara termica: calor en azul, rojo y amarillo");
        add(5, "glitch", "señal rota: bloques desplazados, colores separados y destellos");
        add(6, "pesadilla", "latido, pantalla que respira y bordes rojos");
        add(7, "ebrio", "vision doble y ondulacion, mareo");
        add(8, "comic", "dibujo: pocos colores y contornos negros");
        add(9, "gameboy", "consola portatil de 4 verdes y 160x144");
        add(10, "tunel", "vision de tunel: solo se ve bien el centro");
        add(11, "negativo", "colores invertidos que pulsan");
    }

    public static String nameOf(int id) {
        for (var e : IDS.entrySet()) if (e.getValue() == id) return e.getKey();
        return "ninguno";
    }
}
