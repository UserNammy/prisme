package fr.prisme.player.ui.update

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import fr.prisme.player.PrismApp
import fr.prisme.player.R
import fr.prisme.player.data.update.UpdateState
import fr.prisme.player.ui.theme.PrismPalette
import kotlinx.coroutines.launch

/**
 * Startup prompt for a newer published build. Shown over any screen (like reminders) so
 * the update reaches someone who never opens the settings.
 */
@Composable
fun UpdateHost() {
    val updater = PrismApp.get().updater
    val state by updater.state.collectAsState()
    val scope = rememberCoroutineScope()
    when (val s = state) {
        is UpdateState.Available -> {
            if (s.update.versionCode == updater.dismissedVersion) return
            UpdateCard(
                title = stringResource(R.string.update_available_title),
                body = stringResource(R.string.update_available_body, s.update.versionName),
                primary = stringResource(R.string.update_install),
                onPrimary = { scope.launch { updater.install(s.update) } },
                secondary = stringResource(R.string.update_later),
                onSecondary = { updater.dismiss(s.update) },
            )
        }
        is UpdateState.Downloading -> UpdateCard(
            title = stringResource(R.string.update_downloading, (s.progress * 100).toInt()),
            body = stringResource(R.string.update_downloading_body),
        )
        is UpdateState.NeedsInstallPermission -> UpdateCard(
            title = stringResource(R.string.update_permission_title),
            body = stringResource(R.string.update_permission_body),
            primary = stringResource(R.string.update_close),
            onPrimary = updater::clearMessage,
        )
        is UpdateState.Failed -> UpdateCard(
            title = stringResource(R.string.update_failed_title),
            body = s.message,
            primary = stringResource(R.string.update_close),
            onPrimary = updater::clearMessage,
        )
        else -> Unit
    }
}

private const val FOCUS_ATTEMPTS = 10
private const val FOCUS_RETRY_MS = 300L

@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun UpdateCard(
    title: String,
    body: String,
    primary: String? = null,
    onPrimary: () -> Unit = {},
    secondary: String? = null,
    onSecondary: () -> Unit = {},
) {
    val focus = remember { FocusRequester() }
    var cardHasFocus by remember { mutableStateOf(false) }
    if (primary != null) {
        // The screen underneath (home, wizard) grabs focus when it finishes loading, often
        // after this card appeared: take it back until the card holds it, or the remote
        // can never reach its buttons.
        LaunchedEffect(title) {
            repeat(FOCUS_ATTEMPTS) {
                if (!cardHasFocus) runCatching { focus.requestFocus() }
                delay(FOCUS_RETRY_MS)
            }
        }
        BackHandler(onBack = if (secondary != null) onSecondary else onPrimary)
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        Column(
            modifier = Modifier
                .padding(40.dp)
                .width(460.dp)
                // Opaque: a translucent veil let the tiles behind show through the text.
                .background(PrismPalette.SurfaceControl, RoundedCornerShape(18.dp))
                .padding(22.dp)
                .onFocusChanged { cardHasFocus = it.hasFocus }
                // While it is up, the D-pad stays on its buttons instead of the screen behind.
                .focusProperties { exit = { FocusRequester.Cancel } }
                .focusGroup(),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = PrismPalette.TextPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = PrismPalette.TextSecondary,
            )
            if (primary != null) {
                Spacer(Modifier.height(16.dp))
                Row {
                    Button(
                        onClick = onPrimary,
                        modifier = Modifier.focusRequester(focus),
                        colors = ButtonDefaults.colors(
                            containerColor = PrismPalette.Accent,
                            contentColor = Color.White,
                            focusedContainerColor = Color.White,
                            focusedContentColor = PrismPalette.BackgroundDeep,
                        ),
                    ) { Text(primary) }
                    if (secondary != null) {
                        Spacer(Modifier.width(12.dp))
                        Button(
                            onClick = onSecondary,
                            colors = ButtonDefaults.colors(
                                containerColor = PrismPalette.SurfaceLift,
                                contentColor = PrismPalette.TextPrimary,
                                focusedContainerColor = Color.White,
                                focusedContentColor = PrismPalette.BackgroundDeep,
                            ),
                        ) { Text(secondary) }
                    }
                }
            }
        }
    }
}
