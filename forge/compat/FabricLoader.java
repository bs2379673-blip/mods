package __PKG__;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

public final class FabricLoader {
    private static final FabricLoader INSTANCE = new FabricLoader();

    public static FabricLoader getInstance() {
        return INSTANCE;
    }

    public boolean isModLoaded(String id) {
        // en Forge algunos mods tienen otro id
        if ("iris".equals(id)) {
            return check("iris") || check("oculus");
        }
        if ("entity_model_features".equals(id)) {
            return check("entity_model_features");
        }
        return check(id);
    }

    private static boolean check(String id) {
        ModList list = ModList.get();
        if (list != null) {
            return list.isLoaded(id);
        }
        return FMLLoader.getLoadingModList() != null && FMLLoader.getLoadingModList().getModFileById(id) != null;
    }

    public Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    public Path getGameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    public boolean isDevelopmentEnvironment() {
        return !FMLLoader.isProduction();
    }
}
