package __PKG__;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

import java.util.ArrayList;
import java.util.List;

public final class KeyBindingHelper {
    private static final List<KeyMapping> KEYS = new ArrayList<>();
    private static boolean hooked;

    private KeyBindingHelper() {
    }

    public static synchronized KeyMapping registerKeyBinding(KeyMapping key) {
        if (!hooked) {
            hooked = true;
            Compat.modBus.addListener((RegisterKeyMappingsEvent e) -> KEYS.forEach(e::register));
        }
        KEYS.add(key);
        return key;
    }

    public static InputConstants.Key getBoundKeyOf(KeyMapping key) {
        return key.getKey();
    }
}
