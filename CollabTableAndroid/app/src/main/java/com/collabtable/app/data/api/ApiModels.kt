package com.collabtable.app.data.api

data class NotificationEvent(
    val id: String,
    val deviceIdOrigin: String?,
    val eventType: String,
    val entityType: String,
    val entityId: String?,
    val listId: String?,
    val createdAt: Long,
)

data class PollResponse(
    val notifications: List<NotificationEvent>,
    val serverTimestamp: Long,
)
