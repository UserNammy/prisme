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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.LiveTv
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import fr.prisme.player.PrismApp
import fr.prisme.player.R
import fr.prisme.player.data.Channel
import fr.prisme.player.data.ContentType
import fr.prisme.player.data.db.toDomain
import fr.prisme.player.ui.theme.PrismPalette

/** A deliberately vertical EPG view: legible on a phone without a TV-style timeline. */
@Composable
fun MobileGuideScreen(
    onBack: () -> Unit,
    onPlay: (Channel, List<Channel>) -> Unit,
) {
    val app = PrismApp.get()
    val dao = app.database.channelDao()
    // Flows are remembered: building them inline restarted both Room queries on every recomposition.
    val rowsFlow = remember { dao.observeChannelsByType(ContentType.TV.name, MOBILE_GUIDE_LIMIT * 2) }
    val programmesFlow = remember { dao.observeNowPlaying(System.currentTimeMillis()) }
    val rows by rowsFlow.collectAsState(initial = emptyList())
    val programmes by programmesFlow.collectAsState(initial = emptyList())
    val channels = remember(rows) {
        rows.asSequence().map { it.toDomain() }
            .filter { !it.streamUrl.isNullOrBlank() }
            .take(MOBILE_GUIDE_LIMIT)
            .toList()
    }
    val nowByEpgId = remember(programmes) { programmes.associateBy { it.channelKey } }

    Column(
        modifier = Modifier.fillMaxSize().background(PrismPalette.BackgroundDeep.copy(alpha = 0.78f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, stringResource(R.string.detail_back), tint = PrismPalette.TextPrimary)
            }
            Text(
                text = stringResource(R.string.guide_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = PrismPalette.TextPrimary,
            )
        }
        if (channels.isEmpty()) {
            Text(
                text = stringResource(R.string.channels_empty_type_tv),
                modifier = Modifier.padding(24.dp),
                color = PrismPalette.TextSecondary,
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(channels, key = { it.id }) { channel ->
                    MobileGuideRow(channel, nowByEpgId[channel.epgChannelId], onClick = { onPlay(channel, channels) })
                }
            }
        }
    }
}

@Composable
private fun MobileGuideRow(
    channel: Channel,
    programme: fr.prisme.player.data.db.ProgrammeEntity?,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PrismPalette.SurfaceLift),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(width = 72.dp, height = 48.dp)
                    .clip(RoundedCornerShape(9.dp)).background(PrismPalette.SurfaceElevated),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
                if (channel.logoUrl.isNullOrBlank()) Icon(Icons.Outlined.LiveTv, null, tint = PrismPalette.TextSecondary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(channel.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = programme?.title ?: stringResource(R.string.guide_no_data),
                    style = MaterialTheme.typography.bodySmall,
                    color = PrismPalette.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private const val MOBILE_GUIDE_LIMIT = 300
