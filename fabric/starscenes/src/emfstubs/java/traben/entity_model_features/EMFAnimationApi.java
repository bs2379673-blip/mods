package traben.entity_model_features;

import traben.entity_model_features.models.animation.state.EMFEntityRenderState;
import traben.entity_model_features.models.parts.EMFModelPartRoot;

/** Molde para compilar (la clase de verdad es de EMF). */
public interface EMFAnimationApi {
    static boolean registerAnimationHook(EMFAnimationHook hook) throws Exception {
        throw new UnsupportedOperationException();
    }

    abstract class EMFAnimationHook {
        public boolean onAnimationStart(AnimationContext context, boolean isCancelledByHook) {
            return true;
        }

        public void onAnimationEnd(AnimationContext context, boolean wasCancelledByHook) {
        }

        public record AnimationContext(EMFEntityRenderState activeState, EMFModelPartRoot animatingModelRoot) {
        }
    }
}
