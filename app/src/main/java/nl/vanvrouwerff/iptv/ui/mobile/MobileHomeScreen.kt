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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

/**
 * Deliberately small phone home. It observes the same Room cache as TV, so the catalogue,
 * profiles and favourites never have a second sync path or a second network fetch.
 */
@Composable
fun MobileHomeScreen(
    onOpenSettings: () -> Unit,
    onPlay: (Channel, List<Channel>) -> Unit,
    onOpenDetail: (Channel) -> Unit,
) {
    val app = IptvApp.get()
    val rows by app.database.channelDao().observeChannels().collectAsState(initial = emptyList())
    var selectedType by remember { mutableStateOf(ContentType.TV) }
    var query by remember { mutableStateOf("") }
    val channels = remember(rows, selectedType, query) {
        rows.asSequence()
            .map { it.toDomain() }
            .filter { it.type == selectedType }
            .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
            .take(MOBILE_LIST_LIMIT)
            .toList()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(IptvPalette.BackgroundDeep.copy(alpha = 0.78f)),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "prism",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = IptvPalette.TextPrimary,
                    )
                    Text(
                        text = stringResource(R.string.mobile_library_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = IptvPalette.TextSecondary,
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.mobile_settings),
                        tint = IptvPalette.TextPrimary,
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                label = { Text(stringResource(R.string.mobile_search)) },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MobileTypeChip(ContentType.TV, selectedType, { selectedType = it }, Icons.Outlined.LiveTv)
                MobileTypeChip(ContentType.MOVIE, selectedType, { selectedType = it }, Icons.Outlined.Movie)
                MobileTypeChip(ContentType.SERIES, selectedType, { selectedType = it }, Icons.Outlined.Tv)
            }
        }
        if (channels.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.mobile_empty_library),
                    modifier = Modifier.padding(top = 48.dp),
                    color = IptvPalette.TextSecondary,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            items(channels, key = { it.id }) { channel ->
                MobileChannelCard(
                    channel = channel,
                    onClick = {
                        if (channel.type == ContentType.TV) onPlay(channel, channels) else onOpenDetail(channel)
                    },
                )
            }
        }
    }
}

@Composable
private fun MobileTypeChip(
    type: ContentType,
    selected: ContentType,
    onSelect: (ContentType) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    FilterChip(
        selected = selected == type,
        onClick = { onSelect(type) },
        label = { Text(stringResource(type.mobileLabel)) },
        leadingIcon = { Icon(icon, null, modifier = Modifier.size(18.dp)) },
    )
}

@Composable
private fun MobileChannelCard(channel: Channel, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = IptvPalette.SurfaceLift,
            contentColor = IptvPalette.TextPrimary,
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 82.dp, height = 56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(IptvPalette.SurfaceElevated),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
                if (channel.logoUrl.isNullOrBlank()) {
                    Icon(Icons.Outlined.LiveTv, null, tint = IptvPalette.TextSecondary)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = channel.groupTitle ?: stringResource(R.string.mobile_uncategorized),
                    style = MaterialTheme.typography.bodySmall,
                    color = IptvPalette.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val ContentType.mobileLabel: Int
    get() = when (this) {
        ContentType.TV -> R.string.mobile_live
        ContentType.MOVIE -> R.string.mobile_movies
        ContentType.SERIES -> R.string.mobile_series
    }

private const val MOBILE_LIST_LIMIT = 250
