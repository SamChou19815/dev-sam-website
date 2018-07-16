package com.developersam.rss

import com.google.cloud.datastore.Entity
import com.google.cloud.datastore.Key
import typedstore.TypedEntity
import typedstore.TypedEntityCompanion
import typedstore.TypedTable
import java.util.concurrent.atomic.AtomicReference

/**
 * [Feed] represents the basic information of a feed, which only contains standard RSS 2.0
 * information ([title], [link], [description]) and a RSS link.
 *
 * @property key key of the feed.
 * @property rssUrl the url to the feed XML.
 * @property title title of the feed.
 * @property link link of the feed.
 * @property description description of the feed.
 */
data class Feed(
        private val key: Key? = null,
        private val rssUrl: String,
        private val title: String,
        private val link: String,
        private val description: String
) {

    /**
     * [upsert] upserts the given feed data into the database.
     */
    internal fun upsert(): Key {
        val entity = FeedEntity.query { filter { table.rssUrl eq rssUrl } }.firstOrNull()
        return FeedEntity.upsert(entity = entity) {
            table.rssUrl gets rssUrl
            table.title gets title
            table.link gets link
            table.description gets description
        }.key
    }

    /**
     * [Table] is the table definition for [Feed].
     */
    private object Table : TypedTable<Table>(tableName = "RssFeed") {
        val rssUrl = stringProperty(name = "rss_url")
        val title = stringProperty(name = "title")
        val link = stringProperty(name = "link")
        val description = longStringProperty(name = "description")
    }

    /**
     * [FeedEntity] is the entity definition for [Feed].
     */
    private class FeedEntity(entity: Entity) : TypedEntity<Table>(entity = entity) {
        val rssUrl: String = Table.rssUrl.delegatedValue

        companion object : TypedEntityCompanion<Table, FeedEntity>(table = Table) {
            override fun create(entity: Entity): FeedEntity = FeedEntity(entity = entity)
        }

        /**
         * [asRssFeed] returns the entity as the [Feed] object.
         */
        val asRssFeed: Feed
            get() = Feed(
                    key = key, rssUrl = rssUrl, title = Table.title.delegatedValue,
                    link = Table.link.delegatedValue, description = Table.description.delegatedValue
            )

    }

    companion object {

        /**
         * [lastRefreshedTime] records the last refreshed time for the feed.
         */
        private val lastRefreshedTime: AtomicReference<Long?> = AtomicReference(null)

        /**
         * [MIN_REFRESH_FREQUENCY] is the minimum allowed refresh frequency.
         */
        private const val MIN_REFRESH_FREQUENCY: Long = 1000 * 1200

        /**
         * [get] returns a list of [Feed] given the their [keys].
         */
        internal operator fun get(keys: Iterable<Key>): List<Feed> =
                FeedEntity[keys].map { it.asRssFeed }.toList()

        /**
         * [refreshByUrl] refreshes a given feed by its RSS [url].
         */
        private fun refreshByUrl(url: String) {
            val (feed, items) = FeedParser.parse(url = url) ?: return
            val feedKey = feed.upsert()
            FeedItem.batchRefresh(feedKey = feedKey, items = items)
        }

        /**
         * Refresh all feed in the database.
         */
        fun refresh() {
            val lastRefreshedTime: Long = lastRefreshedTime.get() ?: 0
            if (System.currentTimeMillis() - lastRefreshedTime < MIN_REFRESH_FREQUENCY) {
                return
            }
            FeedEntity.all().map { it.rssUrl }.forEach(action = ::refreshByUrl)
            this.lastRefreshedTime.set(System.currentTimeMillis())
        }

    }

}