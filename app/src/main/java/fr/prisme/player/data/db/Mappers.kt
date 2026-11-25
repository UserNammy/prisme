package fr.prisme.player.data.db

import fr.prisme.player.data.DisplayNames
import fr.prisme.player.data.Channel
import fr.prisme.player.data.ContentType

fun ChannelEntity.toDomain(): Channel = Channel(
    id = id,
    name = DisplayNames.clean(name),
    logoUrl = logoUrl,
    groupTitle = groupTitle,
    streamUrl = streamUrl,
    epgChannelId = epgChannelId,
    type = runCatching { ContentType.valueOf(type) }.getOrDefault(ContentType.TV),
    archiveDays = archiveDays,
)

fun Channel.toEntity(
    sortIndex: Int,
    addedAt: Long = 0L,
): ChannelEntity = ChannelEntity(
    id = id,
    name = name,
    logoUrl = logoUrl,
    groupTitle = groupTitle,
    streamUrl = streamUrl,
    epgChannelId = epgChannelId,
    sortIndex = sortIndex,
    type = type.name,
    addedAt = addedAt,
    archiveDays = archiveDays,
)
