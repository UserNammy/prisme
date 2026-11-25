package fr.prisme.player.ui.theme

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val DRIFT_AMPLITUDE = 0.015f
private const val DRIFT_HALF_CYCLE_MS = 18_000L

/** Drift is quantised to ~1px on a 1080p surface, so the canvas only redraws a few times a second. */
private const val DRIFT_STEPS_PER_UNIT = 2_000f

/** Wake-up interval of the drift clock. The motion is ~3px/s, so 10 updates/s is plenty. */
private const val DRIFT_TICK_MS = 100L

/**
 * The fixed Prism DA background: black with blue clouds in the upper-left and
 * red clouds in the lower-right. Drift is intentionally near-imperceptible on
 * TV; it gives the surface life without spending frames on video playback.
 *
 * Performance notes: the gradients are built once per size (drawWithCache) and only
 * translated afterwards; the drift clock ticks at 10 Hz instead of every vsync and is
 * quantised to whole pixels, so the three full-screen gradients are repainted ~3 times
 * per second instead of 60. With system animations disabled the clouds stay still.
 */
@Composable
fun PrismBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val context = LocalContext.current
    val animationsEnabled = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    val drift = remember { mutableFloatStateOf(-DRIFT_AMPLITUDE) }

    if (animationsEnabled) {
        LaunchedEffect(Unit) {
            val start = withFrameMillis { it }
            while (true) {
                delay(DRIFT_TICK_MS)
                val elapsed = withFrameMillis { it } - start
                drift.floatValue = quantise(driftAt(elapsed))
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PrismPalette.BackgroundDeep)
            .drawWithCache {
                val radiusBase = maxOf(size.width, size.height)
                // Built at drift = 0; the moving clouds are translated at draw time.
                val blue = Brush.radialGradient(
                    colors = listOf(Color(0xB23264FF), Color(0x00205BE0)),
                    center = Offset(size.width * 0.03f, size.height * 0.02f),
                    radius = radiusBase * 0.58f,
                )
                val blueHalo = Brush.radialGradient(
                    colors = listOf(Color(0x7A2850DC), Color.Transparent),
                    center = Offset(size.width * 0.58f, size.height * -0.04f),
                    radius = radiusBase * 0.30f,
                )
                val red = Brush.radialGradient(
                    colors = listOf(Color(0x7AFF3246), Color(0x00DC284F)),
                    center = Offset(size.width * 0.98f, size.height * 1.03f),
                    radius = radiusBase * 0.48f,
                )
                onDrawBehind {
                    val dx = size.width * drift.floatValue
                    // Shift the gradient, not the rectangle: the rect is drawn back at the
                    // origin so no uncovered strip appears at the screen edge.
                    translate(left = dx) { drawRect(blue, topLeft = Offset(-dx, 0f), size = size) }
                    drawRect(blueHalo)
                    translate(left = -dx) { drawRect(red, topLeft = Offset(dx, 0f), size = size) }
                }
            },
    ) {
        content()
    }
}

/** Linear ping-pong between -A and +A, same curve as the former infiniteRepeatable(Reverse). */
internal fun driftAt(elapsedMs: Long): Float {
    val cycle = DRIFT_HALF_CYCLE_MS * 2
    val phase = (elapsedMs % cycle).toFloat() / DRIFT_HALF_CYCLE_MS // 0..2
    val t = if (phase <= 1f) phase else 2f - phase
    return -DRIFT_AMPLITUDE + 2f * DRIFT_AMPLITUDE * t
}

private fun quantise(value: Float): Float =
    (value * DRIFT_STEPS_PER_UNIT).roundToInt() / DRIFT_STEPS_PER_UNIT
