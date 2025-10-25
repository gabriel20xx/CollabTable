package com.collabtable.app.data.api

import com.collabtable.app.data.model.CollabList
import com.collabtable.app.data.model.Field
import com.collabtable.app.data.model.Item
import com.collabtable.app.data.model.ItemValue
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

data class SyncRequest(
    val lastSyncTimestamp: Long,
    val lists: List<CollabList>,
    val fields: List<Field>,
    val items: List<Item>,
    val itemValues: List<ItemValue>,
)

data class SyncResponse(
    val lists: List<CollabList>,
    val fields: List<Field>,
    val items: List<Item>,
    val itemValues: List<ItemValue>,
    val serverTimestamp: Long,
)

interface CollabTableApi {
    @POST("sync")
    suspend fun sync(
        @Body request: SyncRequest,
    ): Response<SyncResponse>
}
