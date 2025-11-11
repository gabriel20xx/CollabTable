package com.collabtable.app.notifications

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.collabtable.app.R

object NotificationHelper {
    const val CHANNEL_LIST_EVENTS = "list_events"
    private const val GROUP_LIST_EVENTS = "group_list_events"
    private const val SUMMARY_ID = 1000

    // Keep a small rolling buffer of recent event lines for the summary
    private val recentEventLines = ArrayDeque<String>()

    @Volatile private var totalEventCount: Int = 0
    private val postedIds = mutableSetOf<Int>()

    private fun builder(
        context: Context,
        title: String,
        text: String,
    ): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, CHANNEL_LIST_EVENTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
    }

    private fun notify(
        context: Context,
        id: Int,
        builder: NotificationCompat.Builder,
    ) {
        with(NotificationManagerCompat.from(context)) {
            notify(id, builder.build())
        }
    }

    fun showListAdded(
        context: Context,
        listId: String,
        name: String,
    ) = showEvent(context, listId, "add", "List added", "\"$name\" was created")

    fun showListEdited(
        context: Context,
        listId: String,
        name: String,
    ) = showEvent(context, listId, "edit", "List edited", "\"$name\" was renamed/updated")

    fun showListRemoved(
        context: Context,
        listId: String,
        name: String,
    ) = showEvent(context, listId, "remove", "List removed", "\"$name\" was deleted")

    private fun showEvent(
        context: Context,
        listId: String,
        type: String,
        title: String,
        text: String,
    ) {
        val b = builder(context, title, text).setGroup(GROUP_LIST_EVENTS)
        val id = (type + listId).hashCode()
        synchronized(postedIds) { postedIds.add(id) }
        notify(context, id, b)
        recordEventLine(type, text)
        postSummary(context)
    }

    private fun postSummary(context: Context) {
        val inbox = NotificationCompat.InboxStyle()
        synchronized(recentEventLines) {
            recentEventLines.take(5).forEach { line -> inbox.addLine(line) }
            inbox.setSummaryText("$totalEventCount total event" + if (totalEventCount == 1) "" else "s")
        }
        val summary =
            NotificationCompat.Builder(context, CHANNEL_LIST_EVENTS)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("List activity")
                .setContentText("Recent list changes")
                .setStyle(inbox)
                .setGroup(GROUP_LIST_EVENTS)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setNumber(totalEventCount)
        notify(context, SUMMARY_ID, summary)
    }

    private fun recordEventLine(
        type: String,
        text: String,
    ) {
        val line =
            when (type) {
                "add" -> "Added • $text"
                "edit" -> "Edited • $text"
                "remove" -> "Removed • $text"
                else -> text
            }
        synchronized(recentEventLines) {
            recentEventLines.addFirst(line)
            // Keep only the last 8 lines to bound memory and UI clutter
            while (recentEventLines.size > 8) recentEventLines.removeLast()
        }
        totalEventCount += 1
    }

    fun clearListEventNotifications(context: Context) {
        val mgr = NotificationManagerCompat.from(context)
        synchronized(postedIds) {
            postedIds.forEach { id -> mgr.cancel(id) }
            postedIds.clear()
        }
        mgr.cancel(SUMMARY_ID)
        synchronized(recentEventLines) {
            recentEventLines.clear()
            totalEventCount = 0
        }
    }
}
