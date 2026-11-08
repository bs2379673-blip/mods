package com.leon.saintsdragons.client.compat.geo;

import com.leon.saintsdragons.util.compat.EntityModelData;
import com.leon.saintsdragons.util.compat.SDTickets;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.model.BakedGeoModel;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.BoneSnapshots;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.RenderPassInfo;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Port 1.21.11: un modelo de GeckoLib 5 con la forma de los de GeckoLib 4 que usa el mod: rutas viejas
 * (geo/..., animations/...), elegir modelo/textura segun la entidad, y mover huesos en setCustomAnimations
 * (lo llama el renderer en el momento en que GeckoLib 5 deja ajustar los huesos).
 */
public abstract class LegacyGeoModel<T extends GeoAnimatable> extends GeoModel<T> {
    public static final DataTicket<Object> ANIMATABLE = DataTicket.create("saintsdragons_animatable", Object.class);
    private static final Pattern PREFIX = Pattern.compile("^(geckolib/)?((animations/)|(models/)|(geo/))?");
    private static final Pattern SUFFIX = Pattern.compile("((\\.geo)|((\\.animation)s?))?(\\.json)$");

    private record Ctx(BakedGeoModel model, BoneSnapshots snapshots) {
    }

    private static final ThreadLocal<Ctx> CURRENT = new ThreadLocal<>();

    private final Identifier defaultModel;
    private final Identifier defaultTexture;
    private final Identifier defaultAnimation;

    protected LegacyGeoModel() {
        this(null);
    }

    /** Como DefaultedEntityGeoModel de GeckoLib 4: entity/&lt;ruta&gt;. */
    protected LegacyGeoModel(Identifier assetSubpath) {
        this(assetSubpath, "entity");
    }

    protected LegacyGeoModel(Identifier assetSubpath, String kind) {
        if (assetSubpath == null) {
            this.defaultModel = this.defaultTexture = this.defaultAnimation = null;
        } else {
            String ns = assetSubpath.getNamespace(), p = assetSubpath.getPath();
            this.defaultModel = Identifier.fromNamespaceAndPath(ns, kind + "/" + p);
            this.defaultAnimation = Identifier.fromNamespaceAndPath(ns, kind + "/" + p);
            this.defaultTexture = Identifier.fromNamespaceAndPath(ns, "textures/" + kind + "/" + p + ".png");
        }
    }

    public static Identifier normalize(Identifier id) {
        if (id == null) {
            return null;
        }
        String path = id.getPath();
        path = PREFIX.matcher(path).replaceFirst("");
        path = SUFFIX.matcher(path).replaceFirst("");
        return path.equals(id.getPath()) ? id : Identifier.fromNamespaceAndPath(id.getNamespace(), path);
    }

    // ------------------------------------------------------------ lo que sobreescriben los modelos del mod

    public Identifier getLegacyModelResource(T animatable) {
        return this.defaultModel;
    }

    public Identifier getLegacyTextureResource(T animatable) {
        return this.defaultTexture;
    }

    public Identifier getLegacyAnimationResource(T animatable) {
        return this.defaultAnimation;
    }

    /** Mover huesos despues de la animacion (como en GeckoLib 4). */
    public void setCustomAnimations(T animatable, long instanceId, LegacyAnimState<T> state) {
    }

    // ------------------------------------------------------------ GeckoLib 5

    @SuppressWarnings("unchecked")
    protected T animatableOf(GeoRenderState state) {
        return state == null ? null : (T) state.getGeckolibData(ANIMATABLE);
    }

    @Override
    public final Identifier getModelResource(GeoRenderState state) {
        return normalize(getLegacyModelResource(animatableOf(state)));
    }

    @Override
    public final Identifier getTextureResource(GeoRenderState state) {
        return getLegacyTextureResource(animatableOf(state));
    }

    @Override
    public final Identifier getAnimationResource(T animatable) {
        return normalize(getLegacyAnimationResource(animatable));
    }

    @Override
    public void addAdditionalStateData(T animatable, Object relatedObject, GeoRenderState state) {
        super.addAdditionalStateData(animatable, relatedObject, state);
        state.addGeckolibData(ANIMATABLE, animatable);
        if (animatable instanceof Entity e) {
            state.addGeckolibData(SDTickets.ENTITY, e);
        }
        if (relatedObject instanceof ItemStack st) {
            state.addGeckolibData(SDTickets.ITEMSTACK, st);
        } else if (relatedObject instanceof Entity e) {
            state.addGeckolibData(SDTickets.ENTITY, e);
        }
        Entity e = state.getGeckolibData(SDTickets.ENTITY);
        if (e instanceof LivingEntity le) {
            Float pt = state.getGeckolibData(DataTickets.PARTIAL_TICK);
            float p = pt == null ? 1 : pt;
            float body = Mth.rotLerp(p, le.yBodyRotO, le.yBodyRot);
            float head = Mth.rotLerp(p, le.yHeadRotO, le.yHeadRot);
            state.addGeckolibData(SDTickets.ENTITY_MODEL_DATA, new EntityModelData(le.isPassenger(), le.isBaby(),
                    Mth.wrapDegrees(head - body), Mth.lerp(p, le.xRotO, le.getXRot())));
        }
    }

    // ------------------------------------------------------------ huesos

    public Optional<GeoBone> getBone(String name) {
        Ctx c = CURRENT.get();
        if (c == null) {
            return Optional.empty();
        }
        return c.snapshots().get(name).map(s -> new GeoBone(s.getBone(), s));
    }

    static GeoBone wrap(software.bernie.geckolib.cache.model.GeoBone bone) {
        Ctx c = CURRENT.get();
        if (c == null) {
            return null;
        }
        return c.snapshots().get(bone.name()).map(s -> new GeoBone(bone, s)).orElse(null);
    }

    /** Lo llama el renderer desde adjustModelBonesForRender. */
    @SuppressWarnings("unchecked")
    public static <A extends GeoAnimatable> void runCustomAnimations(GeoModel<A> model, RenderPassInfo<?> info, BoneSnapshots snapshots) {
        if (!(model instanceof LegacyGeoModel<?> legacy)) {
            return;
        }
        GeoRenderState state = info.renderState();
        Object a = state.getGeckolibData(ANIMATABLE);
        if (a == null) {
            return;
        }
        Float pt = state.getGeckolibData(DataTickets.PARTIAL_TICK);
        Long id = state.getGeckolibData(DataTickets.ANIMATABLE_INSTANCE_ID);
        Ctx prev = CURRENT.get();
        CURRENT.set(new Ctx(info.model(), snapshots));
        try {
            LegacyGeoModel<GeoAnimatable> m = (LegacyGeoModel<GeoAnimatable>) legacy;
            m.setCustomAnimations((GeoAnimatable) a, id == null ? 0 : id, new LegacyAnimState<>((GeoAnimatable) a, pt == null ? 1 : pt, state));
        } catch (RuntimeException ex) {
            // un hueso que falta no corta el dibujo
        } finally {
            if (prev == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(prev);
            }
        }
    }
}
