package com.collabtable.app.data.dao

import androidx.room.*
import com.collabtable.app.data.model.Item
import com.collabtable.app.data.model.ItemWithValues
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items WHERE listId = :listId AND isDeleted = 0 ORDER BY updatedAt DESC")
    fun getItemsForList(listId: String): Flow<List<Item>>

    @Transaction
    @Query("SELECT * FROM items WHERE listId = :listId AND isDeleted = 0 ORDER BY updatedAt DESC")
    fun getItemsWithValuesForList(listId: String): Flow<List<ItemWithValues>>

    @Query("SELECT * FROM items WHERE id = :itemId")
    suspend fun getItemById(itemId: String): Item?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: Item)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<Item>)

    @Update
    suspend fun updateItem(item: Item)

    @Query("UPDATE items SET isDeleted = 1, updatedAt = :timestamp WHERE id = :itemId")
    suspend fun softDeleteItem(itemId: String, timestamp: Long)

    @Query("DELETE FROM items WHERE id = :itemId")
    suspend fun deleteItem(itemId: String)

    @Query("SELECT * FROM items WHERE updatedAt >= :since")
    suspend fun getItemsUpdatedSince(since: Long): List<Item>
}
