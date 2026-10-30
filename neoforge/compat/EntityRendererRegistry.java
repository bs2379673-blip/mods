package __PKG__;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import java.util.function.Supplier;

public final class EntityRendererRegistry {
    private EntityRendererRegistry() {
    }

    /** En Forge el tipo de entidad existe recien despues de registrarse: se pasa como Supplier. */
    public static <E extends Entity> void register(Supplier<? extends EntityType<? extends E>> type, EntityRendererProvider<E> provider) {
        Compat.modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> e.registerEntityRenderer(type.get(), provider));
    }
}
