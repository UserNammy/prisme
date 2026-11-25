package fr.prisme.player.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme as M3MaterialTheme
import androidx.compose.material3.darkColorScheme as m3DarkColorScheme

object PrismPalette {
    /** Prism DA: pure black under the fixed blue/red cloud composition. */
    val BackgroundDeep = Color(0xFF000000)
    val BackgroundLift = Color(0x14000000)
    /** White veil at 8 %, for ordinary tiles and panels. */
    val SurfaceLift = Color(0x14FFFFFF)
    /** White veil at 16 %, for recessed or focused tiles. */
    val SurfaceElevated = Color(0x29FFFFFF)
    /** The single semantic accent: focus and programme progress only. */
    val Accent = Color(0xFF4D86FF)
    val AccentSoft = Color(0xFF9DBBFF)
    val AccentDeep = Color(0xFF254DAD)
    val TextPrimary = Color(0xFFF4F6F7)
    val TextSecondary = Color(0xFFB4BBD0)
    val TextTertiary = Color(0xFFB4BBD0)
}

private val Scheme = darkColorScheme(
    primary = PrismPalette.Accent,
    onPrimary = Color.White,
    primaryContainer = PrismPalette.AccentDeep,
    onPrimaryContainer = PrismPalette.AccentSoft,
    secondary = PrismPalette.AccentSoft,
    onSecondary = Color.White,
    surface = PrismPalette.SurfaceLift,
    onSurface = PrismPalette.TextPrimary,
    surfaceVariant = PrismPalette.SurfaceElevated,
    onSurfaceVariant = PrismPalette.TextSecondary,
    background = PrismPalette.BackgroundDeep,
    onBackground = PrismPalette.TextPrimary,
    border = Color(0x22FFFFFF),
    borderVariant = Color(0x11FFFFFF),
)

// TV-material3 defaults target ~2m viewing; the Formuler sits ~3m from a couch. A 10–15 %
// bump on body / label sizes reads comfortably without overflowing any fixed-size cards.
// Headlines are already large enough — leaving them at defaults avoids hero/rails reflows.
private val TvTypography = Typography(
    bodySmall = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 19.sp, lineHeight = 26.sp),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
)

// OutlinedTextField (and other compose-material3 widgets) read from the compose-material3
// theme — not the tv-material3 theme we use elsewhere. Without this mirror the text field
// falls back to the default light scheme and input text is nearly invisible on our dark
// background. Mirroring onSurface → white keeps every current and future text input
// readable without per-call-site overrides.
private val M3Scheme = m3DarkColorScheme(
    primary = PrismPalette.Accent,
    onPrimary = Color.White,
    surface = PrismPalette.SurfaceLift,
    onSurface = Color.White,
    surfaceVariant = PrismPalette.SurfaceElevated,
    onSurfaceVariant = PrismPalette.TextSecondary,
    background = PrismPalette.BackgroundDeep,
    onBackground = Color.White,
)

@Composable
fun PrismTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = TvTypography) {
        M3MaterialTheme(colorScheme = M3Scheme, content = content)
    }
}
