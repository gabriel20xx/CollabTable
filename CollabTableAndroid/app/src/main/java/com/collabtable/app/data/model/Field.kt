package com.collabtable.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class FieldType {
    // Text types
    TEXT,
    MULTILINE_TEXT,

    // Number types
    NUMBER,
    CURRENCY,
    PERCENTAGE,

    // Selection types
    DROPDOWN,
    AUTOCOMPLETE,
    CHECKBOX,
    SWITCH,

    // Link types
    URL,
    EMAIL,
    PHONE,

    // Date/Time types
    DATE,
    TIME,
    DATETIME,
    DURATION,

    // Media types
    IMAGE,
    FILE,
    BARCODE,
    SIGNATURE,

    // Other types
    RATING,
    COLOR,
    LOCATION,
}

@Entity(
    tableName = "fields",
    foreignKeys = [
        ForeignKey(
            entity = CollabList::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("listId")],
)
data class Field(
    @PrimaryKey val id: String,
    val listId: String,
    val name: String,
    val fieldType: String = "TEXT",
    // JSON string for dropdown options, currency symbol, etc.
    val fieldOptions: String = "",
    val order: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
) {
    fun getType(): FieldType {
        val raw = fieldType.ifBlank { "TEXT" }.uppercase()
        return try {
            FieldType.valueOf(raw)
        } catch (e: Exception) {
            // Handle legacy/synonym field types
            when (raw) {
                "STRING" -> FieldType.TEXT
                "PRICE" -> FieldType.CURRENCY
                "AMOUNT" -> FieldType.NUMBER
                "SIZE" -> FieldType.TEXT
                else -> FieldType.TEXT
            }
        }
    }

    fun getDropdownOptions(): List<String> {
        return if (getType() == FieldType.DROPDOWN && fieldOptions.isNotBlank()) {
            fieldOptions.split("|")
        } else {
            emptyList()
        }
    }

    fun getCurrency(): String {
        return if ((getType() == FieldType.CURRENCY) && fieldOptions.isNotBlank()) {
            fieldOptions
        } else {
            "CHF"
        }
    }

    fun getMaxRating(): Int {
        return if (getType() == FieldType.RATING && fieldOptions.isNotBlank()) {
            fieldOptions.toIntOrNull() ?: 5
        } else {
            5
        }
    }

    fun getAutocompleteOptions(): List<String> {
        return if (getType() == FieldType.AUTOCOMPLETE && fieldOptions.isNotBlank()) {
            fieldOptions.split("|")
        } else {
            emptyList()
        }
    }
}
