package com.annis.interact;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** config/annisinteract.json */
public final class InteractConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static InteractConfig instance;

    /** Al recibir dano mortal, el jugador cae en vez de morir. */
    public boolean caidos = true;
    /** Los NPC Annis tambien caen (si no estan protegidos). */
    public boolean npcsCaen = true;
    /** Segundos hasta morir si nadie ayuda. */
    public int segundosCaido = 60;
    /** Vida (medios corazones) al levantarse. */
    public int vidaAlLevantar = 6;
    /** Los monstruos dejan en paz a los caidos. */
    public boolean monstruosIgnoranCaidos = true;
    /** NPC que te siguen y aldeanos de MCA que te quieren vienen a levantarte. */
    public boolean ayudaAutomatica = true;
    /** Corazones de MCA necesarios para que un aldeano venga a ayudar. */
    public int corazonesParaAyudar = 40;
    /** Segundos para aceptar una invitacion. */
    public int segundosInvitacion = 20;
    /** Corazones de MCA necesarios por interaccion (si falta alguna se usa la del mod). */
    public Map<String, Integer> corazones = new LinkedHashMap<>();
    /** Los aldeanos de MCA casados o comprometidos con el jugador aceptan todo. */
    public boolean parejaAceptaTodo = true;

    private InteractConfig() {
    }

    public static InteractConfig get() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    public static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("annisinteract.json");
    }

    public static void load() {
        InteractConfig c = null;
        try {
            if (Files.isRegularFile(file())) {
                c = GSON.fromJson(Files.readString(file()), InteractConfig.class);
            }
        } catch (Exception e) {
            AnnisInteract.LOGGER.warn("No se pudo leer {}: {}", file(), e.toString());
        }
        if (c == null) {
            c = new InteractConfig();
        }
        if (c.corazones == null) {
            c.corazones = new LinkedHashMap<>();
        }
        for (Interaction i : Interaction.all()) {
            c.corazones.putIfAbsent(i.id(), i.hearts());
        }
        instance = c;
        try {
            Files.writeString(file(), GSON.toJson(c));
        } catch (Exception e) {
            AnnisInteract.LOGGER.warn("No se pudo guardar {}: {}", file(), e.toString());
        }
    }

    public int hearts(Interaction i) {
        Integer v = this.corazones.get(i.id());
        return v == null ? i.hearts() : v;
    }
}
