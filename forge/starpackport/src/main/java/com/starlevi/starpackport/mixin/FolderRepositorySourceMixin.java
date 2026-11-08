package com.starlevi.starpackport.mixin;

import com.starlevi.starpackport.StarPackPort;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;

/** Cuando el juego abre un pack de la carpeta resourcepacks, si hace falta usa la copia convertida. */
@Mixin(FolderRepositorySource.class)
public abstract class FolderRepositorySourceMixin {
    @Inject(method = "detectPackResources", at = @At("RETURN"), cancellable = true)
    private static void starpackport$wrap(Path path, boolean builtin, CallbackInfoReturnable<Pack.ResourcesSupplier> cir) {
        Pack.ResourcesSupplier original = cir.getReturnValue();
        if (original == null || !StarPackPort.isResourcePack(path)) {
            return;
        }
        cir.setReturnValue(name -> {
            Path conv = StarPackPort.converted(path);
            if (conv != null) {
                return new PathPackResources(name, conv, builtin);
            }
            return original.open(name);
        });
    }
}
