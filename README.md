# CollabTable

A collaborative list management system with Android app and server for creating and managing shared lists with custom fields and items.

## Overview

CollabTable allows multiple users to collaboratively create and manage lists with custom fields. Perfect for:
- Shopping lists with price tracking
- Product catalogs
- Task management with custom attributes
- Any structured data collection

## Features

### Android
- ✨ Beautiful Material 3 design with dynamic colors
- 📱 Native Android app with Jetpack Compose
- 💾 Offline-first with Room database
- 🔄 Automatic synchronization with server
- 🎨 Custom fields (name, link, price, category, etc.)
- ✏️ Real-time editing of items
- 🗑️ Soft delete support
- ⚙️ Configurable server URL in settings

### Server
- 🚀 Express.js REST API with TypeScript
- 🐳 Fully containerized with Docker
- 💾 SQLite database with persistent storage
- 🔄 Sync protocol for conflict resolution
- 📊 Complete CRUD operations

## Quick Start

### Server (Docker)

```bash
cd CollabTableServer
docker-compose up -d
```

Server runs on `http://localhost:3000`

### Android App

1. Open `CollabTableAndroid` in Android Studio
2. Wait for Gradle sync
3. Run on emulator or device
4. Go to Settings (gear icon) to configure server URL
   - For emulator: `http://10.0.2.2:3000/api/`
   - For physical device: `http://YOUR_COMPUTER_IP:3000/api/`

## Project Structure

```
CollabTable/
├── CollabTableAndroid/    # Android app
│   ├── app/
│   │   ├── src/main/java/com/collabtable/app/
│   │   │   ├── data/      # Database, API, repositories
│   │   │   └── ui/        # Compose UI, screens, theme
│   │   └── build.gradle
│   └── README.md
│
└── CollabTableServer/     # Node.js server
    ├── src/
    │   ├── models/        # MongoDB models
    │   └── routes/        # API endpoints
    ├── Dockerfile
    ├── docker-compose.yml
    └── README.md
```

## Technology Stack

### Android
- Kotlin
- Jetpack Compose
- Material 3
- Room Database
- Retrofit
- Coroutines & Flow

### Server
- Node.js
- Express
- TypeScript
- SQLite (Sequelize ORM)
- Docker

## API Endpoints

- `GET/POST /api/lists` - Manage lists
- `GET/POST /api/fields` - Manage fields
- `GET/POST /api/items` - Manage items
- `POST /api/sync` - Synchronize data
- `GET /health` - Health check

See [Server README](CollabTableServer/README.md) for detailed API documentation.

## Development

### Android Development
```bash
cd CollabTableAndroid
# Open in Android Studio
```

### Server Development
```bash
cd CollabTableServer
npm install
npm run dev
```

## Data Model

### List
Contains multiple fields and items

### Field
Defines a column/attribute (name, price, category, etc.)

### Item
A row in the list with values for each field

### ItemValue
The actual value for a specific field of an item

## Synchronization

The sync protocol uses timestamps to determine which changes to apply:
1. Client sends all local changes since last sync
2. Server merges changes and returns server-side updates
3. Client applies server updates locally
4. Latest timestamp wins in conflicts

## License

MIT

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.
