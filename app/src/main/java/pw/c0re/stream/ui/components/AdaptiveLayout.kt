package pw.c0re.stream.ui.components

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Remembers the current width bucket. Preview, tests and other non-Activity contexts
 * deliberately fall back to the compact layout instead of crashing.
 */
@Composable
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
fun rememberWindowWidthSizeClass(): WindowWidthSizeClass {
    val context = LocalContext.current
    // Recomputed on each composition so configuration and multi-window changes are honoured.
    return if (context is Activity) calculateWindowSizeClass(context).widthSizeClass
    else WindowWidthSizeClass.Compact
}

/** Remembers the current height bucket; compact height forces the compact layout. */
@Composable
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
fun rememberWindowHeightSizeClass(): WindowHeightSizeClass {
    val context = LocalContext.current
    return if (context is Activity) calculateWindowSizeClass(context).heightSizeClass
    else WindowHeightSizeClass.Compact
}

/** Horizontal page padding that grows with the available width (phone -> tablet -> TV). */
@Composable
fun rememberResponsivePadding(): Dp {
    val widthClass = rememberWindowWidthSizeClass()
    return when (widthClass) {
        WindowWidthSizeClass.Medium -> 24.dp
        WindowWidthSizeClass.Expanded -> 32.dp
        else -> 16.dp
    }
}

/**
 * Picks a layout composition for the current width class. Reusing the same slot for
 * medium and expanded keeps navigation and state intact when a window is resized.
 */
@Composable
fun AdaptivePane(
    modifier: Modifier = Modifier,
    compact: @Composable () -> Unit,
    medium: @Composable () -> Unit = compact,
    expanded: @Composable () -> Unit = medium,
) {
    Box(modifier) {
        if (rememberWindowHeightSizeClass() == WindowHeightSizeClass.Compact) {
            compact()
        } else {
            when (rememberWindowWidthSizeClass()) {
                WindowWidthSizeClass.Compact -> compact()
                WindowWidthSizeClass.Medium -> medium()
                WindowWidthSizeClass.Expanded -> expanded()
                else -> compact()
            }
        }
    }
}
