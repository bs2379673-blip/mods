package com.leon.saintsdragons.client.compat.geo;

import software.bernie.geckolib.animation.state.BoneSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Port 1.21.11 (GeckoLib 5): el hueso de GeckoLib 4 que usan los modelos del mod para mover la cabeza,
 * la cola, las alas, etc. En GeckoLib 5 cada hueso tiene su valor base y un "snapshot" con lo que le
 * suma la animacion de este cuadro; aca se trabaja con el valor total como en GeckoLib 4.
 */
public final class GeoBone {
    private final software.bernie.geckolib.cache.model.GeoBone bone;
    private final BoneSnapshot snap;

    GeoBone(software.bernie.geckolib.cache.model.GeoBone bone, BoneSnapshot snap) {
        this.bone = bone;
        this.snap = snap;
    }

    public software.bernie.geckolib.cache.model.GeoBone handle() {
        return this.bone;
    }

    public String getName() {
        return this.bone.name();
    }

    public GeoBone getParent() {
        software.bernie.geckolib.cache.model.GeoBone p = this.bone.parent();
        return p == null ? null : LegacyGeoModel.wrap(p);
    }

    public List<GeoBone> getChildBones() {
        List<GeoBone> out = new ArrayList<>();
        for (software.bernie.geckolib.cache.model.GeoBone c : this.bone.children()) {
            GeoBone w = LegacyGeoModel.wrap(c);
            if (w != null) {
                out.add(w);
            }
        }
        return out;
    }

    public float getRotX() {
        return this.bone.baseRotX() + this.snap.getRotX();
    }

    public float getRotY() {
        return this.bone.baseRotY() + this.snap.getRotY();
    }

    public float getRotZ() {
        return this.bone.baseRotZ() + this.snap.getRotZ();
    }

    public void setRotX(float v) {
        this.snap.setRotX(v - this.bone.baseRotX());
    }

    public void setRotY(float v) {
        this.snap.setRotY(v - this.bone.baseRotY());
    }

    public void setRotZ(float v) {
        this.snap.setRotZ(v - this.bone.baseRotZ());
    }

    public void updateRotation(float x, float y, float z) {
        setRotX(x);
        setRotY(y);
        setRotZ(z);
    }

    public float getPosX() {
        return this.snap.getTranslateX();
    }

    public float getPosY() {
        return this.snap.getTranslateY();
    }

    public float getPosZ() {
        return this.snap.getTranslateZ();
    }

    public void setPosX(float v) {
        this.snap.setTranslateX(v);
    }

    public void setPosY(float v) {
        this.snap.setTranslateY(v);
    }

    public void setPosZ(float v) {
        this.snap.setTranslateZ(v);
    }

    public void updatePosition(float x, float y, float z) {
        this.snap.setTranslation(x, y, z);
    }

    public float getScaleX() {
        return this.snap.getScaleX();
    }

    public float getScaleY() {
        return this.snap.getScaleY();
    }

    public float getScaleZ() {
        return this.snap.getScaleZ();
    }

    public void setScaleX(float v) {
        this.snap.setScaleX(v);
    }

    public void setScaleY(float v) {
        this.snap.setScaleY(v);
    }

    public void setScaleZ(float v) {
        this.snap.setScaleZ(v);
    }

    public void updateScale(float x, float y, float z) {
        this.snap.setScale(x, y, z);
    }

    public float getPivotX() {
        return this.bone.pivotX();
    }

    public float getPivotY() {
        return this.bone.pivotY();
    }

    public float getPivotZ() {
        return this.bone.pivotZ();
    }

    public boolean isHidden() {
        return this.snap.isHidden();
    }

    public void setHidden(boolean hidden) {
        this.snap.skipRender(hidden);
    }

    public void setChildrenHidden(boolean hidden) {
        this.snap.skipChildrenRender(hidden);
    }

    public boolean isHidingChildren() {
        return this.snap.areChildrenHidden();
    }

    public Initial getInitialSnapshot() {
        return new Initial(this.bone);
    }

    public void resetStateChanges() {
    }

    public void markRotationAsChanged() {
    }

    public void markPositionAsChanged() {
    }

    public void markScaleAsChanged() {
    }

    /** Los valores del modelo sin animar (lo que en GeckoLib 4 era el "snapshot inicial"). */
    public static final class Initial {
        private final software.bernie.geckolib.cache.model.GeoBone bone;

        Initial(software.bernie.geckolib.cache.model.GeoBone bone) {
            this.bone = bone;
        }

        public float getRotX() {
            return this.bone.baseRotX();
        }

        public float getRotY() {
            return this.bone.baseRotY();
        }

        public float getRotZ() {
            return this.bone.baseRotZ();
        }

        public float getOffsetX() {
            return 0;
        }

        public float getOffsetY() {
            return 0;
        }

        public float getOffsetZ() {
            return 0;
        }

        public float getPosX() {
            return 0;
        }

        public float getPosY() {
            return 0;
        }

        public float getPosZ() {
            return 0;
        }

        public float getScaleX() {
            return 1;
        }

        public float getScaleY() {
            return 1;
        }

        public float getScaleZ() {
            return 1;
        }
    }
}
