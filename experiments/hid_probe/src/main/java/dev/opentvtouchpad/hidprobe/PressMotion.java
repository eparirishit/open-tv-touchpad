package dev.opentvtouchpad.hidprobe;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

/**
 * Press feedback for every button: a quick shrink while pressed and a soft spring back on
 * release. Purely visual (no elevation, so no Material shadow). Disabled buttons do not move,
 * and Android's "Remove animations" setting scales these durations to zero.
 */
final class PressMotion {
    private PressMotion() {}

    static void apply(View view) {
        StateListAnimator motion = new StateListAnimator();
        motion.addState(new int[]{android.R.attr.state_pressed, android.R.attr.state_enabled},
                scale(0.93f, 70, new DecelerateInterpolator()));
        motion.addState(new int[]{}, scale(1f, 180, new OvershootInterpolator(2.5f)));
        view.setStateListAnimator(motion);
    }

    private static Animator scale(float to, long millis, android.animation.TimeInterpolator easing) {
        AnimatorSet set = new AnimatorSet();
        set.playTogether(ObjectAnimator.ofFloat(null, View.SCALE_X, to), ObjectAnimator.ofFloat(null, View.SCALE_Y, to));
        set.setDuration(millis);
        set.setInterpolator(easing);
        return set;
    }
}
