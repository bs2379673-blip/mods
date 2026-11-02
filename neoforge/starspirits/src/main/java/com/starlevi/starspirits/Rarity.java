package com.starlevi.starspirits;

import net.minecraft.ChatFormatting;

import java.util.Locale;

/** Rarezas: color del haz de extraccion y probabilidad base (se cambia en config/starspirits/config.json). */
public enum Rarity {
    COMUN("Común", 0x7FD957, ChatFormatting.GREEN, 55),
    RARO("Raro", 0x4FB3FF, ChatFormatting.AQUA, 28),
    EPICO("Épico", 0xC46BFF, ChatFormatting.LIGHT_PURPLE, 13),
    LEGENDARIO("Legendario", 0xFFC93C, ChatFormatting.GOLD, 4);

    public final String label;
    public final int color;
    public final ChatFormatting chat;
    public final int defaultWeight;

    Rarity(String label, int color, ChatFormatting chat, int defaultWeight) {
        this.label = label;
        this.color = color;
        this.chat = chat;
        this.defaultWeight = defaultWeight;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public float r() {
        return (this.color >> 16 & 255) / 255f;
    }

    public float g() {
        return (this.color >> 8 & 255) / 255f;
    }

    public float b() {
        return (this.color & 255) / 255f;
    }

    public static Rarity parse(String s) {
        if (s == null) {
            return COMUN;
        }
        String k = s.trim().toLowerCase(Locale.ROOT).replace("é", "e").replace("ú", "u");
        return switch (k) {
            case "raro", "rare" -> RARO;
            case "epico", "epic" -> EPICO;
            case "legendario", "legendary" -> LEGENDARIO;
            default -> COMUN;
        };
    }
}
