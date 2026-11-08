package com.leon.saintsdragons.fabric.resource;

import com.leon.saintsdragons.common.SaintsDragonsCommon;
import com.leon.saintsdragons.server.loot.DragonChestLootReloadListener;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class FabricDragonChestLootReloadListener implements IdentifiableResourceReloadListener {
    private static final Identifier ID = SaintsDragonsCommon.rl("dragon_chest_loot");
    private final DragonChestLootReloadListener delegate = DragonChestLootReloadListener.getInstance();

    @Override
    public Identifier getFabricId() {
        return ID;
    }

    @Override
    public @NotNull CompletableFuture<Void> reload(PreparableReloadListener.@NotNull PreparationBarrier barrier,
                                                   @NotNull ResourceManager manager,
                                                   @NotNull ProfilerFiller prepareProfiler,
                                                   @NotNull ProfilerFiller applyProfiler,
                                                   @NotNull Executor prepareExecutor,
                                                   @NotNull Executor applyExecutor) {
        return delegate.reload(barrier, manager, prepareProfiler, applyProfiler, prepareExecutor, applyExecutor);
    }
}
