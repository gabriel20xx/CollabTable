package com.collabtable.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.collabtable.app.utils.LogEntry
import com.collabtable.app.utils.LogLevel
import com.collabtable.app.utils.Logger

enum class TimeRange(
    val label: String,
    val milliseconds: Long,
) {
    LAST_10_SECONDS("Last 10 seconds", 10_000),
    LAST_30_SECONDS("Last 30 seconds", 30_000),
    LAST_MINUTE("Last minute", 60_000),
    LAST_5_MINUTES("Last 5 minutes", 300_000),
    LAST_15_MINUTES("Last 15 minutes", 900_000),
    LAST_HOUR("Last hour", 3_600_000),
    LAST_24_HOURS("Last 24 hours", 86_400_000),
    ALL_TIME("All time", Long.MAX_VALUE),
}

data class LogFilters(
    val severities: Set<LogLevel> = LogLevel.values().toSet(),
    val timeRange: TimeRange = TimeRange.ALL_TIME,
    val tags: Set<String> = emptySet(),
    val searchText: String = "",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(onNavigateBack: () -> Unit) {
    val logs by Logger.logs.collectAsState()
    val listState = rememberLazyListState()
    var showFilterSheet by remember { mutableStateOf(false) }
    var filters by remember { mutableStateOf(LogFilters()) }

    // Extract all unique tags from logs
    val allTags =
        remember(logs) {
            logs.map { it.tag }.distinct().sorted()
        }

    // Apply filters
    val filteredLogs =
        remember(logs, filters) {
            val now = System.currentTimeMillis()
            val cutoffTime = now - filters.timeRange.milliseconds

            logs.filter { log ->
                // Severity filter
                log.level in filters.severities &&
                    // Time range filter
                    log.timestamp >= cutoffTime &&
                    // Tag filter (empty means show all)
                    (filters.tags.isEmpty() || log.tag in filters.tags) &&
                    // Search text filter
                    (
                        filters.searchText.isEmpty() ||
                            log.message.contains(filters.searchText, ignoreCase = true) ||
                            log.tag.contains(filters.searchText, ignoreCase = true)
                    )
            }
        }

    // Auto-scroll to bottom when new logs arrive
    LaunchedEffect(filteredLogs.size) {
        if (filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.size - 1)
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Logs (${filteredLogs.size}/${logs.size})") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showFilterSheet = true }) {
                        Badge(
                            containerColor =
                                if (hasActiveFilters(filters)) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    Color.Transparent
                                },
                        ) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filter logs")
                        }
                    }
                    IconButton(onClick = { Logger.clear() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear logs")
                    }
                    // Export / Share current (filtered) logs
                    IconButton(onClick = {
                        if (filteredLogs.isEmpty()) return@IconButton
                        val exportText =
                            buildString {
                                append("CollabTable Logs Export\n")
                                append("Total: ").append(filteredLogs.size).append('\n')
                                append("Generated: ")
                                append(
                                    java.text
                                        .SimpleDateFormat(
                                            "yyyy-MM-dd HH:mm:ss",
                                            java.util.Locale.getDefault(),
                                        ).format(java.util.Date()),
                                )
                                append("\n\n")
                                filteredLogs.forEach { log ->
                                    append(log.toFormattedString()).append('\n')
                                }
                            }
                        val intent =
                            android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, exportText)
                                putExtra(android.content.Intent.EXTRA_TITLE, "CollabTable Logs")
                            }
                        val chooser = android.content.Intent.createChooser(intent, "Share Logs")
                        try {
                            context.startActivity(chooser)
                        } catch (_: Exception) {
                            // Silently ignore if no activity can handle
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Export / Share logs")
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            // Active filters chips
            if (hasActiveFilters(filters)) {
                LazyRow(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Severity filters
                    if (filters.severities.size < LogLevel.values().size) {
                        items(filters.severities.toList()) { level ->
                            FilterChip(
                                selected = true,
                                onClick = {
                                    filters =
                                        filters.copy(
                                            severities = filters.severities - level,
                                        )
                                },
                                label = { Text(level.name, fontSize = 12.sp) },
                            )
                        }
                    }

                    // Time range filter
                    if (filters.timeRange != TimeRange.ALL_TIME) {
                        item {
                            FilterChip(
                                selected = true,
                                onClick = {
                                    filters = filters.copy(timeRange = TimeRange.ALL_TIME)
                                },
                                label = { Text(filters.timeRange.label, fontSize = 12.sp) },
                            )
                        }
                    }

                    // Tag filters
                    items(filters.tags.toList()) { tag ->
                        FilterChip(
                            selected = true,
                            onClick = {
                                filters = filters.copy(tags = filters.tags - tag)
                            },
                            label = { Text(tag, fontSize = 12.sp) },
                        )
                    }

                    // Clear all button
                    item {
                        FilterChip(
                            selected = false,
                            onClick = {
                                filters = LogFilters()
                            },
                            label = { Text("Clear All", fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    }
                }
            }

            // Logs list
            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (logs.isEmpty()) "No logs yet" else "No logs match filters",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                    contentPadding = PaddingValues(8.dp),
                ) {
                    items(filteredLogs) { log ->
                        LogItem(log)
                    }
                }
            }
        }
    }

    // Filter bottom sheet
    if (showFilterSheet) {
        FilterBottomSheet(
            filters = filters,
            allTags = allTags,
            onFiltersChanged = { filters = it },
            onDismiss = { showFilterSheet = false },
        )
    }
}

