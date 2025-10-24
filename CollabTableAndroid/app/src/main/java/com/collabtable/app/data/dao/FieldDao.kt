package com.collabtable.app.data.dao

import androidx.room.*
import com.collabtable.app.data.model.Field
import kotlinx.coroutines.flow.Flow

@Dao
interface FieldDao {
    @Query("SELECT * FROM fields WHERE listId = :listId AND isDeleted = 0 ORDER BY `order`")
    fun getFieldsForList(listId: String): Flow<List<Field>>

    @Query("SELECT * FROM fields WHERE id = :fieldId")
    suspend fun getFieldById(fieldId: String): Field?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertField(field: Field)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFields(fields: List<Field>)

    @Update
    suspend fun updateField(field: Field)

    @Query("UPDATE fields SET isDeleted = 1, updatedAt = :timestamp WHERE id = :fieldId")
    suspend fun softDeleteField(fieldId: String, timestamp: Long)

    @Query("DELETE FROM fields WHERE id = :fieldId")
    suspend fun deleteField(fieldId: String)

    @Query("SELECT * FROM fields WHERE updatedAt >= :since")
    suspend fun getFieldsUpdatedSince(since: Long): List<Field>
}
