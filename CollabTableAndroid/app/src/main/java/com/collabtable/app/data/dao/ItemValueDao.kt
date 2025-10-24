package com.collabtable.app.data.dao

import androidx.room.*
import com.collabtable.app.data.model.ItemValue
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemValueDao {
    @Query("SELECT * FROM item_values WHERE itemId = :itemId")
    fun getValuesForItem(itemId: String): Flow<List<ItemValue>>

    @Query("SELECT * FROM item_values WHERE id = :valueId")
    suspend fun getValueById(valueId: String): ItemValue?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertValue(value: ItemValue)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertValues(values: List<ItemValue>)

    @Update
    suspend fun updateValue(value: ItemValue)

    @Query("DELETE FROM item_values WHERE id = :valueId")
    suspend fun deleteValue(valueId: String)

    @Query("DELETE FROM item_values WHERE itemId = :itemId")
    suspend fun deleteValuesForItem(itemId: String)

    @Query("SELECT * FROM item_values WHERE updatedAt >= :since")
    suspend fun getValuesUpdatedSince(since: Long): List<ItemValue>
}
