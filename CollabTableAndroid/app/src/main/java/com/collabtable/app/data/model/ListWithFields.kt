package com.collabtable.app.data.model

import androidx.room.Embedded
import androidx.room.Relation

data class ListWithFields(
    @Embedded val list: CollabList,
    @Relation(
        parentColumn = "id",
        entityColumn = "listId",
    )
    val fields: List<Field>,
)
