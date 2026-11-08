package com.leon.saintsdragons.util.compat;

/** Port 1.21.11: el EntityModelData de GeckoLib 4 (lo que mira la cabeza, si esta sentado o es cria). */
public record EntityModelData(boolean isSitting, boolean isChild, float netHeadYaw, float headPitch) {
}
