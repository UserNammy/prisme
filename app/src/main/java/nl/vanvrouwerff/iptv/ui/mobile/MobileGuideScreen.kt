package nl.vanvrouwerff.iptv.ui.mobile

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
import nl.vanvrouwerff.iptv.IptvApp
import nl.vanvrouwerff.iptv.R
import nl.vanvrouwerff.iptv.data.Channel
import nl.vanvrouwerff.iptv.data.ContentType
import nl.vanvrouwerff.iptv.data.db.toDomain
import nl.vanvrouwerff.iptv.ui.theme.IptvPalette

/** A deliberately vertical EPG view: legible on a phone without a TV-style timeline. */
@Composable
fun MobileGuideScreen(
    onBack: () -> Unit,
    onPlay: (Channel, List<Channel>) -> Unit,
) {
    val app = IptvApp.get()
    val rows by app.database.channelDao().observeChannels().collectAsState(initial = emptyList())
    val programmes by app.database.channelDao()
        .observeNowPlaying(System.currentTimeMillis())
        .collectAsState(initial = emptyList())
    val channels = remember(rows) {
        rows.asSequence().map { it.toDomain() }
            .filter { it.type == ContentType.TV && !it.streamUrl.isNullOrBlank() }
            .take(MOBILE_GUIDE_LIMIT)
            .toList()
    }
    val nowByEpgId = remember(programmes) { programmes.associateBy { it.channelKey } }

    Column(
        modifier = Modifier.fillMaxSize().background(IptvPalette.BackgroundDeep.copy(alpha = 0.78f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, stringResource(R.string.detail_back), tint = IptvPalette.TextPrimary)
            }
            Text(
                text = stringResource(R.string.guide_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = IptvPalette.TextPrimary,
            )
        }
        if (channels.isEmpty()) {
            Text(
                text = stringResource(R.string.channels_empty_type_tv),
                modifier = Modifier.padding(24.dp),
                color = IptvPalette.TextSecondary,
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
    programme: nl.vanvrouwerff.iptv.data.db.ProgrammeEntity?,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = IptvPalette.SurfaceLift),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(width = 72.dp, height = 48.dp)
                    .clip(RoundedCornerShape(9.dp)).background(IptvPalette.SurfaceElevated),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
                if (channel.logoUrl.isNullOrBlank()) Icon(Icons.Outlined.LiveTv, null, tint = IptvPalette.TextSecondary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(channel.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = programme?.title ?: stringResource(R.string.guide_no_data),
                    style = MaterialTheme.typography.bodySmall,
                    color = IptvPalette.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private const val MOBILE_GUIDE_LIMIT = 300
