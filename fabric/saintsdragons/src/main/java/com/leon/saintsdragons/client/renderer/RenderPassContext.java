package com.leon.saintsdragons.client.renderer;

import java.util.ArrayDeque;
import java.util.Deque;

public final class RenderPassContext {
    private static final ThreadLocal<Deque<Integer>> EXTRACTION_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    private RenderPassContext() {
    }

    public static void beginExtraction(int dragonId) {
        EXTRACTION_STACK.get().push(dragonId);
    }

    public static void endExtraction() {
        Deque<Integer> stack = EXTRACTION_STACK.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
        if (stack.isEmpty()) {
            EXTRACTION_STACK.remove();
        }
    }

    /** Port 1.21.11: con GeckoLib 5 cada dibujo de un dragon es "el principal" salvo en la pasada de sombras. */
    public static boolean isExtractionAllowedNow() {
        return !ShaderPassCompatibility.isIrisShadowPass();
    }

    public static boolean isExtractionAllowed(int dragonId) {
        Deque<Integer> stack = EXTRACTION_STACK.get();
        if (stack.isEmpty()) {
            return isExtractionAllowedNow();
        }
        return !stack.isEmpty()
                && stack.peek() == dragonId
                && !ShaderPassCompatibility.isIrisShadowPass();
    }
}
