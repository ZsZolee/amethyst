package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relays.COMMON_FEED_TYPES
import com.vitorpamplona.amethyst.service.relays.EOSETime
import com.vitorpamplona.amethyst.service.relays.JsonFilter
import com.vitorpamplona.amethyst.service.relays.TypedFilter
import com.vitorpamplona.quartz.events.MetadataEvent
import com.vitorpamplona.quartz.events.ReportEvent
import com.vitorpamplona.quartz.events.StatusEvent

object NostrSingleUserDataSource : NostrDataSource("SingleUserFeed") {
    var usersToWatch = setOf<User>()

    fun createUserFilter(): List<TypedFilter>? {
        if (usersToWatch.isEmpty()) return null

        return usersToWatch.filter { it.info?.latestMetadata == null }.map {
            TypedFilter(
                types = COMMON_FEED_TYPES,
                filter = JsonFilter(
                    kinds = listOf(MetadataEvent.kind),
                    authors = listOf(it.pubkeyHex),
                    limit = 1
                )
            )
        }
    }

    fun createUserStatusFilter(): List<TypedFilter>? {
        if (usersToWatch.isEmpty()) return null

        return usersToWatch.map {
            TypedFilter(
                types = COMMON_FEED_TYPES,
                filter = JsonFilter(
                    kinds = listOf(StatusEvent.kind),
                    authors = listOf(it.pubkeyHex),
                    since = it.latestEOSEs
                )
            )
        }
    }

    fun createUserReportFilter(): List<TypedFilter>? {
        if (usersToWatch.isEmpty()) return null

        return usersToWatch.map {
            TypedFilter(
                types = COMMON_FEED_TYPES,
                filter = JsonFilter(
                    kinds = listOf(ReportEvent.kind),
                    tags = mapOf("p" to listOf(it.pubkeyHex)),
                    since = it.latestEOSEs
                )
            )
        }
    }

    val userChannel = requestNewChannel() { time, relayUrl ->
        usersToWatch.forEach {
            val eose = it.latestEOSEs[relayUrl]
            if (eose == null) {
                it.latestEOSEs = it.latestEOSEs + Pair(relayUrl, EOSETime(time))
            } else {
                eose.time = time
            }
        }
    }

    val userChannelOnce = requestNewChannel() { time, relayUrl ->
        // Many relays operate with limits in the amount of filters.
        // As information comes, the filters will be rotated to get more data.
        invalidateFilters()
    }

    override fun updateChannelFilters() {
        userChannel.typedFilters = listOfNotNull(createUserReportFilter(), createUserStatusFilter()).flatten().ifEmpty { null }
        userChannelOnce.typedFilters = listOfNotNull(createUserFilter()).flatten().ifEmpty { null }
    }

    fun add(user: User) {
        if (!usersToWatch.contains(user)) {
            usersToWatch = usersToWatch.plus(user)
            invalidateFilters()
        }
    }

    fun remove(user: User) {
        if (usersToWatch.contains(user)) {
            usersToWatch = usersToWatch.minus(user)
            invalidateFilters()
        }
    }
}
