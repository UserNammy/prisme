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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
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

/**
 * Deliberately small phone home. It observes the same Room cache as TV, so the catalogue,
 * profiles and favourites never have a second sync path or a second network fetch.
 */
@Composable
fun MobileHomeScreen(
    onOpenSettings: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenProfiles: () -> Unit,
    onPlay: (Channel, List<Channel>) -> Unit,
    onOpenDetail: (Channel) -> Unit,
) {
    val app = PrismApp.get()
    var selectedType by remember { mutableStateOf(ContentType.TV) }
    var query by remember { mutableStateOf("") }
    val trimmedQuery = query.trim()
    // Type, search and limit are applied by SQLite (indexed on type) instead of loading the whole
    // catalogue and filtering it on the UI thread at every keystroke.
    val rowsFlow = remember(selectedType, trimmedQuery) {
        val dao = app.database.channelDao()
        if (trimmedQuery.isEmpty()) dao.observeChannelsByType(selectedType.name, MOBILE_LIST_LIMIT)
        else dao.searchChannels(selectedType.name, escapeLike(trimmedQuery), MOBILE_LIST_LIMIT)
    }
    val rows by rowsFlow.collectAsState(initial = emptyList())
    val channels = remember(rows) { rows.map { it.toDomain() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PrismPalette.BackgroundDeep.copy(alpha = 0.78f)),
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_prism_mark),
                    contentDescription = null,
                    modifier = Modifier.size(34.dp),
                    tint = Color.Unspecified,
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = PrismPalette.TextPrimary,
                    )
                    Text(
                        text = stringResource(R.string.mobile_library_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PrismPalette.TextSecondary,
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.mobile_settings),
                        tint = PrismPalette.TextPrimary,
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
                    color = PrismPalette.TextSecondary,
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
        NavigationBar(containerColor = PrismPalette.SurfaceLift) {
            NavigationBarItem(
                selected = true,
                onClick = {},
                icon = { Icon(Icons.Outlined.Home, null) },
                label = { Text(stringResource(R.string.mobile_home)) },
            )
            NavigationBarItem(
                selected = false,
                onClick = onOpenGuide,
                icon = { Icon(Icons.Outlined.LiveTv, null) },
                label = { Text(stringResource(R.string.mobile_guide)) },
            )
            NavigationBarItem(
                selected = false,
                onClick = onOpenProfiles,
                icon = { Icon(Icons.Outlined.Person, null) },
                label = { Text(stringResource(R.string.mobile_profiles)) },
            )
            NavigationBarItem(
                selected = false,
                onClick = onOpenSettings,
                icon = { Icon(Icons.Outlined.Settings, null) },
                label = { Text(stringResource(R.string.mobile_settings)) },
            )
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
            containerColor = PrismPalette.SurfaceLift,
            contentColor = PrismPalette.TextPrimary,
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
                    .background(PrismPalette.SurfaceElevated),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
                if (channel.logoUrl.isNullOrBlank()) {
                    Icon(Icons.Outlined.LiveTv, null, tint = PrismPalette.TextSecondary)
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
                    color = PrismPalette.TextSecondary,
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

/** Escapes LIKE wildcards for the `ESCAPE '\'` clause used by ChannelDao.searchChannels. */
private fun escapeLike(raw: String): String =
    raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
