package com.collabtable.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "item_values",
    foreignKeys = [
        ForeignKey(
            entity = Item::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Field::class,
            parentColumns = ["id"],
            childColumns = ["fieldId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("itemId"), Index("fieldId")],
)
data class ItemValue(
    @PrimaryKey val id: String,
    val itemId: String,
    val fieldId: String,
    val value: String,
    val updatedAt: Long,
)
