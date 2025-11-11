package com.collabtable.app.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.collabtable.app.data.model.Item
import com.collabtable.app.data.model.ItemWithValues
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    // Use stable creation order instead of updatedAt-based ordering to avoid resorting the entire list
    // on every cell edit (which bumps updatedAt). This dramatically reduces scroll jank and recomposition
    // churn for large tables while still presenting rows in a predictable order.
    @Query("SELECT * FROM items WHERE listId = :listId AND isDeleted = 0 ORDER BY createdAt ASC")
    fun getItemsForList(listId: String): Flow<List<Item>>

    @Transaction
    @Query("SELECT * FROM items WHERE listId = :listId AND isDeleted = 0 ORDER BY createdAt ASC")
    fun getItemsWithValuesForList(listId: String): Flow<List<ItemWithValues>>

    @Query("SELECT * FROM items WHERE id = :itemId")
    suspend fun getItemById(itemId: String): Item?

    @Query("SELECT id FROM items")
    suspend fun getAllItemIds(): List<String>

    @Upsert
    suspend fun insertItem(item: Item)

    @Upsert
    suspend fun insertItems(items: List<Item>)

    @Update
    suspend fun updateItem(item: Item)

    @Query("UPDATE items SET isDeleted = 1, updatedAt = :timestamp WHERE id = :itemId")
    suspend fun softDeleteItem(
        itemId: String,
        timestamp: Long,
    )

    @Query("DELETE FROM items WHERE id = :itemId")
    suspend fun deleteItem(itemId: String)

    // Get all items updated since timestamp, including deleted ones for sync
    @Query("SELECT * FROM items WHERE updatedAt >= :since")
    suspend fun getItemsUpdatedSince(since: Long): List<Item>

    @Query("UPDATE items SET isDeleted = 1, updatedAt = :timestamp WHERE listId = :listId")
    suspend fun softDeleteItemsByList(
        listId: String,
        timestamp: Long,
    )
}
