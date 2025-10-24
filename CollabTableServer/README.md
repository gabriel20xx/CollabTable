# CollabTable Server

A collaborative list management server built with Node.js, Express, TypeScript, and SQLite.

## Features

- RESTful API for managing lists, fields, items, and values
- Real-time synchronization support
- SQLite database with Sequelize ORM
- Dockerized deployment with persistent storage
- TypeScript for type safety

## Prerequisites

- Docker and Docker Compose
- Or Node.js 20+ (for local development)

## Quick Start with Docker

1. Clone the repository
2. Navigate to the server directory:
   ```bash
   cd CollabTableServer
   ```

3. Copy the example environment file:
   ```bash
   cp .env.example .env
   ```

4. Start the services:
   ```bash
   docker-compose up -d
   ```

The server will be available at `http://localhost:3000`

**Important:** The SQLite database is stored in a Docker volume named `sqlite_data`, which persists across container restarts and rebuilds.

## Local Development

1. Install dependencies:
   ```bash
   npm install
   ```

2. Create a `.env` file:
   ```bash
   cp .env.example .env
   ```

3. Update the database path in `.env` if needed:
   ```
   DB_PATH=./data/collabtable.db
   ```

4. Start the development server:
   ```bash
   npm run dev
   ```

## API Endpoints

### Lists
- `GET /api/lists` - Get all lists
- `GET /api/lists/:id` - Get a specific list
- `POST /api/lists` - Create a new list
- `PUT /api/lists/:id` - Update a list
- `DELETE /api/lists/:id` - Delete a list (soft delete)

### Fields
- `GET /api/fields/list/:listId` - Get all fields for a list
- `POST /api/fields` - Create a new field
- `PUT /api/fields/:id` - Update a field
- `DELETE /api/fields/:id` - Delete a field (soft delete)

### Items
- `GET /api/items/list/:listId` - Get all items for a list
- `GET /api/items/:itemId/values` - Get all values for an item
- `POST /api/items` - Create a new item
- `POST /api/items/values` - Create/update an item value
- `PUT /api/items/:id` - Update an item
- `DELETE /api/items/:id` - Delete an item (soft delete)

### Sync
- `POST /api/sync` - Synchronize data between client and server

### Health Check
- `GET /health` - Check server health

## Data Models

### List
```typescript
{
  id: string;
  name: string;
  createdAt: number;
  updatedAt: number;
  isDeleted: boolean;
}
```

### Field
```typescript
{
  id: string;
  listId: string;
  name: string;
  order: number;
  createdAt: number;
  updatedAt: number;
  isDeleted: boolean;
}
```

### Item
```typescript
{
  id: string;
  listId: string;
  createdAt: number;
  updatedAt: number;
  isDeleted: boolean;
}
```

### ItemValue
```typescript
{
  id: string;
  itemId: string;
  fieldId: string;
  value: string;
  updatedAt: number;
}
```

## Sync Protocol

The sync endpoint accepts a POST request with the following structure:

```json
{
  "lastSyncTimestamp": 1234567890,
  "lists": [...],
  "fields": [...],
  "items": [...],
  "itemValues": [...]
}
```

The server responds with:

```json
{
  "lists": [...],
  "fields": [...],
  "items": [...],
  "itemValues": [...],
  "serverTimestamp": 1234567890
}
```

The sync process:
1. Client sends all local changes since the last sync
2. Server saves the client's changes
3. Server returns all changes made on the server since the client's last sync
4. Client applies the server's changes locally

## Building for Production

Build the TypeScript code:
```bash
npm run build
```

Start the production server:
```bash
npm start
```

## Docker Commands

Start services:
```bash
docker-compose up -d
```

Stop services:
```bash
docker-compose down
```

View logs:
```bash
docker-compose logs -f
```

Rebuild and restart:
```bash
docker-compose up -d --build
```

## Environment Variables

- `PORT` - Server port (default: 3000)
- `DB_PATH` - Path to SQLite database file (default: /data/collabtable.db)
- `NODE_ENV` - Environment (development/production)

## Data Persistence

The SQLite database file is stored in a Docker volume, ensuring data persists across:
- Container restarts
- Container recreation
- Docker Compose down/up cycles

To backup your data:
```bash
docker cp collabtable-server:/data/collabtable.db ./backup.db
```

To restore data:
```bash
docker cp ./backup.db collabtable-server:/data/collabtable.db
docker-compose restart
```

## License

MIT