private fun hasActiveFilters(filters: LogFilters): Boolean =
    filters.severities.size < LogLevel.values().size ||
        filters.timeRange != TimeRange.ALL_TIME ||
        filters.tags.isNotEmpty() ||
        filters.searchText.isNotEmpty()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    filters: LogFilters,
    allTags: List<String>,
    onFiltersChanged: (LogFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp),
        ) {
            Text(
                text = "Filter Logs",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            // Severity filters
            Text(
                text = "Severity",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            LogLevel.values().forEach { level ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = level in filters.severities,
                        onCheckedChange = { checked ->
                            onFiltersChanged(
                                filters.copy(
                                    severities =
                                        if (checked) {
                                            filters.severities + level
                                        } else {
                                            filters.severities - level
                                        },
                                ),
                            )
                        },
                    )
                    Text(
                        text = level.name,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // Time range filter
            Text(
                text = "Time Range",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            TimeRange.values().forEach { range ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = filters.timeRange == range,
                        onClick = {
                            onFiltersChanged(filters.copy(timeRange = range))
                        },
                    )
                    Text(
                        text = range.label,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // Tag filters
            Text(
                text = "Tags (${filters.tags.size} selected)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            if (allTags.isEmpty()) {
                Text(
                    text = "No tags available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = {
                            onFiltersChanged(filters.copy(tags = allTags.toSet()))
                        },
                    ) {
                        Text("Select All")
                    }
                    TextButton(
                        onClick = {
                            onFiltersChanged(filters.copy(tags = emptySet()))
                        },
                    ) {
                        Text("Clear All")
                    }
                }

                allTags.forEach { tag ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = tag in filters.tags,
                            onCheckedChange = { checked ->
                                onFiltersChanged(
                                    filters.copy(
                                        tags =
                                            if (checked) {
                                                filters.tags + tag
                                            } else {
                                                filters.tags - tag
                                            },
                                    ),
                                )
                            },
                        )
                        Text(
                            text = tag,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // Reset button
            Button(
                onClick = {
                    onFiltersChanged(LogFilters())
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Reset All Filters")
            }
        }
    }
}

@Composable
fun LogItem(log: LogEntry) {
    val color =
        when (log.level) {
            LogLevel.DEBUG -> Color(0xFF808080)
            LogLevel.INFO -> Color(0xFF4FC3F7)
            LogLevel.WARN -> Color(0xFFFFA726)
            LogLevel.ERROR -> Color(0xFFEF5350)
        }

    Text(
        text = log.toFormattedString(),
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
    )
}
