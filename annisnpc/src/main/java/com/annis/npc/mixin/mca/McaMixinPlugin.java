package com.annis.npc.mixin.mca;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Los mixins de MCA solo se aplican si MCA esta instalado. */
public class McaMixinPlugin implements IMixinConfigPlugin {
    private static final boolean MCA = FabricLoader.getInstance().isModLoaded("mca");

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return MCA;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        org.slf4j.LoggerFactory.getLogger("annisnpc").info("Enganche con MCA aplicado: {} -> {}",
                mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1), targetClassName);
    }
}
