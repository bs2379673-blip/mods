package __PKG__;

import net.minecraft.client.Minecraft;

/** Como el DeltaTracker de 1.21: la fraccion de tick del fotograma. */
public final class DeltaTracker {
    public static final DeltaTracker INSTANCE = new DeltaTracker();

    public float getGameTimeDeltaPartialTick(boolean ignoreFreeze) {
        return Minecraft.getInstance().getPartialTick();
    }

    public float getGameTimeDeltaTicks() {
        return Minecraft.getInstance().getDeltaFrameTime();
    }
}
