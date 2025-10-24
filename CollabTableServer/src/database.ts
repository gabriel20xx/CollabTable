import path from 'path';
import fs from 'fs';
import BetterSqlite3 from 'better-sqlite3';

const DB_PATH = process.env.DB_PATH || './data/collabtable.db';

// Ensure the data directory exists
const dbDir = path.dirname(DB_PATH);
if (!fs.existsSync(dbDir)) {
  fs.mkdirSync(dbDir, { recursive: true });
  console.log(`Created database directory: ${dbDir}`);
}

console.log(`Using database at: ${DB_PATH}`);

// Create and export the database connection
export const db = new BetterSqlite3(DB_PATH);

// Enable foreign keys
db.pragma('foreign_keys = ON');

// Create tables
export function initializeDatabase() {
  // Create lists table
  db.exec(`
    CREATE TABLE IF NOT EXISTS lists (
      id TEXT PRIMARY KEY,
      name TEXT NOT NULL,
      createdAt INTEGER NOT NULL,
      updatedAt INTEGER NOT NULL,
      isDeleted INTEGER DEFAULT 0
    )
  `);

  // Create fields table
  db.exec(`
    CREATE TABLE IF NOT EXISTS fields (
      id TEXT PRIMARY KEY,
      name TEXT NOT NULL,
      fieldType TEXT NOT NULL,
      fieldOptions TEXT,
      listId TEXT NOT NULL,
      \`order\` INTEGER NOT NULL,
      createdAt INTEGER NOT NULL,
      updatedAt INTEGER NOT NULL,
      isDeleted INTEGER DEFAULT 0,
      FOREIGN KEY (listId) REFERENCES lists(id)
    )
  `);

  // Create items table
  db.exec(`
    CREATE TABLE IF NOT EXISTS items (
      id TEXT PRIMARY KEY,
      listId TEXT NOT NULL,
      createdAt INTEGER NOT NULL,
      updatedAt INTEGER NOT NULL,
      isDeleted INTEGER DEFAULT 0,
      FOREIGN KEY (listId) REFERENCES lists(id)
    )
  `);

  // Create item_values table
  db.exec(`
    CREATE TABLE IF NOT EXISTS item_values (
      id TEXT PRIMARY KEY,
      itemId TEXT NOT NULL,
      fieldId TEXT NOT NULL,
      value TEXT,
      updatedAt INTEGER NOT NULL,
      FOREIGN KEY (itemId) REFERENCES items(id),
      FOREIGN KEY (fieldId) REFERENCES fields(id)
    )
  `);

  // Create indexes for better query performance
  db.exec(`CREATE INDEX IF NOT EXISTS idx_fields_listId ON fields(listId)`);
  db.exec(`CREATE INDEX IF NOT EXISTS idx_items_listId ON items(listId)`);
  db.exec(`CREATE INDEX IF NOT EXISTS idx_item_values_itemId ON item_values(itemId)`);
  db.exec(`CREATE INDEX IF NOT EXISTS idx_item_values_fieldId ON item_values(fieldId)`);

  console.log('Database tables initialized');
}
