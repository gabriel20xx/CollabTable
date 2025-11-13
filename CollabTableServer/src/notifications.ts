import { dbAdapter, DBAdapter } from './db';
import crypto from 'crypto';

export type NotificationEvent = {
  id: string;
  deviceIdOrigin?: string;
  eventType: string; // created | updated | deleted | listContentUpdated | listEdited ...
  entityType: string; // list | field | item | value
  entityId?: string;
  listId?: string;
  createdAt: number; // ms epoch
};

function genId() {
  try {
    return crypto.randomUUID();
  } catch {
    return `${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 10)}`;
  }
}

export async function enqueueNotification(
  adapter: DBAdapter,
  deviceIdOrigin: string | undefined,
  eventType: string,
  entityType: string,
  entityId?: string,
  listId?: string,
  createdAt?: number
) {
  const id = genId();
  const ts = createdAt ?? Date.now();
  await adapter.execute(
    'INSERT INTO notifications (id, deviceIdOrigin, eventType, entityType, entityId, listId, createdAt) VALUES (?, ?, ?, ?, ?, ?, ?)',
    [id, deviceIdOrigin ?? null, eventType, entityType, entityId ?? null, listId ?? null, ts]
  );
}

// Retention policy
const DEFAULT_RETENTION_MS = 7 * 24 * 60 * 60 * 1000; // 7 days
const CLEAN_INTERVAL_MS = 60 * 60 * 1000; // hourly

export function startNotificationCleanup() {
  const retentionMs = Number(process.env.NOTIF_RETENTION_MS || DEFAULT_RETENTION_MS);
  setInterval(async () => {
    try {
      const cutoff = Date.now() - retentionMs;
      await dbAdapter.execute('DELETE FROM notifications WHERE createdAt < ?', [cutoff]);
    } catch (e) {
      console.warn('[NOTIF] Retention cleanup failed:', e);
    }
  }, CLEAN_INTERVAL_MS).unref?.();
}
