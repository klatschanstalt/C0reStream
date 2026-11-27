package pw.c0re.stream.ui.components

import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import pw.c0re.stream.ui.theme.BrandMotion

/**
 * True when the user has disabled/strongly reduced animations in Android accessibility
 * settings. Reads the platform animation scales (API 30+) so no extra dependency is
 * needed and Compose's own animation system remains in control.
 */
@Composable
fun rememberBrandReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        fun scale(name: String): Float = runCatching {
            Settings.Global.getFloat(context.contentResolver, name, 1f)
        }.getOrDefault(1f)
        scale(Settings.Global.ANIMATOR_DURATION_SCALE) == 0f ||
            scale(Settings.Global.TRANSITION_ANIMATION_SCALE) == 0f
    }
}

/** Hardware-friendly alpha animation for simple enter/exit state changes. */
@Composable
fun rememberBrandAlpha(visible: Boolean): State<Float> {
    val reducedMotion = rememberBrandReducedMotion()
    return animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = BrandMotion.spec(reducedMotion, BrandMotion.DurationMedium),
        label = "BrandAlpha",
    )
}
