package fr.prisme.player.ui.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import fr.prisme.player.PrismApp
import fr.prisme.player.R
import fr.prisme.player.data.db.ProfileEntity
import fr.prisme.player.ui.theme.PrismPalette

/** Compact profile picker. Profile creation and PIN management remain in Settings for now. */
@Composable
fun MobileProfilesScreen(onBack: () -> Unit, onPicked: () -> Unit) {
    val app = PrismApp.get()
    val profilesFlow = remember { app.database.profileDao().observeProfiles() }
    val profiles by profilesFlow.collectAsState(initial = emptyList())
    val activeId by app.settings.activeProfileId.collectAsState(initial = "default")
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().background(PrismPalette.BackgroundDeep.copy(alpha = 0.78f))) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, stringResource(R.string.detail_back), tint = PrismPalette.TextPrimary)
            }
            Text(
                stringResource(R.string.profiles_title),
                style = MaterialTheme.typography.headlineSmall,
                color = PrismPalette.TextPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            stringResource(R.string.mobile_profiles_hint),
            modifier = Modifier.padding(horizontal = 20.dp),
            color = PrismPalette.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(profiles, key = { it.id }) { profile ->
                MobileProfileCard(profile, profile.id == activeId) {
                    scope.launch {
                        app.settings.setActiveProfile(profile.id)
                        app.settings.setLastProfileSessionAt()
                        onPicked()
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileProfileCard(profile: ProfileEntity, active: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (active) PrismPalette.SurfaceElevated else PrismPalette.SurfaceLift),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier.size(46.dp).clip(CircleShape).background(Color(profile.colorArgb)),
                contentAlignment = Alignment.Center,
            ) {
                Text(profile.avatarEmoji ?: profile.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
            }
            Text(profile.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            if (active) Icon(Icons.Outlined.Check, stringResource(R.string.profiles_active), tint = PrismPalette.Accent)
        }
    }
}
