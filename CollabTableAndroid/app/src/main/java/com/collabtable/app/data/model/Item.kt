package com.collabtable.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "items",
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
data class Item(
    @PrimaryKey val id: String,
    val listId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
)
