package nl.vanvrouwerff.iptv.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * The fixed Prism DA background: black with blue clouds in the upper-left and
 * red clouds in the lower-right. Drift is intentionally near-imperceptible on
 * TV; it gives the surface life without spending frames on video playback.
 */
@Composable
fun PrismBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "prism-clouds")
    val drift = transition.animateFloat(
        initialValue = -0.015f,
        targetValue = 0.015f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "prism-cloud-drift",
    )

    Box(modifier = modifier.fillMaxSize().background(IptvPalette.BackgroundDeep)) {
        Canvas(Modifier.fillMaxSize()) {
            drawPrismClouds(drift.value)
        }
        content()
    }
}

private fun DrawScope.drawPrismClouds(drift: Float) {
    val blueCentre = Offset(size.width * (0.03f + drift), size.height * 0.02f)
    val redCentre = Offset(size.width * (0.98f - drift), size.height * 1.03f)

    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xB23264FF), Color(0x00205BE0)),
            center = blueCentre,
            radius = maxOf(size.width, size.height) * 0.58f,
        ),
    )
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color(0x7A2850DC), Color.Transparent),
            center = Offset(size.width * 0.58f, size.height * -0.04f),
            radius = maxOf(size.width, size.height) * 0.30f,
        ),
    )
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color(0x7AFF3246), Color(0x00DC284F)),
            center = redCentre,
            radius = maxOf(size.width, size.height) * 0.48f,
        ),
    )
}
