package com.starlevi.starvillagers;

import com.starlevi.starscenes.api.StarScenesAPI;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.function.Function;

/**
 * StarVillagers: los aldeanos de Minecraft pasan a ser personas (con nombre, genes y skin armada
 * por capas), dibujadas y animadas con StarScenes, y se habla con ellos con los dialogos de
 * StarScenes. Siguen siendo aldeanos de Minecraft: comercian, trabajan y los protegen los guardias.
 * Propiedad de Starlevi. Las texturas son de Minecraft Comes Alive (GPL-3.0).
 */
public final class StarVillagers implements ModInitializer {
    public static final String MOD_ID = "starvillagers";
    public static final Logger LOGGER = LoggerFactory.getLogger("StarVillagers");

    public static final AttachmentType<Identity> IDENTITY = AttachmentRegistry.create(id("identidad"),
            b -> b.persistent(Identity.CODEC).syncWith(Identity.STREAM_CODEC, AttachmentSyncPredicate.all()));

    /** La textura armada de un aldeano (la pone el cliente; en el servidor no se usa). */
    public static Function<Villager, Identifier> texture = v -> null;

    private static final Random RANDOM = new Random();

    /**
     * Modelo de EMF que usan los aldeanos si tus paquetes lo traen ("player" = el de jugador de tu
     * paquete de animaciones), o "" para usar siempre el modelo de StarScenes.
     * Se cambia en config/starvillagers.json.
     */
    public static String emfModel = "player";

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public static Identity identity(Villager v) {
        return ((AttachmentTarget) (Object) v).getAttached(IDENTITY);
    }

    /** El oficio para elegir la ropa ("child" si es chico). */
    public static String profession(Villager v) {
        if (v.isBaby()) {
            return "child";
        }
        return v.getVillagerData().profession().unwrapKey().map(k -> k.identifier().getPath()).orElse("none");
    }

    /** Le da identidad si no tiene, y la ropa de su oficio. */
    public static void ensure(Villager v) {
        if (!Looks.ready()) {
            return;
        }
        Identity id = identity(v);
        String prof = profession(v);
        com.starlevi.starvillagers.social.Social.mind(v);
        if (id == null) {
            id = Looks.random(RANDOM, prof);
            ((AttachmentTarget) (Object) v).setAttached(IDENTITY, id);
            if (!v.hasCustomName()) {
                v.setCustomName(Component.literal(id.name()));
            }
        } else {
            String c = Looks.clothes(id, prof);
            if (!c.equals(id.clothes())) {
                ((AttachmentTarget) (Object) v).setAttached(IDENTITY, id.withClothes(c));
            }
        }
    }

    @Override
    public void onInitialize() {
        ServerEntityEvents.ENTITY_LOAD.register((e, level) -> {
            if (e instanceof Villager v) {
                ensure(v);
            }
        });
        // tocarlo: se charla (con el dialogo "aldeano"); agachado se comercia directo
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!(entity instanceof Villager v) || hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown() || v.isBaby() || v.isSleeping()
                    || v.isTrading() || level.isClientSide() || !(player instanceof ServerPlayer sp)) {
                return InteractionResult.PASS;
            }
            ensure(v);
            if (!StarScenesAPI.interacted(sp, v) && !StarScenesAPI.talk(sp, v, "aldeano")) {
                return InteractionResult.PASS;
            }
            v.getLookControl().setLookAt(player);
            return InteractionResult.SUCCESS;
        });
        // acciones para los dialogos
        StarScenesAPI.registerAction("comerciar", (p, s, v) -> {
            if (s instanceof Villager vil) {
                StarScenesAPI.run(p, s, java.util.List.of("cerrar"));
                vil.mobInteract(p, InteractionHand.MAIN_HAND);
            }
        });
        StarScenesAPI.registerCondition("aldeana", (p, s, v) -> s instanceof Villager vil && identity(vil) != null && identity(vil).female());
        writeDialogue();
        readConfig();
        com.starlevi.starvillagers.social.Social.init();
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (s.getTickCount() % 1200 == 0) {
                com.starlevi.starvillagers.social.Social.moodTick(s);
            }
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(com.starlevi.starvillagers.server.Greetings::tick);
    }

    private static void readConfig() {
        Path f = FabricLoader.getInstance().getConfigDir().resolve("starvillagers.json");
        try {
            if (!Files.exists(f)) {
                Files.writeString(f, """
                        {
                          // Modelo de EMF de tus paquetes de recursos para los aldeanos ("player" = el de jugador,
                          // con las animaciones de tu paquete). "" = usar siempre el modelo de StarScenes.
                          "modelo_emf": "player"
                        }
                        """, StandardCharsets.UTF_8);
            }
            var r = new com.google.gson.stream.JsonReader(new java.io.StringReader(Files.readString(f, StandardCharsets.UTF_8)));
            r.setStrictness(com.google.gson.Strictness.LENIENT);
            var o = com.google.gson.JsonParser.parseReader(r).getAsJsonObject();
            if (o.has("modelo_emf")) {
                emfModel = o.get("modelo_emf").getAsString();
            }
        } catch (Exception e) {
            LOGGER.warn("StarVillagers: no pude leer starvillagers.json: {}", e.toString());
        }
    }

    /** El dialogo de charla de los aldeanos (se crea una vez; despues se puede editar). */
    private static void writeDialogue() {
        Path f = FabricLoader.getInstance().getConfigDir().resolve("starscenes").resolve("dialogos").resolve("aldeano.json");
        try {
            // la charla vieja (sin amistad ni regalos) se guarda aparte y se pone la nueva
            if (Files.exists(f) && !Files.readString(f, StandardCharsets.UTF_8).contains("anim_")) {
                Files.move(f, f.resolveSibling("aldeano_viejo.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ignored) {
        }
        if (Files.exists(f)) {
            return;
        }
        try (var in = StarVillagers.class.getResourceAsStream("/starvillagers_dialogos/aldeano.json")) {
            if (in != null) {
                Files.createDirectories(f.getParent());
                Files.write(f, in.readAllBytes());
            }
        } catch (Exception e) {
            LOGGER.warn("StarVillagers: no pude crear el dialogo de los aldeanos: {}", e.toString());
        }
    }
}
