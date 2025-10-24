package com.collabtable.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class FieldType {
    STRING,
    PRICE,
    DROPDOWN,
    URL,
    DATE,
    TIME,
    DATETIME
}

@Entity(
    tableName = "fields",
    foreignKeys = [
        ForeignKey(
            entity = CollabList::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("listId")]
)
data class Field(
    @PrimaryKey val id: String,
    val listId: String,
    val name: String,
    val fieldType: String = "STRING", // STRING, PRICE, DROPDOWN
    val fieldOptions: String = "", // JSON string for dropdown options or currency for price
    val order: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false
) {
    fun getType(): FieldType {
        return try {
            FieldType.valueOf(fieldType)
        } catch (e: Exception) {
            FieldType.STRING
        }
    }
    
    fun getDropdownOptions(): List<String> {
        return if (fieldType == "DROPDOWN" && fieldOptions.isNotBlank()) {
            fieldOptions.split("|")
        } else {
            emptyList()
        }
    }
    
    fun getCurrency(): String {
        return if (fieldType == "PRICE" && fieldOptions.isNotBlank()) {
            fieldOptions
        } else {
            "$"
        }
    }
}
