package com.leon.saintsdragons.fabric.config;

import com.leon.saintsdragons.fabric.config.lite.ConfigData;
import com.leon.saintsdragons.fabric.config.lite.Config;
import com.leon.saintsdragons.fabric.config.lite.ConfigEntry;

@Config(name = "saintsdragons/client/clientcommon")
public final class SaintsDragonsFabricClientConfig implements ConfigData {
    @ConfigEntry.Category("client")
    @ConfigEntry.Gui.Tooltip
    public boolean raevyxBeamFirstPersonEnabled = true;

    @ConfigEntry.Category("client")
    @ConfigEntry.Gui.Tooltip
    public boolean firstPersonBankingCameraEnabled = true;

    @ConfigEntry.Category("client")
    @ConfigEntry.Gui.Tooltip
    public boolean thirdPersonBankingCameraEnabled = true;

    @ConfigEntry.Category("client")
    @ConfigEntry.Gui.Tooltip
    public boolean diveCameraWobbleEnabled = true;

    @ConfigEntry.Category("client")
    @ConfigEntry.Gui.Tooltip
    public boolean diveSpeedLinesEnabled = true;

    @ConfigEntry.Category("client")
    @ConfigEntry.Gui.Tooltip
    public boolean genericDiveLoopEnabled = true;

    @ConfigEntry.Category("client")
    @ConfigEntry.Gui.Tooltip
    public int swarmBattleMusicVolume = 100;
}
