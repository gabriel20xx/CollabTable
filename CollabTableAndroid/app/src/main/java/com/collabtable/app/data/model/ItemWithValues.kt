package com.collabtable.app.data.model

import androidx.room.Embedded
import androidx.room.Relation

data class ItemWithValues(
    @Embedded val item: Item,
    @Relation(
        parentColumn = "id",
        entityColumn = "itemId"
    )
    val values: List<ItemValue>
)
