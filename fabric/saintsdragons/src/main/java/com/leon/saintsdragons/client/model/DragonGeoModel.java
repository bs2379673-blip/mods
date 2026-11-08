package com.leon.saintsdragons.client.model;

import com.leon.saintsdragons.common.SaintsDragonsCommon;
import com.leon.saintsdragons.client.animation.EntityAnimationPlayback;
import com.leon.saintsdragons.server.entity.base.DragonEntity;
import com.leon.saintsdragons.server.entity.base.RideableDragonBase;
import com.leon.saintsdragons.client.renderer.DragonRiderAttachments;
import com.leon.saintsdragons.client.renderer.ShaderPassCompatibility;
import net.minecraft.resources.Identifier;
import software.bernie.geckolib.cache.model.BakedGeoModel;
import com.leon.saintsdragons.client.compat.geo.LegacyAnimState;

public abstract class DragonGeoModel<T extends DragonEntity> extends com.leon.saintsdragons.client.compat.geo.LegacyGeoModel<T> {
    protected final Identifier model;
    protected final Identifier babyModel;
    protected final Identifier animation;
    protected final Identifier babyAnimation;
    protected final Identifier maleTexture;
    protected final Identifier femaleTexture;
    protected final Identifier babyMaleTexture;
    protected final Identifier babyFemaleTexture;


    protected DragonGeoModel(String dragonId) {
        this(dragonId, true);
    }

    protected DragonGeoModel(String dragonId, boolean hasBabyResources) {
        this(SaintsDragonsCommon.rl(dragonId), hasBabyResources);
    }

    protected DragonGeoModel(Identifier dragonId) {
        this(dragonId, true);
    }

    protected DragonGeoModel(Identifier dragonId, boolean hasBabyResources) {
        this(dragonId, Resources.conventional(dragonId, hasBabyResources));
    }

    protected DragonGeoModel(Identifier dragonId, Resources resources) {
        super(dragonId);
        this.model = resources.model();
        this.babyModel = resources.babyModel();
        this.animation = resources.animation();
        this.babyAnimation = resources.babyAnimation();
        this.maleTexture = resources.maleTexture();
        this.femaleTexture = resources.femaleTexture();
        this.babyMaleTexture = resources.babyMaleTexture();
        this.babyFemaleTexture = resources.babyFemaleTexture();
    }

    public record Resources(Identifier model, Identifier babyModel,
                            Identifier animation, Identifier babyAnimation,
                            Identifier maleTexture, Identifier femaleTexture,
                            Identifier babyMaleTexture, Identifier babyFemaleTexture) {
        public Resources {
            java.util.Objects.requireNonNull(model, "model");
            java.util.Objects.requireNonNull(babyModel, "babyModel");
            java.util.Objects.requireNonNull(animation, "animation");
            java.util.Objects.requireNonNull(babyAnimation, "babyAnimation");
            java.util.Objects.requireNonNull(maleTexture, "maleTexture");
            java.util.Objects.requireNonNull(femaleTexture, "femaleTexture");
            java.util.Objects.requireNonNull(babyMaleTexture, "babyMaleTexture");
            java.util.Objects.requireNonNull(babyFemaleTexture, "babyFemaleTexture");
        }

        public static Resources conventional(Identifier id, boolean babies) {
            String path = id.getPath();
            String name = path.substring(path.lastIndexOf('/') + 1);
            Identifier model = resource(id, "geo/entity/" + path + "/" + name + ".geo.json");
            Identifier animation = resource(id, "animations/entity/" + path + "/" + name + ".animation.json");
            Identifier male = resource(id, "textures/entity/" + path + "/" + name + ".png");
            Identifier female = resource(id, "textures/entity/" + path + "/" + name + "_female.png");
            return new Resources(model,
                    babies ? resource(id, "geo/entity/" + path + "/baby_" + name + ".geo.json") : model,
                    animation,
                    babies ? resource(id, "animations/entity/" + path + "/baby_" + name + ".animation.json") : animation,
                    male, female,
                    babies ? resource(id, "textures/entity/" + path + "/baby_" + name + ".png") : male,
                    babies ? resource(id, "textures/entity/" + path + "/baby_" + name + "_female.png") : female);
        }

        private static Identifier resource(Identifier id, String path) {
            return Identifier.fromNamespaceAndPath(id.getNamespace(), path);
        }
    }

    @Override
    public Identifier getLegacyModelResource(T entity) {
        return entity != null && entity.isBaby() ? babyModel : model;
    }

    @Override
    public Identifier getLegacyTextureResource(T entity) {
        if (entity == null) {
            return maleTexture;
        }
        if (entity.isBaby()) {
            return getBabyTexture(entity);
        }
        return getAdultTexture(entity);
    }

    @Override
    public Identifier getLegacyAnimationResource(T entity) {
        return entity != null && entity.isBaby() ? babyAnimation : animation;
    }

    protected Identifier getAdultTexture(T entity) {
        if (entity.hasCustomTextureVariant()) {
            return entity.getCustomAdultTextureResource(entity.isFemale());
        }
        return entity.isFemale() ? femaleTexture : maleTexture;
    }

    protected Identifier getBabyTexture(T entity) {
        return entity.isFemale() ? babyFemaleTexture : babyMaleTexture;
    }
}
