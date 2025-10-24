package com.collabtable.app.data.api

import com.collabtable.app.data.model.CollabList
import com.collabtable.app.data.model.Field
import com.collabtable.app.data.model.Item
import com.collabtable.app.data.model.ItemValue
import retrofit2.Response
import retrofit2.http.*

data class SyncRequest(
    val lastSyncTimestamp: Long,
    val lists: List<CollabList>,
    val fields: List<Field>,
    val items: List<Item>,
    val itemValues: List<ItemValue>
)

data class SyncResponse(
    val lists: List<CollabList>,
    val fields: List<Field>,
    val items: List<Item>,
    val itemValues: List<ItemValue>,
    val serverTimestamp: Long
)

interface CollabTableApi {
    @POST("sync")
    suspend fun sync(@Body request: SyncRequest): Response<SyncResponse>

    @GET("lists")
    suspend fun getLists(): Response<List<CollabList>>

    @GET("lists/{listId}")
    suspend fun getList(@Path("listId") listId: String): Response<CollabList>

    @GET("lists/{listId}/fields")
    suspend fun getFields(@Path("listId") listId: String): Response<List<Field>>

    @GET("lists/{listId}/items")
    suspend fun getItems(@Path("listId") listId: String): Response<List<Item>>

    @GET("items/{itemId}/values")
    suspend fun getItemValues(@Path("itemId") itemId: String): Response<List<ItemValue>>
}
