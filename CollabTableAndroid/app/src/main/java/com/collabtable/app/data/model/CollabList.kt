package com.collabtable.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lists")
data class CollabList(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    // Local-only ordering for manual reordering (not synced)
    val orderIndex: Long? = null,
)
