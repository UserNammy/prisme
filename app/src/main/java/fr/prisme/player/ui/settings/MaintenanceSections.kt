package fr.prisme.player.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import fr.prisme.player.BuildConfig
import fr.prisme.player.PrismApp
import fr.prisme.player.R
import fr.prisme.player.data.settings.PhoneSetupServer
import fr.prisme.player.data.update.UpdateState
import fr.prisme.player.ui.theme.PrismPalette
import kotlinx.coroutines.launch

/** Current version, manual check, and the install button when a newer build exists. */
@Composable
internal fun UpdateSection() {
    val updater = PrismApp.get().updater
    val state by updater.state.collectAsState()
    val scope = rememberCoroutineScope()

    Text(
        stringResource(R.string.update_current_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
        style = MaterialTheme.typography.bodyMedium,
        color = PrismPalette.TextSecondary,
    )
    val status = when (val s = state) {
        UpdateState.Checking -> stringResource(R.string.update_checking)
        UpdateState.UpToDate -> stringResource(R.string.update_up_to_date)
        is UpdateState.Available -> stringResource(R.string.update_available_body, s.update.versionName)
        is UpdateState.Downloading -> stringResource(R.string.update_downloading, (s.progress * 100).toInt())
        UpdateState.Installing -> stringResource(R.string.update_installing)
        UpdateState.NeedsInstallPermission -> stringResource(R.string.update_permission_body)
        is UpdateState.Failed -> s.message
        UpdateState.Idle -> null
    }
    status?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = PrismPalette.AccentSoft)
    }
    val available = (state as? UpdateState.Available)?.update
    if (available != null) {
        Button(onClick = { scope.launch { updater.install(available) } }) {
            Text(
                stringResource(R.string.update_install),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    } else {
        Button(onClick = { scope.launch { updater.check(userInitiated = true) } }) {
            Text(
                stringResource(R.string.update_check_now),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

/** Backup and restore go through the phone page: TV boxes rarely have a file picker. */
@Composable
internal fun BackupSection() {
    val restoredCount by PhoneSetupServer.restored.collectAsState()
    SettingsSection(title = stringResource(R.string.backup_section_title)) {
        Text(
            stringResource(R.string.backup_section_body),
            style = MaterialTheme.typography.bodyMedium,
            color = PrismPalette.TextSecondary,
        )
        if (restoredCount > 0) {
            Text(
                stringResource(R.string.backup_restored),
                style = MaterialTheme.typography.bodySmall,
                color = PrismPalette.AccentSoft,
            )
        }
        PhoneSetupPanel(qrSize = 120.dp)
    }
}
