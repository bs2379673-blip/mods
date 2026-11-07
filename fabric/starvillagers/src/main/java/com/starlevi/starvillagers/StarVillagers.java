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

    /** El anillo de compromiso (para casarse). */
    public static net.minecraft.world.item.Item RING;

    /**
     * Modelo de EMF que usan los aldeanos si tus paquetes lo traen ("player" = el de jugador de tu
     * paquete de animaciones), o "" para usar siempre el modelo de StarScenes.
     * Se cambia en config/starvillagers.json.
     */
    public static String emfModel = "player";

    /**
     * De donde salen las animaciones de los aldeanos, ademas del modelo humano: config/starscenes/modelos/&lt;nombre&gt;.bbmodel.
     * Con "+" adelante, sus animaciones reemplazan a las que se llaman igual. Se cambia en config/starvillagers.json.
     */
    public static java.util.List<String> animSources = java.util.List.of("+aldeanos", "scenes_quests");

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
            float temp = v.level().getBiome(v.blockPosition()).value().getBaseTemperature();
            id = Looks.random(RANDOM, prof, temp);
            ((AttachmentTarget) (Object) v).setAttached(IDENTITY, id);
            if (!v.hasCustomName()) {
                v.setCustomName(Component.literal(id.name()));
            }
        } else {
            // con "piel": "clara", los que nacieron mas oscuros (de antes) se aclaran
            Identity fixed = Looks.fitSkin(id, RANDOM);
            if (fixed != id) {
                ((AttachmentTarget) (Object) v).setAttached(IDENTITY, fixed);
                id = fixed;
            }
            String c = Looks.clothes(id, prof);
            if (!c.equals(id.clothes()) && !id.clothes().startsWith("biblioteca:")) {
                ((AttachmentTarget) (Object) v).setAttached(IDENTITY, id.withClothes(c));
            }
        }
    }

    @Override
    public void onInitialize() {
        var ringKey = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM, id("anillo"));
        RING = net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.ITEM, ringKey,
                new net.minecraft.world.item.Item(new net.minecraft.world.item.Item.Properties().setId(ringKey).stacksTo(16)
                        .rarity(net.minecraft.world.item.Rarity.RARE)));
        net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(net.minecraft.world.item.CreativeModeTabs.INGREDIENTS)
                .register(e -> e.accept(RING));
        com.starlevi.starvillagers.family.Family.init();
        com.starlevi.starvillagers.look.Wardrobe.init();
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
        com.starlevi.starvillagers.social.Together.init();
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
                          "modelo_emf": "player",
                          // Tono de piel de los aldeanos: "clara" (todos de tez blanca o clara, por el lore del mundo)
                          // o "bioma" (como en MCA: depende del bioma, con gente de otros lados).
                          "piel": "clara",
                          // Tus animaciones de Blockbench (config/starscenes/modelos/<nombre>.bbmodel), con los huesos del
                          // modelo humano. Con "+" adelante reemplazan a las del mod que se llamen igual.
                          "animaciones": ["+aldeanos", "scenes_quests"]
                        }
                        """, StandardCharsets.UTF_8);
            }
            String txt = Files.readString(f, StandardCharsets.UTF_8);
            if (!txt.contains("\"piel\"")) {
                // archivo de una version anterior: se agrega la opcion
                int end = txt.lastIndexOf('}');
                String before = txt.substring(0, end).stripTrailing();
                txt = before + (before.endsWith("{") ? "" : ",") + "\n  // Tono de piel: \"clara\" (todos de tez clara) o \"bioma\" (como en MCA).\n  \"piel\": \"clara\"\n}\n";
                Files.writeString(f, txt, StandardCharsets.UTF_8);
            }
            var r = new com.google.gson.stream.JsonReader(new java.io.StringReader(Files.readString(f, StandardCharsets.UTF_8)));
            r.setStrictness(com.google.gson.Strictness.LENIENT);
            var o = com.google.gson.JsonParser.parseReader(r).getAsJsonObject();
            if (o.has("modelo_emf")) {
                emfModel = o.get("modelo_emf").getAsString();
            }
            if (o.has("animaciones") && o.get("animaciones").isJsonArray()) {
                java.util.List<String> l = new java.util.ArrayList<>();
                o.getAsJsonArray("animaciones").forEach(x -> l.add(x.getAsString()));
                animSources = java.util.List.copyOf(l);
            }
            Looks.lightSkin = !o.has("piel") || !o.get("piel").getAsString().equalsIgnoreCase("bioma");
        } catch (Exception e) {
            LOGGER.warn("StarVillagers: no pude leer starvillagers.json: {}", e.toString());
        }
    }

    /** El dialogo de charla de los aldeanos (se crea una vez; despues se puede editar). */
    private static void writeDialogue() {
        Path f = FabricLoader.getInstance().getConfigDir().resolve("starscenes").resolve("dialogos").resolve("aldeano.json");
        try {
            // la charla vieja (sin amistad ni regalos) se guarda aparte y se pone la nueva
            if (Files.exists(f) && !Files.readString(f, StandardCharsets.UTF_8).contains("familia:")) {
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
