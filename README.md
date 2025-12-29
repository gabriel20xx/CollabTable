# CollabTable

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Android](https://img.shields.io/badge/Android-26%2B-green.svg)](https://android.com)
[![Node.js](https://img.shields.io/badge/Node.js-20%2B-brightgreen.svg)](https://nodejs.org)

A collaborative list management system with Android app and server for creating and managing shared lists with custom fields and items.

## Overview

CollabTable allows multiple users to collaboratively create and manage lists with custom fields. Perfect for:
- Shopping lists with price tracking
- Product catalogs
- Task management with custom attributes
- Any structured data collection

## Features

### Android App
- ✨ **Beautiful Material 3 design** with dynamic colors
- 📱 **Native Android app** built with Jetpack Compose
- 💾 **Offline-first** with Room database
- 🔄 **Real-time synchronization** via WebSocket (with HTTP fallback)
- 🎨 **Custom fields** (name, link, price, category, etc.)
- ✏️ **Inline editing** of items with auto-save
- 📤 **Export functionality** (CSV format)
- 🗑️ **Soft delete support** for data recovery
- ⚙️ **Configurable server** URL and authentication
- 📊 **Optimized performance** for large tables with thousands of rows
- 🎯 **Column alignment** options (left, center, right)

### Server
- 🚀 **Express.js REST API** with TypeScript
- 🐳 **Fully containerized** with Docker
- 💾 **Flexible database** support: SQLite or PostgreSQL
- 🔄 **Advanced sync protocol** for conflict resolution
- 🔌 **WebSocket support** for real-time updates
- 🔐 **Optional authentication** with shared password
- 📊 **Complete CRUD operations** for all entities
- 💽 **Persistent storage** with Docker volumes

## Prerequisites

### Server
- **Docker** and **Docker Compose** (recommended)
- Or **Node.js 20+** for local development

### Android App
- **Android Studio** Hedgehog or later
- **Android SDK 26+** (Android 8.0 or higher)
- **JDK 21**
- Android emulator or physical device

## Quick Start

### Server (Docker)

1. **Navigate to server directory:**
   ```bash
   cd CollabTableServer
   ```

2. **Copy environment file (optional):**
   ```bash
   cp .env.example .env
   ```
   
3. **Configure authentication (optional):**
   Edit `.env` and set `SERVER_PASSWORD` for API protection:
   ```env
   SERVER_PASSWORD=your_secure_password
   ```

4. **Start the server:**
   ```bash
   docker-compose up -d
   ```

Server runs on `http://localhost:3000`

**Database Options:**
- SQLite (default): Data persists in Docker volume `sqlite_data`
- PostgreSQL: Configure in `.env` with `DB_CLIENT=postgres`

### Android App

1. **Open project in Android Studio:**
   - Open `CollabTableAndroid` folder in Android Studio
   - Wait for Gradle sync to complete

2. **Run the app:**
   - Run on emulator or physical device

3. **Configure server connection:**
   - Tap the Settings icon (⚙️) in the top bar
   - Enter your server URL:
     - **For emulator:** `http://10.0.2.2:3000/api/`
     - **For physical device:** `http://YOUR_COMPUTER_IP:3000/api/`
   - If server has authentication enabled, enter the password
   - Tap "Save"

**Note:** Make sure to include the trailing `/api/` in the URL.

## Project Structure

```
CollabTable/
├── CollabTableAndroid/          # Android app
│   ├── app/
│   │   ├── src/main/java/com/collabtable/app/
│   │   │   ├── data/            # Database, API, repositories
│   │   │   │   ├── api/         # Retrofit interfaces
│   │   │   │   ├── dao/         # Room DAOs
│   │   │   │   ├── database/    # Room database setup
│   │   │   │   ├── model/       # Data models
│   │   │   │   └── repository/  # Repository pattern
│   │   │   └── ui/              # Compose UI
│   │   │       ├── navigation/  # Navigation setup
│   │   │       ├── screens/     # UI screens & ViewModels
│   │   │       └── theme/       # Material 3 theme
│   │   └── build.gradle
│   └── README.md
│
└── CollabTableServer/           # Node.js server
    ├── src/
    │   ├── models/              # Data models & database logic
    │   ├── routes/              # API endpoints
    │   └── index.ts             # Server entry point
    ├── data/                    # SQLite database storage
    ├── Dockerfile
    ├── docker-compose.yml
    └── README.md
```

## Technology Stack

### Android
- **Language:** Kotlin
- **UI Framework:** Jetpack Compose with Material 3
- **Database:** Room (SQLite)
- **Networking:** Retrofit (HTTP), OkHttp WebSocket
- **Async:** Coroutines & Flow
- **Architecture:** MVVM with Repository pattern
- **Build System:** Gradle with Kotlin DSL

### Server
- **Runtime:** Node.js 20+
- **Framework:** Express.js
- **Language:** TypeScript
- **Database:** SQLite (better-sqlite3) or PostgreSQL (pg)
- **Containerization:** Docker & Docker Compose
- **Real-time:** WebSocket support

## API Endpoints

All endpoints are prefixed with `/api/` and can be protected with optional authentication.

### Authentication
If `SERVER_PASSWORD` is set in the server's `.env` file, include this header in all requests:
```
Authorization: Bearer your_password_here
```

### Lists
- `GET /api/lists` - Get all lists
- `GET /api/lists/:id` - Get a specific list
- `POST /api/lists` - Create a new list
- `PUT /api/lists/:id` - Update a list
- `DELETE /api/lists/:id` - Delete a list (soft delete)

### Fields
- `GET /api/fields/list/:listId` - Get all fields for a list
- `POST /api/fields` - Create a new field
- `PUT /api/fields/:id` - Update a field (including alignment)
- `DELETE /api/fields/:id` - Delete a field (soft delete)

### Items
- `GET /api/items/list/:listId` - Get all items for a list
- `GET /api/items/:itemId/values` - Get all values for an item
- `POST /api/items` - Create a new item
- `POST /api/items/values` - Create/update item values
- `PUT /api/items/:id` - Update an item
- `DELETE /api/items/:id` - Delete an item (soft delete)

### Sync
- `POST /api/sync` - Synchronize data (HTTP fallback)
- `WebSocket /api/ws` - WebSocket endpoint for real-time sync

### Health
- `GET /health` - Server health check

See [Server README](CollabTableServer/README.md) for detailed API documentation.

## Development

### Server Development

**Local development without Docker:**

1. Install dependencies:
   ```bash
   cd CollabTableServer
   npm install
   ```

2. Create `.env` file:
   ```bash
   cp .env.example .env
   ```

3. Start development server with hot reload:
   ```bash
   npm run dev
   ```

4. Build for production:
   ```bash
   npm run build
   npm start
   ```

**Useful commands:**
```bash
npm run lint      # Check code style
npm run format    # Auto-format code
```

### Android Development

1. Open `CollabTableAndroid` in Android Studio

2. Build the project:
   ```bash
   ./gradlew build
   ```

3. Run linters:
   ```bash
   ./gradlew ktlintCheck    # Check Kotlin code style
   ./gradlew detekt         # Run static analysis
   ```

4. Format code:
   ```bash
   ./gradlew ktlintFormat
   ```

## Data Model

### Database Schema

#### List
Represents a table/list container:
```typescript
{
  id: string;           // Unique identifier
  name: string;         // List name
  createdAt: number;    // Creation timestamp
  updatedAt: number;    // Last update timestamp
  isDeleted: boolean;   // Soft delete flag
}
```

#### Field
Defines a column/attribute in a list:
```typescript
{
  id: string;           // Unique identifier
  listId: string;       // Parent list ID
  name: string;         // Field name (e.g., "Price", "Category")
  order: number;        // Display order
  alignment: string;    // Content alignment: "left", "center", "right"
  createdAt: number;    // Creation timestamp
  updatedAt: number;    // Last update timestamp
  isDeleted: boolean;   // Soft delete flag
}
```

#### Item
Represents a row in a list:
```typescript
{
  id: string;           // Unique identifier
  listId: string;       // Parent list ID
  createdAt: number;    // Creation timestamp
  updatedAt: number;    // Last update timestamp
  isDeleted: boolean;   // Soft delete flag
}
```

#### ItemValue
The actual value for a specific field of an item:
```typescript
{
  id: string;           // Unique identifier
  itemId: string;       // Parent item ID
  fieldId: string;      // Associated field ID
  value: string;        // The actual value
  updatedAt: number;    // Last update timestamp
}
```

### Relationships
- One **List** has many **Fields**
- One **List** has many **Items**
- One **Item** has many **ItemValues** (one per Field)

## Troubleshooting

### Common Issues

#### Android App

**Cannot connect to server**
- Verify server is running: visit `http://localhost:3000/health` in browser
- Check server URL in app settings:
  - Emulator: Use `http://10.0.2.2:3000/api/`
  - Physical device: Use your computer's IP address
- Ensure trailing `/api/` is included in URL
- If server has authentication, verify password is correct

**Gradle sync failed**
- Update Android Studio to latest version
- Ensure JDK 21 is installed and selected
- Try: File → Invalidate Caches → Invalidate and Restart

**Build errors with Room/Kotlin**
- Clean and rebuild: Build → Clean Project → Rebuild Project
- Check that you're using compatible versions

**Large tables are slow**
- Performance is optimized for thousands of rows
- For tens of thousands of rows, consider server-side filtering
- Check that debouncing is enabled (default)

#### Server

**Port 3000 already in use**
```bash
# Find and kill process using port 3000
lsof -ti:3000 | xargs kill -9
# Or change port in docker-compose.yml or .env
```

**Docker container won't start**
```bash
# View logs
docker-compose logs -f

# Rebuild container
docker-compose down
docker-compose up -d --build
```

**Database connection error (PostgreSQL)**
- Verify PostgreSQL is running
- Check connection details in `.env`
- Ensure database exists

**Data not persisting after container restart**
- Verify Docker volume exists: `docker volume ls`
- Check volume mount in `docker-compose.yml`
- For SQLite, data is in `sqlite_data` volume

### Getting Help

- Check the [Android README](CollabTableAndroid/README.md) for app-specific details
- Check the [Server README](CollabTableServer/README.md) for server-specific details
- Review Docker logs: `docker-compose logs -f`
- Open an issue on GitHub with detailed error messages

## Synchronization

CollabTable uses a sophisticated sync protocol to keep data consistent across clients:

### Sync Strategy
- **Primary:** WebSocket (`/api/ws`) for real-time, low-latency sync
- **Fallback:** HTTP POST (`/api/sync`) when WebSocket fails
- **Conflict Resolution:** Latest timestamp wins

### How It Works
1. **Client → Server:** Client sends all local changes since last sync timestamp
2. **Server Processing:** Server saves client changes and retrieves server changes
3. **Server → Client:** Server returns changes made since client's last sync
4. **Client Update:** Client applies server updates locally
5. **Timestamp Update:** Client stores new server timestamp for next sync

### Sync Flow
```
Client                                Server
  |                                      |
  |-- Sync Request (local changes) ---->|
  |    (lists, fields, items, values)   |
  |                                      |
  |<-- Sync Response (server changes) --|
  |    (updates + new serverTimestamp)  |
  |                                      |
```

### WebSocket Protocol
Send JSON messages to `/api/ws`:
```json
{
  "type": "sync",
  "id": "unique-request-id",
  "payload": {
    "lastSyncTimestamp": 1234567890,
    "lists": [...],
    "fields": [...],
    "items": [...],
    "itemValues": [...]
  }
}
```

Response:
```json
{
  "type": "syncResponse",
  "id": "unique-request-id",
  "payload": {
    "lists": [...],
    "fields": [...],
    "items": [...],
    "itemValues": [...],
    "serverTimestamp": 1234567890
  }
}
```

### Features
- **Automatic Sync:** Triggered on app start and after local changes
- **Soft Deletes:** Deleted items can be recovered on server
- **Optimistic Updates:** UI updates immediately, syncs in background
- **Performance:** Debounced updates for large table operations

## Docker & Deployment

### Data Persistence

**SQLite (Default)**
- Database stored in Docker volume: `sqlite_data`
- Persists across container restarts
- Backup database:
  ```bash
  # Using container name (set in docker-compose.yml)
  docker cp collabtable-server:/data/collabtable.db ./backup.db
  
  # Or using docker-compose (if in server directory)
  docker-compose exec server cat /data/collabtable.db > ./backup.db
  ```
- Restore database:
  ```bash
  docker cp ./backup.db collabtable-server:/data/collabtable.db
  docker-compose restart
  ```

**PostgreSQL**
- Configure in `.env`: `DB_CLIENT=postgres`
- Use standard PostgreSQL backup tools (`pg_dump`, `pg_restore`)
- Docker volume manages persistence automatically

### Docker Commands

**Start services:**
```bash
docker-compose up -d
```

**Stop services:**
```bash
docker-compose down
```

**View logs:**
```bash
docker-compose logs -f
```

**Rebuild and restart:**
```bash
docker-compose up -d --build
```

**Check running containers:**
```bash
docker ps
```

### Production Deployment

**Environment Variables**
Configure these in `.env` or docker-compose.yml:
```env
PORT=3000                           # Server port
NODE_ENV=production                 # Environment
SERVER_PASSWORD=your_secure_pass    # API authentication (recommended)
DB_CLIENT=sqlite                    # Database: sqlite or postgres
DB_PATH=/data/collabtable.db       # SQLite path (in container)
```

**Security Recommendations**
1. Set `SERVER_PASSWORD` for production
2. Use HTTPS with a reverse proxy (nginx, Caddy)
3. Regular database backups
4. Keep Docker images updated
5. Restrict network access to server ports

**Reverse Proxy Example (nginx)**
```nginx
server {
    listen 80;
    server_name example.com;
    
    location / {
        proxy_pass http://localhost:3000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_cache_bypass $http_upgrade;
    }
}
```

## Contributing

Contributions are welcome! Here's how you can help:

### Getting Started
1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature-name`
3. Make your changes
4. Test your changes thoroughly
5. Commit with clear messages: `git commit -m "feat: add new feature"`
6. Push to your fork: `git push origin feature/your-feature-name`
7. Open a Pull Request

### Development Guidelines

**Code Style**
- **Android:** Follow Kotlin conventions, use ktlint for formatting
- **Server:** Follow TypeScript/JavaScript conventions, use Prettier
- Keep functions small and focused
- Write meaningful variable and function names
- Add comments for complex logic

**Testing**
- Test your changes on both emulator and physical devices (Android)
- Test with both SQLite and PostgreSQL backends (Server)
- Verify sync works correctly with multiple clients
- Test with large datasets to ensure performance

**Commit Messages**
Follow conventional commits:
- `feat:` for new features
- `fix:` for bug fixes
- `docs:` for documentation changes
- `style:` for formatting changes
- `refactor:` for code refactoring
- `test:` for adding tests
- `chore:` for maintenance tasks

### Areas for Contribution

**Features**
- Additional export formats (JSON, Excel)
- Search and filter functionality
- Batch operations
- User authentication and permissions
- Drag-and-drop row reordering
- Rich text field support

**Improvements**
- Performance optimization for very large tables
- Better offline support
- Enhanced error handling
- Accessibility improvements
- Internationalization (i18n)

**Documentation**
- API documentation improvements
- Tutorial videos or blog posts
- Translation to other languages
- More code examples

### Pull Request Guidelines
- Keep PRs focused on a single feature or fix
- Update documentation if needed
- Ensure all tests pass
- Add screenshots for UI changes
- Reference any related issues

## License

MIT
