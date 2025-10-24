# Logs Filter Feature

## Overview
Added comprehensive filtering capabilities to the Android app's logs screen, allowing users to filter logs by severity, time range, and tags.

## Features

### 1. **Filter Button**
- Located in the top app bar next to the clear button
- Shows a red badge indicator when filters are active
- Opens a bottom sheet with all filter options

### 2. **Filter Options**

#### **Severity Filtering**
Filter by log level:
- ✅ DEBUG
- ✅ INFO
- ✅ WARN
- ✅ ERROR

Users can select multiple severity levels or deselect to hide certain log types.

#### **Time Range Filtering**
Filter logs by recency:
- Last 10 seconds
- Last 30 seconds
- Last minute
- Last 5 minutes
- Last 15 minutes
- Last hour
- Last 24 hours
- All time (default)

#### **Tag Filtering**
- Dynamically shows all unique tags from current logs
- Multi-select checkboxes for each tag
- "Select All" and "Clear All" buttons for convenience
- Only shows logs from selected tags (empty = show all)

### 3. **Active Filter Chips**
- Horizontal scrollable row below the app bar
- Shows all currently active filters as chips
- Tap any chip to remove that filter
- "Clear All" chip to reset all filters at once
- Only visible when filters are active

### 4. **Log Counter**
- Top bar shows: "Logs (filtered count/total count)"
- Example: "Logs (15/234)" means 15 logs match current filters out of 234 total

### 5. **Smart Empty States**
- "No logs yet" - when no logs exist
- "No logs match filters" - when logs exist but none match filters

## Implementation Details

### New Components

**TimeRange enum**
```kotlin
enum class TimeRange(val label: String, val milliseconds: Long) {
    LAST_10_SECONDS("Last 10 seconds", 10_000),
    LAST_30_SECONDS("Last 30 seconds", 30_000),
    // ... etc
}
```

**LogFilters data class**
```kotlin
data class LogFilters(
    val severities: Set<LogLevel> = LogLevel.values().toSet(),
    val timeRange: TimeRange = TimeRange.ALL_TIME,
    val tags: Set<String> = emptySet(),
    val searchText: String = ""
)
```

**FilterBottomSheet composable**
- Modal bottom sheet with scrollable content
- Checkboxes for severities and tags
- Radio buttons for time range selection
- Reset button to clear all filters

### Filter Logic

Filters are applied in real-time using `remember()` with dependencies:
```kotlin
val filteredLogs = remember(logs, filters) {
    val now = System.currentTimeMillis()
    val cutoffTime = now - filters.timeRange.milliseconds
    
    logs.filter { log ->
        log.level in filters.severities &&
        log.timestamp >= cutoffTime &&
        (filters.tags.isEmpty() || log.tag in filters.tags) &&
        (filters.searchText.isEmpty() || /* search logic */)
    }
}
```

### Auto-scroll Behavior
- Automatically scrolls to the latest log when new logs arrive
- Works with filtered logs, not just all logs

## User Experience

1. **Opening filters**: Tap filter icon in top bar
2. **Applying filters**: Check/uncheck options in bottom sheet
3. **Viewing active filters**: See chips below app bar
4. **Quick removal**: Tap any filter chip to remove it
5. **Reset all**: Tap "Clear All" chip or "Reset All Filters" button
6. **Dismissing sheet**: Tap outside or swipe down

## Benefits

- **Debugging**: Quickly isolate ERROR/WARN logs
- **Performance**: Focus on specific time windows
- **Organization**: Filter by component/tag
- **Visibility**: Clear indication of active filters
- **Efficiency**: Persistent filters across log updates
- **UX**: Smooth animations and Material Design 3

## Technical Notes

- Uses Jetpack Compose with Material3
- State management with `remember` and `mutableStateOf`
- Filter state persists during screen lifetime
- Efficient filtering with Kotlin collections
- Responsive to real-time log updates

## Files Modified

- `LogsScreen.kt`: Complete rewrite with filter UI and logic
- Build verified successfully on both Android and Server
