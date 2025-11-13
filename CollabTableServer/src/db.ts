import dotenv from 'dotenv';
import BetterSqlite3, { Database as SqliteDB } from 'better-sqlite3';
import { Pool, PoolClient, QueryResult, types as pgTypes } from 'pg';
import path from 'path';
import fs from 'fs';

dotenv.config();

type Param = any;

type ExecResult = { changes: number };


interface DBAdapter {
  queryAll(sql: string, params?: Param[]): Promise<any[]>;
  queryOne(sql: string, params?: Param[]): Promise<any | undefined>;
  execute(sql: string, params?: Param[]): Promise<ExecResult>;
  transaction<T>(fn: (tx: DBAdapter) => Promise<T>): Promise<T>;
  initialize(): Promise<void>;
}

function ensureDataDir(dbPath: string) {
  const dir = path.dirname(dbPath);
  if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
}

function replaceQuotedIdentifiers(sql: string) {
  // Convert backticks to double quotes for portability
  return sql.replace(/`([^`]+)`/g, '"$1"');
}

function convertQMarksToPg(sql: string) {
  // Replace each ? with $1, $2, ...
  let idx = 0;
  return sql.replace(/\?/g, () => `$${++idx}`);
}

// ---------- SQLite Adapter (wrapped async) ----------
class SqliteAdapter implements DBAdapter {
  private db: SqliteDB;

  constructor() {
    const DB_PATH = process.env.DB_PATH || './data/collabtable.db';
    ensureDataDir(DB_PATH);
    this.db = new BetterSqlite3(DB_PATH);
    this.db.pragma('foreign_keys = ON');
  }

  async initialize(): Promise<void> {
    // Create tables and indexes
    this.db.exec(`
      CREATE TABLE IF NOT EXISTS lists (
        id TEXT PRIMARY KEY,
        name TEXT NOT NULL,
        createdAt INTEGER NOT NULL,
        updatedAt INTEGER NOT NULL,
        isDeleted INTEGER DEFAULT 0
      );

      CREATE TABLE IF NOT EXISTS fields (
        id TEXT PRIMARY KEY,
        name TEXT NOT NULL,
        fieldType TEXT NOT NULL,
        fieldOptions TEXT,
        alignment TEXT NOT NULL DEFAULT 'start',
        listId TEXT NOT NULL,
        "order" INTEGER NOT NULL,
        createdAt INTEGER NOT NULL,
        updatedAt INTEGER NOT NULL,
        isDeleted INTEGER DEFAULT 0,
        FOREIGN KEY (listId) REFERENCES lists(id)
      );

      CREATE TABLE IF NOT EXISTS items (
        id TEXT PRIMARY KEY,
        listId TEXT NOT NULL,
        createdAt INTEGER NOT NULL,
        updatedAt INTEGER NOT NULL,
        isDeleted INTEGER DEFAULT 0,
        FOREIGN KEY (listId) REFERENCES lists(id)
      );

      CREATE TABLE IF NOT EXISTS item_values (
        id TEXT PRIMARY KEY,
        itemId TEXT NOT NULL,
        fieldId TEXT NOT NULL,
        value TEXT,
        updatedAt INTEGER NOT NULL,
        FOREIGN KEY (itemId) REFERENCES items(id),
        FOREIGN KEY (fieldId) REFERENCES fields(id)
      );

      CREATE INDEX IF NOT EXISTS idx_fields_listId ON fields(listId);
      CREATE INDEX IF NOT EXISTS idx_items_listId ON items(listId);
      CREATE INDEX IF NOT EXISTS idx_item_values_itemId ON item_values(itemId);
      CREATE INDEX IF NOT EXISTS idx_item_values_fieldId ON item_values(fieldId);

      -- Notification events table (for remote-change notifications)
      CREATE TABLE IF NOT EXISTS notifications (
        id TEXT PRIMARY KEY,
        deviceIdOrigin TEXT,
        eventType TEXT NOT NULL,
        entityType TEXT NOT NULL,
        entityId TEXT,
        listId TEXT,
        createdAt INTEGER NOT NULL
      );
      CREATE INDEX IF NOT EXISTS idx_notifications_createdAt ON notifications(createdAt);
      CREATE INDEX IF NOT EXISTS idx_notifications_listId ON notifications(listId);
    `);
    // Attempt to add alignment column if upgrading an existing DB (ignore error if exists)
    try {
      this.db.exec(`ALTER TABLE fields ADD COLUMN alignment TEXT NOT NULL DEFAULT 'start'`);
    } catch (e) {
      // Column may already exist; ignore
    }
  }

  async queryAll(sql: string, params: Param[] = []): Promise<any[]> {
    const stmt = this.db.prepare(replaceQuotedIdentifiers(sql));
    return stmt.all(...params);
  }

  async queryOne(sql: string, params: Param[] = []): Promise<any | undefined> {
    const stmt = this.db.prepare(replaceQuotedIdentifiers(sql));
    return stmt.get(...params);
  }

  async execute(sql: string, params: Param[] = []): Promise<ExecResult> {
    const stmt = this.db.prepare(replaceQuotedIdentifiers(sql));
    const res = stmt.run(...params);
    return { changes: res.changes || 0 };
  }

  async transaction<T>(fn: (tx: DBAdapter) => Promise<T>): Promise<T> {
    const tx = this.db.transaction((innerFn: (tx: DBAdapter) => Promise<T>) => {
      // We can reuse the same adapter since better-sqlite3 is transactional per connection
      return innerFn(this);
    });
    return tx(fn);
  }
}

// ---------- Postgres Adapter ----------
class PostgresAdapter implements DBAdapter {
  private pool: Pool;

  constructor() {
    // Ensure BIGINT (int8) is parsed as number to avoid string timestamps
    // OID 20 = INT8, OID 1700 = NUMERIC (keep default for now)
    pgTypes.setTypeParser(20, (val: string) => parseInt(val, 10));
    const connectionString = process.env.DATABASE_URL;
    if (connectionString) {
      this.pool = new Pool({ connectionString });
    } else {
      this.pool = new Pool({
        host: process.env.PGHOST || 'localhost',
        port: parseInt(process.env.PGPORT || '5432', 10),
        user: process.env.PGUSER || 'postgres',
        password: process.env.PGPASSWORD || '',
        database: process.env.PGDATABASE || 'collabtable'
      });
    }
  }

  async initialize(): Promise<void> {
    // Create tables if not exist, keeping schema aligned with sqlite (isDeleted INTEGER 0/1)
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');
      await client.query(`
        CREATE TABLE IF NOT EXISTS lists (
          id TEXT PRIMARY KEY,
          name TEXT NOT NULL,
          createdAt BIGINT NOT NULL,
          updatedAt BIGINT NOT NULL,
          isDeleted INTEGER DEFAULT 0
        );
      `);
      await client.query(`
        CREATE TABLE IF NOT EXISTS fields (
          id TEXT PRIMARY KEY,
          name TEXT NOT NULL,
          fieldType TEXT NOT NULL,
          fieldOptions TEXT,
          alignment TEXT NOT NULL DEFAULT 'start',
          listId TEXT NOT NULL,
          "order" INTEGER NOT NULL,
          createdAt BIGINT NOT NULL,
          updatedAt BIGINT NOT NULL,
          isDeleted INTEGER DEFAULT 0,
          FOREIGN KEY (listId) REFERENCES lists(id)
        );
      `);
      await client.query(`
        CREATE TABLE IF NOT EXISTS items (
          id TEXT PRIMARY KEY,
          listId TEXT NOT NULL,
          createdAt BIGINT NOT NULL,
          updatedAt BIGINT NOT NULL,
          isDeleted INTEGER DEFAULT 0,
          FOREIGN KEY (listId) REFERENCES lists(id)
        );
      `);
      await client.query(`
        CREATE TABLE IF NOT EXISTS item_values (
          id TEXT PRIMARY KEY,
          itemId TEXT NOT NULL,
          fieldId TEXT NOT NULL,
          value TEXT,
          updatedAt BIGINT NOT NULL,
          FOREIGN KEY (itemId) REFERENCES items(id),
          FOREIGN KEY (fieldId) REFERENCES fields(id)
        );
      `);
      await client.query(`CREATE INDEX IF NOT EXISTS idx_fields_listId ON fields(listId);`);
      await client.query(`CREATE INDEX IF NOT EXISTS idx_items_listId ON items(listId);`);
      await client.query(`CREATE INDEX IF NOT EXISTS idx_item_values_itemId ON item_values(itemId);`);
      await client.query(`CREATE INDEX IF NOT EXISTS idx_item_values_fieldId ON item_values(fieldId);`);
      // Notifications table
      await client.query(`
        CREATE TABLE IF NOT EXISTS notifications (
          id TEXT PRIMARY KEY,
          deviceIdOrigin TEXT,
          eventType TEXT NOT NULL,
          entityType TEXT NOT NULL,
          entityId TEXT,
          listId TEXT,
          createdAt BIGINT NOT NULL
        );
      `);
      await client.query(`CREATE INDEX IF NOT EXISTS idx_notifications_createdAt ON notifications(createdAt);`);
      await client.query(`CREATE INDEX IF NOT EXISTS idx_notifications_listId ON notifications(listId);`);
      // Migrate existing DBs: ensure alignment column exists
      await client.query(`ALTER TABLE fields ADD COLUMN IF NOT EXISTS alignment TEXT NOT NULL DEFAULT 'start';`);
      await client.query('COMMIT');
    } catch (e) {
      await client.query('ROLLBACK');
      throw e;
    } finally {
      client.release();
    }
  }

  private async runQuery(sql: string, params: Param[] = [], client?: PoolClient): Promise<QueryResult<any>> {
    // Replace backticks and convert ? to $1..$n
    const text = convertQMarksToPg(replaceQuotedIdentifiers(sql));
    const runner: Pool | PoolClient = client || this.pool;
    return runner.query(text, params);
  }

  async queryAll(sql: string, params: Param[] = []): Promise<any[]> {
    const res = await this.runQuery(sql, params);
    return res.rows;
  }

  async queryOne(sql: string, params: Param[] = []): Promise<any | undefined> {
    const res = await this.runQuery(sql, params);
    return res.rows[0];
  }

  async execute(sql: string, params: Param[] = []): Promise<ExecResult> {
    const res = await this.runQuery(sql, params);
    return { changes: res.rowCount || 0 };
  }

  async transaction<T>(fn: (tx: DBAdapter) => Promise<T>): Promise<T> {
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');
      const txAdapter: DBAdapter = {
        initialize: async () => {},
        queryAll: (sql, params) => this.runQuery(sql, params, client).then(r => r.rows),
        queryOne: (sql, params) => this.runQuery(sql, params, client).then(r => r.rows[0]),
        execute: (sql, params) => this.runQuery(sql, params, client).then(r => ({ changes: r.rowCount || 0 })),
        transaction: async (inner) => {
          // Nested transactions are treated as flat for simplicity
          return inner(txAdapter);
        }
      } as DBAdapter;
      const result = await fn(txAdapter);
      await client.query('COMMIT');
      return result;
    } catch (e) {
      await client.query('ROLLBACK');
      throw e;
    } finally {
      client.release();
    }
  }
}

const clientType = (process.env.DB_CLIENT || process.env.DB_TYPE || 'sqlite').toLowerCase();
export const dbAdapter: DBAdapter = clientType === 'postgres' || clientType === 'postgresql'
  ? new PostgresAdapter()
  : new SqliteAdapter();

export async function initializeDatabase() {
  await dbAdapter.initialize();
}

export type { DBAdapter };
