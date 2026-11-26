package fr.prisme.player.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * One focus recipe for rows, tabs and chips: a lighter fill (via [FocusStyle.Fill] as the
 * Surface's focusedContainerColor), a 2dp light outline and a subtle scale. A selected item
 * keeps its accent fill; focus adds the outline and scale on top, so "selected" and
 * "selected + focused" always look different.
 */
object FocusStyle {
    val Fill = Color(0xFF415575)
    val Border = PrismPalette.TextPrimary
    const val RowScale = 1.02f
    const val ChipScale = 1.06f
}

/**
 * Scale read in the draw/layer phase instead of composition. `Modifier.scale(animated)`
 * reads the animated value while composing, so the whole calling composable recomposes
 * on every animation frame; this variant only re-runs the layer block. Same visual result.
 */
fun Modifier.deferredScale(scale: () -> Float): Modifier = graphicsLayer {
    val s = scale()
    scaleX = s
    scaleY = s
}

/** Alpha counterpart of [deferredScale]. */
fun Modifier.deferredAlpha(alpha: () -> Float): Modifier = graphicsLayer {
    this.alpha = alpha()
}

fun Modifier.tvFocus(focused: Boolean, shape: Shape, focusedScale: Float = FocusStyle.RowScale): Modifier =
    composed {
        val scale by animateFloatAsState(
            targetValue = if (focused) focusedScale else 1f,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "tv-focus-scale",
        )
        this
            .deferredScale { scale }
            .then(if (focused) Modifier.border(2.dp, FocusStyle.Border, shape) else Modifier)
    }
