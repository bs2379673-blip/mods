package com.annis.npc.mixin;

import com.annis.npc.client.punchy.BedrockClip;
import com.annis.npc.client.punchy.PunchyClips;
import com.annis.npc.net.EmoteSyncPayload;
import com.annis.npc.compat.ClientPlayNetworking;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/**
 * Si esta Punchy Moves: cuando lanzas un emote, Annis avisa al servidor para que los demas tambien
 * lo vean (Punchy solo lo muestra en tu pantalla). Si Punchy no esta, esto no hace nada.
 */
@Pseudo
@Mixin(targets = "punchymoves.client.PlayerAnimationEngine", remap = false)
public abstract class PunchyEmoteMixin {
    private static Field annisnpc$emoteField;
    private static boolean annisnpc$looked;

    @Inject(method = "triggerEmote", at = @At("RETURN"), remap = false, require = 0)
    private static void annisnpc$sync(String emote, CallbackInfo ci) {
        try {
            String name = emote;
            if (!annisnpc$looked) {
                annisnpc$looked = true;
                try {
                    annisnpc$emoteField = Class.forName("punchymoves.client.PlayerAnimationEngine").getDeclaredField("emoteAnimation");
                    annisnpc$emoteField.setAccessible(true);
                } catch (ReflectiveOperationException ignored) {
                    annisnpc$emoteField = null;
                }
            }
            if (annisnpc$emoteField != null) {
                Object v = annisnpc$emoteField.get(null);
                if (v instanceof String s && !s.isEmpty()) {
                    name = s;
                }
            }
            BedrockClip clip = PunchyClips.get(name);
            if (clip == null && emote != null) {
                clip = PunchyClips.get(emote);
                name = emote;
            }
            if (clip == null || !ClientPlayNetworking.canSend(EmoteSyncPayload.ID)) {
                return;
            }
            int ticks = clip.loop == BedrockClip.Loop.LOOP ? 0 : Math.max(1, Math.round(clip.length * 20));
            ClientPlayNetworking.send(new EmoteSyncPayload(PunchyClips.PREFIX + name.toLowerCase(java.util.Locale.ROOT), ticks));
        } catch (Throwable ignored) {
            // nunca romper Punchy por esto
        }
    }
}
