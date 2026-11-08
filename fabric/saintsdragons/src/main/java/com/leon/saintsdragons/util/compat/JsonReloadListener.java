package com.leon.saintsdragons.util.compat;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/**
 * Port 1.21.11: el SimpleJsonResourceReloadListener de 1.20.1 (lee todos los .json de una carpeta de datos
 * y los entrega como JSON). En 1.21.11 el de Minecraft pide un Codec; este conserva la forma vieja.
 */
public abstract class JsonReloadListener extends SimplePreparableReloadListener<Map<Identifier, JsonElement>> {
    private final String directory;

    protected JsonReloadListener(Gson gson, String directory) {
        this.directory = directory;
    }

    @Override
    protected Map<Identifier, JsonElement> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<Identifier, JsonElement> out = new HashMap<>();
        FileToIdConverter conv = FileToIdConverter.json(this.directory);
        for (Map.Entry<Identifier, Resource> e : conv.listMatchingResources(manager).entrySet()) {
            Identifier id = conv.fileToId(e.getKey());
            try (Reader r = e.getValue().openAsReader()) {
                out.put(id, JsonParser.parseReader(r));
            } catch (Exception ex) {
                com.leon.saintsdragons.common.SaintsDragonsCommon.LOGGER.error("Couldn't parse data file {} from {}", id, e.getKey(), ex);
            }
        }
        return out;
    }
}
