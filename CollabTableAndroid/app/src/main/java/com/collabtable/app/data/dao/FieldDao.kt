package com.collabtable.app.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.collabtable.app.data.model.Field
import kotlinx.coroutines.flow.Flow

@Dao
interface FieldDao {
    @Query("SELECT * FROM fields WHERE listId = :listId AND isDeleted = 0 ORDER BY `order`")
    fun getFieldsForList(listId: String): Flow<List<Field>>

    @Query("SELECT * FROM fields WHERE id = :fieldId")
    suspend fun getFieldById(fieldId: String): Field?

    @Query("SELECT id FROM fields")
    suspend fun getAllFieldIds(): List<String>

    @Upsert
    suspend fun insertField(field: Field)

    @Upsert
    suspend fun insertFields(fields: List<Field>)

    @Update
    suspend fun updateField(field: Field)

    @Query("UPDATE fields SET isDeleted = 1, updatedAt = :timestamp WHERE id = :fieldId")
    suspend fun softDeleteField(
        fieldId: String,
        timestamp: Long,
    )

    @Query("DELETE FROM fields WHERE id = :fieldId")
    suspend fun deleteField(fieldId: String)

    // Get all fields updated since timestamp, including deleted ones for sync
    @Query("SELECT * FROM fields WHERE updatedAt >= :since")
    suspend fun getFieldsUpdatedSince(since: Long): List<Field>

    // Reorder fields in a single transaction for clarity and consistency
    @Transaction
    suspend fun reorderFieldsInTransaction(
        reorderedFields: List<Field>,
        timestamp: Long,
    ) {
        reorderedFields.forEachIndexed { index, field ->
            updateField(
                field.copy(
                    order = index,
                    updatedAt = timestamp,
                ),
            )
        }
    }
}
