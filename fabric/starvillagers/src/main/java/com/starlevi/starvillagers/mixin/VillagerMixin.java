package com.starlevi.starvillagers.mixin;

import com.starlevi.starscenes.api.SceneActor;
import com.starlevi.starvillagers.Identity;
import com.starlevi.starvillagers.StarVillagers;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Los aldeanos de Minecraft pasan a ser actores de StarScenes (se dibujan y se animan con su modelo
 * humano) y cambian de ropa cuando cambian de oficio o crecen.
 */
@Mixin(Villager.class)
public abstract class VillagerMixin implements SceneActor {
    @org.spongepowered.asm.mixin.Unique
    private static final List<String> STARVILLAGERS$SOURCES = List.of("scenes_quests");

    @org.spongepowered.asm.mixin.Unique
    private Villager starvillagers$self() {
        return (Villager) (Object) this;
    }

    @Override
    public String sceneModel() {
        Identity id = StarVillagers.identity((Villager) (Object) this);
        return id != null && id.female() && !((Villager) (Object) this).isBaby() ? "humano_mujer" : "humano";
    }

    @Override
    public boolean sceneChest() {
        Identity id = StarVillagers.identity((Villager) (Object) this);
        return id != null && id.female() && !((Villager) (Object) this).isBaby();
    }

    @Override
    public int sceneStyle() {
        return 0; // cada uno elige al azar entre sus animaciones de reposo
    }

    @Override
    public float sceneScale() {
        Identity id = StarVillagers.identity(starvillagers$self());
        float s = id == null ? 0.95f : id.height();
        return starvillagers$self().isBaby() ? s * 0.55f : s;
    }

    @Override
    public float sceneRadius() {
        return 4f;
    }

    @Override
    public ItemStack sceneItem(int slot) {
        return slot == MAIN_HAND ? starvillagers$self().getMainHandItem() : ItemStack.EMPTY;
    }

    @Override
    public String sceneVoice() {
        Identity id = StarVillagers.identity(starvillagers$self());
        return id != null && id.female() ? "femenina" : "masculina";
    }

    @Override
    public float sceneVoicePitch() {
        Identity id = StarVillagers.identity(starvillagers$self());
        float p = id == null ? 1f : id.voice();
        return starvillagers$self().isBaby() ? p * 1.3f : p;
    }

    @Override
    public Identifier sceneTexture() {
        return StarVillagers.texture.apply(starvillagers$self());
    }

    @Override
    public String sceneEmfModel() {
        return StarVillagers.emfModel == null || StarVillagers.emfModel.isEmpty() ? null : StarVillagers.emfModel;
    }

    @Override
    public List<String> sceneAnimationSources() {
        return STARVILLAGERS$SOURCES;
    }

    @Inject(method = "setVillagerData", at = @At("TAIL"))
    private void starvillagers$data(VillagerData data, CallbackInfo ci) {
        if (!starvillagers$self().level().isClientSide() && StarVillagers.identity(starvillagers$self()) != null) {
            StarVillagers.ensure(starvillagers$self());
        }
    }

    @Inject(method = "ageBoundaryReached", at = @At("TAIL"))
    private void starvillagers$grew(CallbackInfo ci) {
        if (!starvillagers$self().level().isClientSide()) {
            StarVillagers.ensure(starvillagers$self());
        }
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "getBreedOffspring(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/AgeableMob;)Lnet/minecraft/world/entity/npc/villager/Villager;",
            at = @org.spongepowered.asm.mixin.injection.At("RETURN"))
    private void starvillagers$baby(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.AgeableMob other,
                                    org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Villager> cir) {
        Villager child = cir.getReturnValue();
        if (child != null && other instanceof Villager o && StarVillagers.identity((Villager) (Object) this) != null) {
            com.starlevi.starvillagers.family.Family.born(child, (Villager) (Object) this, o, null);
        }
    }
}
