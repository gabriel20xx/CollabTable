package com.collabtable.app.data.dao

import androidx.room.*
import com.collabtable.app.data.model.CollabList
import com.collabtable.app.data.model.ListWithFields
import kotlinx.coroutines.flow.Flow

data class ListIdAndOrder(
    val id: String,
    val orderIndex: Long?,
)

@Dao
interface ListDao {
    @Query("SELECT * FROM lists WHERE isDeleted = 0")
    fun getAllLists(): Flow<List<CollabList>>

    @Transaction
    @Query("SELECT * FROM lists WHERE id = :listId AND isDeleted = 0")
    fun getListWithFields(listId: String): Flow<ListWithFields?>

    @Query("SELECT * FROM lists WHERE id = :listId")
    suspend fun getListById(listId: String): CollabList?

    @Query("SELECT id FROM lists")
    suspend fun getAllListIds(): List<String>

    @Query("SELECT id, orderIndex FROM lists")
    suspend fun getListIdsAndOrder(): List<ListIdAndOrder>

    @Upsert
    suspend fun insertList(list: CollabList)

    @Upsert
    suspend fun insertLists(lists: List<CollabList>)

    @Update
    suspend fun updateList(list: CollabList)

    @Query("UPDATE lists SET orderIndex = :orderIndex WHERE id = :listId")
    suspend fun updateListOrderIndex(
        listId: String,
        orderIndex: Long,
    )

    @Transaction
    suspend fun updateOrderIndexes(order: List<Pair<String, Long>>) {
        order.forEach { (id, idx) ->
            updateListOrderIndex(id, idx)
        }
    }

    @Query("UPDATE lists SET isDeleted = 1, updatedAt = :timestamp WHERE id = :listId")
    suspend fun softDeleteList(
        listId: String,
        timestamp: Long,
    )

    @Query("DELETE FROM lists WHERE id = :listId")
    suspend fun deleteList(listId: String)

    // Get all lists updated since timestamp, including deleted ones for sync
    @Query("SELECT * FROM lists WHERE updatedAt >= :since")
    suspend fun getListsUpdatedSince(since: Long): List<CollabList>
}
