package fr.prisme.player.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.prisme.player.R
import fr.prisme.player.data.DisplayNames
import fr.prisme.player.ui.channels.ChannelsUiState

/**
 * Rail and category titles double as stable internal keys (focus memory, "see all"
 * routing), inherited from the upstream app as Dutch literals. They must never reach the
 * screen as-is: built-in keys map to string resources, provider categories are cleaned.
 */
@Composable
fun displayRailTitle(raw: String): String = when (raw) {
    ChannelsUiState.CONTINUE_WATCHING -> stringResource(R.string.rail_continue_watching)
    ChannelsUiState.POPULAR_NOW -> stringResource(R.string.rail_popular_now)
    ChannelsUiState.MY_LIST -> stringResource(R.string.rail_my_list)
    ChannelsUiState.FAVORITES -> stringResource(R.string.rail_favorites)
    ChannelsUiState.UNCATEGORIZED -> stringResource(R.string.mobile_uncategorized)
    else -> DisplayNames.clean(raw)
}
