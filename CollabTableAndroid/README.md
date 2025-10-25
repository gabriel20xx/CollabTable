# CollabTable Android App

A collaborative table management Android application built with Jetpack Compose and Material 3.

## Features

- Create and manage multiple tables
- Add custom fields to tables (name, link, price, category, etc.)
- Add items with values for each field
- Beautiful Material 3 design with dynamic colors
- Local Room database for offline support
- Automatic synchronization with server (WebSocket-first with HTTP fallback)
- Soft delete support (items can be recovered on server)

## Architecture

- **UI Layer**: Jetpack Compose with Material 3
- **Business Logic**: ViewModels with Kotlin Coroutines
- **Data Layer**: Room database + Retrofit for API calls
- **Navigation**: Jetpack Navigation Compose

## Prerequisites

- Android Studio Hedgehog or later
- Android SDK 26 (Android 8.0) or higher
- JDK 17

## Building the Project

1. Clone the repository
2. Open the `CollabTableAndroid` folder in Android Studio
3. Wait for Gradle sync to complete
4. Run the app on an emulator or physical device

## Server Configuration

The app includes a Settings screen where you can configure the server URL:

1. Tap the Settings icon (⚙️) in the top bar of the Tables screen
2. Enter your server URL
3. Tap "Save"

**Default URLs:**
- Android Emulator: `http://10.0.2.2:3000/api/`
- Physical Device: `http://YOUR_COMPUTER_IP:3000/api/` (replace YOUR_COMPUTER_IP with your actual IP address)

**Note:** Make sure to include `/api/` at the end of the URL.

## Project Structure

```
app/src/main/java/com/collabtable/app/
├── data/
│   ├── api/          # Retrofit API interfaces and client
│   ├── dao/          # Room DAOs
│   ├── database/     # Room database
│   ├── model/        # Data models
│   └── repository/   # Repository pattern for data access
├── ui/
│   ├── navigation/   # Navigation setup
│   ├── screens/      # UI screens and ViewModels
│   └── theme/        # Material 3 theme
├── CollabTableApplication.kt
└── MainActivity.kt
```

## Key Technologies

- **Jetpack Compose**: Modern declarative UI toolkit
- **Material 3**: Latest Material Design guidelines
- **Room**: Local SQLite database
- **Retrofit**: REST API client (fallback)
- **OkHttp WebSocket**: Real-time sync channel
- **Kotlin Coroutines**: Asynchronous programming
- **Flow**: Reactive data streams

## Usage

### Creating a Table
1. Tap the + button on the main screen
2. Enter a table name
3. Tap "Save"

### Adding Fields
1. Open a table
2. Tap the + icon in the top bar
3. Enter a field name (e.g., "Name", "Price", "Category")
4. Tap "Add"

### Adding Items
1. After adding fields, tap the floating + button
2. Fill in values for each field
3. Values are saved automatically as you type

### Deleting
- Tap the delete icon next to any table, field, or item
- Confirm the deletion

## Synchronization

The app uses a WebSocket-first sync strategy with an HTTP fallback:

- WebSocket (`/api/ws`): Sends a `sync` message with local changes and receives deltas plus the new `serverTimestamp`.
- HTTP (`POST /api/sync`): Used automatically as a fallback if the WS exchange fails.

Behavior:
- Sends local changes since the last sync and receives server changes since the last known timestamp.
- Timestamps are used to resolve conflicts (latest wins).
- If the server is protected with `SERVER_PASSWORD`, the app sends `Authorization: Bearer <password>` for both HTTP and WebSocket.

To trigger sync programmatically, call `SyncRepository.performSync()`.

## Building for Release

1. Update the version in `app/build.gradle`
2. Create a keystore for signing
3. Build the release APK:
   ```bash
   ./gradlew assembleRelease
   ```

## License

MIT
