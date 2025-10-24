# CollabTable - Updates Summary

## Latest Changes (October 2025)

### Field Editing Feature
**Added ability to edit field types and options after creation**

#### Android Changes:
- **New `EditFieldDialog` composable** in `ListDetailScreen.kt`
  - Allows changing field type (STRING, PRICE, DROPDOWN, URL, DATE, TIME, DATETIME)
  - Modify dropdown options or currency symbols
  - Similar UI to AddFieldDialog for consistency
  
- **FieldHeader enhancements**
  - Added Edit icon button next to field name
  - Click to open EditFieldDialog
  - Long-press still deletes field
  
- **ViewModel update**
  - New `updateField()` method in `ListDetailViewModel`
  - Updates field type and options in database
  - Properly maintains field state

**Use case:** Change a STRING field to a DROPDOWN, or update dropdown options without recreating the field.

---

### Password Authentication Feature
**Server-side password protection with Android client support**

#### Server Changes:

1. **Environment Configuration** (`.env.example`)
   - Added `SERVER_PASSWORD` environment variable
   - Set your secure password in `.env` file

2. **Authentication Middleware** (`src/middleware/auth.ts`)
   - Validates password on all `/api/*` routes
   - Uses Bearer token format: `Authorization: Bearer <password>`
   - `/health` endpoint remains unauthenticated
   - Returns 401 for missing or invalid passwords
   
3. **Server Integration** (`src/index.ts`)
   - Auth middleware applied before route handlers
   - All API calls now require valid password
   - Health check accessible without auth

#### Android Changes:

1. **PreferencesManager updates**
   - `getServerPassword()` and `setServerPassword()` methods
   - Secure storage in SharedPreferences
   - Password persists across app sessions

2. **ServerSetupScreen enhancements**
   - New password input field with show/hide toggle
   - Password required for setup completion
   - Validates password during initial setup
   - Tests authentication before saving

3. **ServerSetupViewModel**
   - Updated `validateAndSaveServerUrl()` to accept password
   - Two-step validation: health check + authenticated request
   - Stores password only after successful authentication
   - Clear error messages for auth failures

4. **ApiClient authentication**
   - New auth interceptor adds `Authorization` header
   - Automatically includes password in all API requests
   - Retrieves password from PreferencesManager
   - No code changes needed in individual API calls

#### Setup Instructions:

**Server:**
```bash
# Set password in .env file
echo "SERVER_PASSWORD=your_secure_password" >> .env

# Restart server
docker-compose restart
```

**Android:**
1. Open app (first time or after reset)
2. Enter server URL
3. Enter server password
4. Tap "Validate and Continue"
5. Password is validated and stored

**Security Notes:**
- Password stored in Android SharedPreferences (encrypted on modern devices)
- Password sent via HTTPS in production (use SSL certificates)
- Consider using more robust auth (OAuth, JWT) for production use
- Current implementation is simple password-based authentication

---

## Previous Changes

### Android App Enhancements

#### 1. Settings Screen Added
- **New Files:**
  - `PreferencesManager.kt` - Manages persistent storage of settings
  - `SettingsScreen.kt` - UI for configuring server URL
  - `SettingsViewModel.kt` - Business logic for settings

- **Features:**
  - Settings icon in the main Lists screen toolbar
  - Configurable server URL with validation
  - Helpful tips for emulator vs physical device URLs
  - Persistent storage across app restarts
  - Success notification when URL is updated

#### 2. API Client Updates
- `ApiClient.kt` now initializes with saved preferences
- Server URL can be changed at runtime
- Automatically loads saved URL on app start

#### 3. Navigation Updates
- Added settings route to navigation graph
- Settings accessible from Lists screen

### Server Migration to SQLite

#### 1. Database Change
- **Replaced:** MongoDB → SQLite with Sequelize ORM
- **Reason:** Simpler deployment, built-in persistence, no separate database container needed

#### 2. Updated Dependencies
- **Removed:** `mongoose`
- **Added:** `sequelize`, `sqlite3`

