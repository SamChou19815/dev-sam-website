package com.developersam.rss

import com.google.cloud.datastore.Entity
import com.google.cloud.datastore.Key
import typedstore.TypedEntity
import typedstore.TypedEntityBuilder
import typedstore.TypedEntityCompanion
import typedstore.TypedTable

/**
 * [FeedItem] is a feed item with standard properties [title], [link], and [description].
 * It has a [feedKey] to help identify the feed it belongs to.
 * It is always a child of [Feed].
 */
data class FeedItem(
        private val feedKey: Key? = null,
        private val title: String,
        private val link: String,
        private val description: String
) {

    /**
     * [toUserFeedItem] returns the [UserFeedItem] form of the this item with required additional
     * info [isRead] and [lastUpdatedTime].
     */
    internal fun toUserFeedItem(isRead: Boolean, lastUpdatedTime: Long): UserFeedItem =
            UserFeedItem(
                    feedKey = feedKey, title = title, link = link, description = description,
                    isRead = isRead, lastUpdatedTime = lastUpdatedTime
            )

    /**
     * [Table] is the table definition of [FeedItem].
     */
    private object Table : TypedTable<Table>(tableName = "RssFeedItem") {
        val title = stringProperty(name = "title")
        val link = stringProperty(name = "link")
        val description = longStringProperty(name = "description")
    }

    /**
     * [ItemEntity] is the table definition of [FeedItem].
     */
    private class ItemEntity(entity: Entity) : TypedEntity<Table>(entity = entity) {
        val title: String = Table.title.delegatedValue
        val link: String = Table.link.delegatedValue
        val description: String = Table.description.delegatedValue

        companion object : TypedEntityCompanion<Table, ItemEntity>(table = Table) {
            override fun create(entity: Entity): ItemEntity = ItemEntity(entity = entity)
        }

        /**
         * [asRssFeedItem] returns the entity as the [FeedItem] feed.
         */
        val asRssFeedItem: FeedItem
            get() = FeedItem(
                    feedKey = key.parent ?: error(message = "DB corrupted!"),
                    title = title, link = link, description = description
            )

    }

    internal companion object {

        /**
         * Returns a list of [FeedItem] given their [keys].
         */
        operator fun get(keys: Iterable<Key>): List<FeedItem> =
                ItemEntity[keys].map { it.asRssFeedItem }.toList()

        /**
         * [batchRefresh] will refresh a list of items in batch to automatically reconcile the info
         * recorded in DB.
         */
        fun batchRefresh(feedKey: Key, items: List<FeedItem>) {
            // Step 1: Classify items to new and existing ones
            val newItems = arrayListOf<FeedItem>()
            val existingItems = arrayListOf<FeedItem>()
            val entities = arrayListOf<ItemEntity>()
            for (item in items) {
                val entity = ItemEntity.query { filter { table.link eq item.link } }.firstOrNull()
                if (entity == null) {
                    newItems.add(element = item)
                } else {
                    existingItems.add(element = item)
                    entities.add(element = entity)
                }
            }
            // Step 2: Update them separately
            val builder: TypedEntityBuilder<Table, ItemEntity>.(FeedItem) -> Unit = { item ->
                table.title gets item.title
                table.link gets item.link
                table.description gets item.description
            }
            val itemKeys = ArrayList<Key>(newItems.size + existingItems.size)
            ItemEntity.batchInsert(parent = feedKey, source = newItems, builder = builder)
                    .forEach { itemKeys.add(element = it.key) }
            entities.apply {
                ItemEntity.batchUpdate(entities = this, source = existingItems, builder = builder)
            }.forEach { itemKeys.add(element = it.key) }
            // Step 3: Collect items keys to update user feed.
            UserData.Subscriptions.batchRefresh(feedKey = feedKey, feedItemKeys = itemKeys)
        }

    }

}
