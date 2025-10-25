package com.collabtable.app.data.dao

import androidx.room.*
import com.collabtable.app.data.model.CollabList
import com.collabtable.app.data.model.ListWithFields
import kotlinx.coroutines.flow.Flow

@Dao
interface ListDao {
    @Query("SELECT * FROM lists WHERE isDeleted = 0 ORDER BY updatedAt DESC")
    fun getAllLists(): Flow<List<CollabList>>

    @Transaction
    @Query("SELECT * FROM lists WHERE id = :listId AND isDeleted = 0")
    fun getListWithFields(listId: String): Flow<ListWithFields?>

    @Query("SELECT * FROM lists WHERE id = :listId")
    suspend fun getListById(listId: String): CollabList?

    @Query("SELECT id FROM lists")
    suspend fun getAllListIds(): List<String>

    @Upsert
    suspend fun insertList(list: CollabList)

    @Upsert
    suspend fun insertLists(lists: List<CollabList>)

    @Update
    suspend fun updateList(list: CollabList)

    @Query("UPDATE lists SET isDeleted = 1, updatedAt = :timestamp WHERE id = :listId")
    suspend fun softDeleteList(listId: String, timestamp: Long)

    @Query("DELETE FROM lists WHERE id = :listId")
    suspend fun deleteList(listId: String)

    // Get all lists updated since timestamp, including deleted ones for sync
    @Query("SELECT * FROM lists WHERE updatedAt >= :since")
    suspend fun getListsUpdatedSince(since: Long): List<CollabList>
}
