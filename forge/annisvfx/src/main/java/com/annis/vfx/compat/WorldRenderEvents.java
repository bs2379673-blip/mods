package com.annis.vfx.compat;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;

public final class WorldRenderEvents {
    private WorldRenderEvents() {
    }

    public interface Listener {
        void render(WorldRenderContext ctx);
    }

    public static final Event<Listener> AFTER_TRANSLUCENT = stage(RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS);
    public static final Event<Listener> AFTER_ENTITIES = stage(RenderLevelStageEvent.Stage.AFTER_ENTITIES);
    public static final Event<Listener> LAST = stage(RenderLevelStageEvent.Stage.AFTER_LEVEL);

    private static Event<Listener> stage(RenderLevelStageEvent.Stage stage) {
        Event<Listener>[] self = new Event[1];
        self[0] = new Event<>(() -> MinecraftForge.EVENT_BUS.addListener((RenderLevelStageEvent e) -> {
            if (e.getStage() != stage) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            WorldRenderContext ctx = new WorldRenderContext(e.getPoseStack(), e.getCamera(), e.getProjectionMatrix(),
                    e.getPartialTick(), mc.renderBuffers().bufferSource(), mc.level);
            for (Listener l : self[0].listeners()) l.render(ctx);
        }));
        return self[0];
    }
}