#### 3. New Database Configuration
- `database.ts` - Sequelize connection setup
- Database path configurable via `DB_PATH` environment variable
- Default location: `/data/collabtable.db`

#### 4. Model Updates
All models converted from Mongoose schemas to Sequelize models:
- `List.ts`
- `Field.ts`
- `Item.ts`
- `ItemValue.ts`

#### 5. Routes Updates
All route handlers updated to use Sequelize methods:
- `findAll()` instead of `find()`
- `upsert()` instead of `findOneAndUpdate()`
- Sequelize operators (`Op.gt`) instead of MongoDB operators (`$gt`)

### Docker Configuration

#### 1. Simplified Setup
- **Removed:** MongoDB container
- **Changed:** Single server container with SQLite
- **Result:** Faster startup, simpler architecture

#### 2. Persistent Storage
- Docker volume: `sqlite_data`
- Mounted at: `/data` in container
- **Persists across:**
  - Container restarts
  - Container recreation
  - `docker-compose down/up` cycles

#### 3. Environment Variables
- `PORT` - Server port (default: 3000)
- `DB_PATH` - SQLite database path (default: /data/collabtable.db)
- `NODE_ENV` - Environment mode

## How to Use

### Start the Server
```bash
cd CollabTableServer
docker-compose up -d
```

### Configure Android App
1. Open app in Android Studio
2. Build and run on device/emulator
3. Tap Settings icon (⚙️) in top bar
4. Enter server URL:
   - **Emulator:** `http://10.0.2.2:3000/api/`
   - **Physical Device:** `http://YOUR_IP:3000/api/`
5. Tap "Save"

### Backup Database
```bash
# Backup
docker cp collabtable-server:/data/collabtable.db ./backup.db

# Restore
docker cp ./backup.db collabtable-server:/data/collabtable.db
docker-compose restart
```

## Benefits

### Android App
✅ No hardcoded server URLs
✅ Easy to switch between environments
✅ User-friendly configuration
✅ Clear instructions for different device types

### Server
✅ Simpler deployment (one container vs two)
✅ No database configuration needed
✅ Built-in data persistence
✅ Easy backups (single file)
✅ Lower resource usage
✅ Faster startup time

## Testing

### Test Server Persistence
```bash
# Start server
docker-compose up -d

# Use the app to create data
# ...

# Restart server
docker-compose restart

# Data should still be there!

# Even after complete teardown
docker-compose down
docker-compose up -d

# Data persists because of the volume!
```

### Test Settings
1. Open app
2. Go to Settings
3. Change server URL
4. Close app
5. Reopen app
6. Go to Settings - URL should be saved!

## Architecture Diagram

```
┌─────────────────────┐
│   Android App       │
│  (Material 3 UI)    │
│                     │
│  ┌───────────────┐  │
│  │  Settings     │  │
│  │  Screen       │  │
│  └───────────────┘  │
│         ↓           │
│  ┌───────────────┐  │
│  │ Preferences   │  │
│  │ Manager       │  │
│  └───────────────┘  │
│         ↓           │
│  ┌───────────────┐  │
│  │  API Client   │  │
│  └───────────────┘  │
└──────────┬──────────┘
           │ HTTP/REST
           ↓
┌─────────────────────┐
│  Docker Container   │
│                     │
│  ┌───────────────┐  │
│  │  Express.js   │  │
│  │   Server      │  │
│  └───────┬───────┘  │
│          ↓          │
│  ┌───────────────┐  │
│  │  Sequelize    │  │
│  │     ORM       │  │
│  └───────┬───────┘  │
│          ↓          │
│  ┌───────────────┐  │
│  │    SQLite     │  │
│  │   Database    │  │
│  └───────────────┘  │
│          ↓          │
│     /data/collabtable.db
│          ↓          │
└──────────┬──────────┘
           │
    Docker Volume
   (sqlite_data)
```

## Notes

- TypeScript compilation errors shown are expected until dependencies are installed
- Run `npm install` in the server directory before local development
- The Android app works offline-first and syncs when connected
- Server validates and sanitizes all incoming data
- Timestamps are used for conflict resolution (latest wins)
