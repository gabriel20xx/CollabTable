import { Router, Request, Response } from 'express';
import { dbAdapter } from '../db';

const router = Router();

interface SyncRequest {
  lastSyncTimestamp: number;
  lists: any[];
  fields: any[];
  items: any[];
  itemValues: any[];
}

// Stats tracking
let syncStats = {
  totalSyncs: 0,
  listsReceived: 0,
  fieldsReceived: 0,
  itemsReceived: 0,
  valuesReceived: 0,
  listsSent: 0,
  fieldsSent: 0,
  itemsSent: 0,
  valuesSent: 0,
  lastReset: Date.now()
};

// Print stats every 60 seconds (reduced spam)
setInterval(() => {
  if (syncStats.totalSyncs > 0) {
    const elapsed = Math.round((Date.now() - syncStats.lastReset) / 1000);
    console.log(`\n[STATS] Sync Summary (last ${elapsed}s):`);
    console.log(`   ${syncStats.totalSyncs} sync requests processed`);
    
    if (syncStats.listsReceived > 0 || syncStats.fieldsReceived > 0 || syncStats.itemsReceived > 0 || syncStats.valuesReceived > 0) {
      console.log(`   [IN] Received: ${syncStats.listsReceived} lists, ${syncStats.fieldsReceived} fields, ${syncStats.itemsReceived} items, ${syncStats.valuesReceived} values`);
    }
    
    if (syncStats.listsSent > 0 || syncStats.fieldsSent > 0 || syncStats.itemsSent > 0 || syncStats.valuesSent > 0) {
      console.log(`   [OUT] Sent: ${syncStats.listsSent} lists, ${syncStats.fieldsSent} fields, ${syncStats.itemsSent} items, ${syncStats.valuesSent} values`);
    }
    
    // Reset stats
    syncStats = {
      totalSyncs: 0,
      listsReceived: 0,
      fieldsReceived: 0,
      itemsReceived: 0,
      valuesReceived: 0,
      listsSent: 0,
      fieldsSent: 0,
      itemsSent: 0,
      valuesSent: 0,
      lastReset: Date.now()
    };
  }
}, 60000); // Changed from 30s to 60s

// Helper function to get prepared statements (lazy initialization)
// We'll perform upserts with positional params for cross-DB portability
const UPSERT_LIST = 'INSERT INTO lists (id, name, createdAt, updatedAt, isDeleted) VALUES (?, ?, ?, ?, ?) ON CONFLICT(id) DO UPDATE SET name = EXCLUDED.name, updatedAt = EXCLUDED.updatedAt, isDeleted = EXCLUDED.isDeleted';
const UPSERT_FIELD = 'INSERT INTO fields (id, name, fieldType, fieldOptions, alignment, listId, "order", createdAt, updatedAt, isDeleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(id) DO UPDATE SET name = EXCLUDED.name, fieldType = EXCLUDED.fieldType, fieldOptions = EXCLUDED.fieldOptions, alignment = EXCLUDED.alignment, "order" = EXCLUDED."order", updatedAt = EXCLUDED.updatedAt, isDeleted = EXCLUDED.isDeleted';
const UPSERT_ITEM = 'INSERT INTO items (id, listId, createdAt, updatedAt, isDeleted) VALUES (?, ?, ?, ?, ?) ON CONFLICT(id) DO UPDATE SET updatedAt = EXCLUDED.updatedAt, isDeleted = EXCLUDED.isDeleted';
const UPSERT_ITEM_VALUE = 'INSERT INTO item_values (id, itemId, fieldId, value, updatedAt) VALUES (?, ?, ?, ?, ?) ON CONFLICT(id) DO UPDATE SET value = EXCLUDED.value, updatedAt = EXCLUDED.updatedAt';

router.post('/sync', async (req: Request, res: Response) => {
  try {
    const { lastSyncTimestamp, lists, fields, items, itemValues }: SyncRequest = req.body;
    
    // Track stats
    syncStats.totalSyncs++;
    const incomingLists = lists?.length || 0;
    const incomingFields = fields?.length || 0;
    const incomingItems = items?.length || 0;
    const incomingValues = itemValues?.length || 0;
    
    syncStats.listsReceived += incomingLists;
    syncStats.fieldsReceived += incomingFields;
    syncStats.itemsReceived += incomingItems;
    syncStats.valuesReceived += incomingValues;
    
    // Only log significant sync events (not empty syncs)
    const hasIncomingData = incomingLists > 0 || incomingFields > 0 || incomingItems > 0 || incomingValues > 0;
    const isInitialSync = lastSyncTimestamp === 0;
    
    if (hasIncomingData) {
      const timestamp = new Date().toLocaleTimeString();
      console.log(`\n[SYNC] [${timestamp}] Receiving data from client`);
      
      if (incomingLists > 0) {
        console.log(`   [LISTS] ${incomingLists} list(s)`);
        lists.forEach(list => {
          const action = list.isDeleted ? 'Deleted' : (lastSyncTimestamp === 0 ? 'Created' : 'Updated');
          console.log(`      ${action}: "${list.name}"`);
        });
      }
      
      if (incomingFields > 0) {
        console.log(`   [FIELDS] ${incomingFields} field(s)`);
      }
      
      if (incomingItems > 0) {
        console.log(`   [ITEMS] ${incomingItems} item(s)`);
      }
      
      if (incomingValues > 0) {
        console.log(`   [VALUES] ${incomingValues} value(s)`);
      }
    } else if (isInitialSync) {
      const timestamp = new Date().toLocaleTimeString();
      console.log(`\n[SYNC] [${timestamp}] Initial sync from new client`);
    }

    // Save incoming data from client using a transaction
    await dbAdapter.transaction(async (tx) => {
      if (lists && lists.length > 0) {
        for (const list of lists) {
          await tx.execute(UPSERT_LIST, [
            list.id,
            list.name,
            list.createdAt,
            list.updatedAt,
            list.isDeleted ? 1 : 0
          ]);
          // If a list was deleted, cascade the deletion to child records on server
          // - mark fields and items as deleted (tombstones) so other clients learn about them
          // - remove item_values belonging to items under this list (no tombstone support for values)
          if (list.isDeleted) {
            try {
              await tx.execute('UPDATE fields SET isDeleted = 1, updatedAt = ? WHERE listId = ?', [list.updatedAt, list.id]);
              await tx.execute('UPDATE items SET isDeleted = 1, updatedAt = ? WHERE listId = ?', [list.updatedAt, list.id]);
              await tx.execute('DELETE FROM item_values WHERE itemId IN (SELECT id FROM items WHERE listId = ?)', [list.id]);
            } catch (err) {
              console.warn('[SYNC] Cascade delete for list failed:', String(err));
            }
          }
        }
      }

      if (fields && fields.length > 0) {
        for (const field of fields) {
          await tx.execute(UPSERT_FIELD, [
            field.id,
            field.name,
            field.fieldType,
            field.fieldOptions,
            (field.alignment ?? 'start'),
            field.listId,
            field.order,
            field.createdAt,
            field.updatedAt,
            field.isDeleted ? 1 : 0
          ]);
          // If a field was deleted, remove any item_values referencing it so clients converge
          if (field.isDeleted) {
            try {
              await tx.execute('DELETE FROM item_values WHERE fieldId = ?', [field.id]);
            } catch (err) {
              console.warn('[SYNC] Cascade delete for field failed:', String(err));
            }
          }
        }
      }

      if (items && items.length > 0) {
        for (const item of items) {
          await tx.execute(UPSERT_ITEM, [
            item.id,
            item.listId,
            item.createdAt,
            item.updatedAt,
            item.isDeleted ? 1 : 0
          ]);
          // If an item was deleted, remove its values (no tombstone support for values)
          if (item.isDeleted) {
            try {
              await tx.execute('DELETE FROM item_values WHERE itemId = ?', [item.id]);
            } catch (err) {
              console.warn('[SYNC] Cascade delete for item failed:', String(err));
            }
          }
        }
      }

      // Before inserting item values, verify parent item and field existence to avoid FK violations (PG strictness)
      const orphanValues: any[] = [];
      if (itemValues && itemValues.length > 0) {
        // Build distinct parent id lists
        const distinctItemIds = [...new Set(itemValues.map(v => v.itemId).filter(Boolean))];
        const distinctFieldIds = [...new Set(itemValues.map(v => v.fieldId).filter(Boolean))];

        // Helper to build IN clause placeholders (?,?,?) matching adapter's ? substitution
        const makeIn = (arr: string[]) => arr.map(() => '?').join(', ');

  let existingItemIds = new Set<string>();
  let existingFieldIds = new Set<string>();
  const fieldToList: Record<string, string> = {};
        try {
          if (distinctItemIds.length > 0) {
            const rows = await tx.queryAll(`SELECT id FROM items WHERE id IN (${makeIn(distinctItemIds)})`, distinctItemIds);
            existingItemIds = new Set(rows.map((r: any) => r.id));
          }
          if (distinctFieldIds.length > 0) {
            const rows = await tx.queryAll(`SELECT id, listId FROM fields WHERE id IN (${makeIn(distinctFieldIds)})`, distinctFieldIds);
            existingFieldIds = new Set(rows.map((r: any) => r.id));
            rows.forEach((r: any) => { fieldToList[r.id] = r.listid ?? r.listId; });
          }
        } catch (e) {
          console.warn('[SYNC] Failed preloading parent ids for values:', e);
        }

        for (const value of itemValues) {
          const hasItem = existingItemIds.has(value.itemId);
          const hasField = existingFieldIds.has(value.fieldId);
          if (!hasField) {
            // Missing field -> skip value
            orphanValues.push({ id: value.id, itemId: value.itemId, fieldId: value.fieldId, reason: 'missingField' });
            continue;
          }
          // If item missing but field exists, synthesize a stub item to preserve referential integrity
          if (!hasItem) {
            const inferredListId = fieldToList[value.fieldId];
            if (!inferredListId) {
              orphanValues.push({ id: value.id, itemId: value.itemId, fieldId: value.fieldId, reason: 'missingItemNoList' });
              continue;
            }
            const ts = value.updatedAt ?? Date.now();
            try {
              await tx.execute(UPSERT_ITEM, [
                value.itemId,
                inferredListId,
                ts,
                ts,
                0
              ]);
              existingItemIds.add(value.itemId);
              console.warn('[SYNC] Created stub item for orphan value:', value.itemId, 'in list', inferredListId);
            } catch (e) {
              orphanValues.push({ id: value.id, itemId: value.itemId, fieldId: value.fieldId, reason: 'failedCreateStubItem' });
              continue;
            }
          }
          try {
            await tx.execute(UPSERT_ITEM_VALUE, [
              value.id,
              value.itemId,
              value.fieldId,
              value.value,
              value.updatedAt
            ]);
          } catch (err: any) {
            // Catch FK violation just in case race or deletion happened inside same sync
            if (err && err.code === '23503') {
              orphanValues.push({ id: value.id, itemId: value.itemId, fieldId: value.fieldId, reason: 'fkViolation' });
              console.warn('[SYNC] Skipped value due to FK violation:', value.id, '->', err.detail || err.message);
              continue;
            }
            throw err; // rethrow other errors
          }
        }

        if (orphanValues.length > 0) {
          console.warn(`[SYNC] Skipped ${orphanValues.length} orphan item_value record(s); will inform client.`);
        }
        // Attach orphanValues to request context (not ideal, but simplest without altering adapter signature)
        // We'll include them in response below.
        (req as any)._orphanValues = orphanValues;
      }
    });

    // IMPORTANT: Get updates from server AFTER saving incoming data
    // This ensures clients receive back any data they just sent (for confirmation)
    // If lastSyncTimestamp is 0, get all data (initial sync)
    // IMPORTANT: Include deleted items so clients can sync deletions
    let serverLists, serverFields, serverItems, serverItemValues;

    if (lastSyncTimestamp === 0) {
      // Include deleted rows on initial sync to propagate tombstones
      serverLists = await dbAdapter.queryAll('SELECT * FROM lists');
      serverFields = await dbAdapter.queryAll('SELECT * FROM fields');
      serverItems = await dbAdapter.queryAll('SELECT * FROM items');
      serverItemValues = await dbAdapter.queryAll('SELECT * FROM item_values');
    } else {
      serverLists = await dbAdapter.queryAll('SELECT * FROM lists WHERE updatedAt >= ?', [lastSyncTimestamp]);
      serverFields = await dbAdapter.queryAll('SELECT * FROM fields WHERE updatedAt >= ?', [lastSyncTimestamp]);
      serverItems = await dbAdapter.queryAll('SELECT * FROM items WHERE updatedAt >= ?', [lastSyncTimestamp]);
      serverItemValues = await dbAdapter.queryAll('SELECT * FROM item_values WHERE updatedAt >= ?', [lastSyncTimestamp]);
    }

    // Normalize output for clients (camelCase keys, ms timestamps, boolean isDeleted)
    const toMillis = (v: any): number | null => {
      if (v === null || v === undefined) return null;
      if (typeof v === 'number') return v < 1e12 ? Math.trunc(v * 1000) : Math.trunc(v);
      if (typeof v === 'string') {
        if (/^\d+$/.test(v)) {
          const n = Number(v);
          return n < 1e12 ? Math.trunc(n * 1000) : Math.trunc(n);
        }
        const t = Date.parse(v);
        return Number.isNaN(t) ? null : Math.trunc(t);
      }
      return null;
    };
    const pick = (obj: any, a: string, b: string) => obj[a] ?? obj[b];

    const normLists = (serverLists as any[]).map(l => ({
      id: l.id,
      name: l.name,
      createdAt: toMillis(pick(l, 'createdAt', 'createdat')),
      updatedAt: toMillis(pick(l, 'updatedAt', 'updatedat')),
      isDeleted: !!pick(l, 'isDeleted', 'isdeleted'),
      // orderIndex is local-only, do not include
    }));
    const normFields = (serverFields as any[]).map(f => ({
      id: f.id,
      listId: pick(f, 'listId', 'listid'),
      name: f.name,
      fieldType: f.fieldType ?? f.fieldtype,
      fieldOptions: f.fieldOptions ?? f.fieldoptions ?? '',
      alignment: f.alignment ?? 'start',
      order: f.order,
      createdAt: toMillis(pick(f, 'createdAt', 'createdat')),
      updatedAt: toMillis(pick(f, 'updatedAt', 'updatedat')),
      isDeleted: !!pick(f, 'isDeleted', 'isdeleted'),
    }));
    const normItems = (serverItems as any[]).map(i => ({
      id: i.id,
      listId: pick(i, 'listId', 'listid'),
      createdAt: toMillis(pick(i, 'createdAt', 'createdat')),
      updatedAt: toMillis(pick(i, 'updatedAt', 'updatedat')),
      isDeleted: !!pick(i, 'isDeleted', 'isdeleted'),
    }));
    const normValues = (serverItemValues as any[]).map(v => ({
      id: v.id,
      itemId: pick(v, 'itemId', 'itemid'),
      fieldId: pick(v, 'fieldId', 'fieldid'),
      value: v.value,
      updatedAt: toMillis(pick(v, 'updatedAt', 'updatedat')),
    }));

    const serverTimestamp = Date.now();
    
    // Track outgoing stats
    syncStats.listsSent += (serverLists as any[]).length;
    syncStats.fieldsSent += (serverFields as any[]).length;
    syncStats.itemsSent += (serverItems as any[]).length;
    syncStats.valuesSent += (serverItemValues as any[]).length;
    
    // Only log when sending significant data back
    const hasOutgoingData = (serverLists as any[]).length > 0 || (serverFields as any[]).length > 0 || (serverItems as any[]).length > 0 || (serverItemValues as any[]).length > 0;
    if (hasOutgoingData && !isInitialSync) {
      console.log(`   [OUT] Sending back: ${(serverLists as any[]).length} lists, ${(serverFields as any[]).length} fields, ${(serverItems as any[]).length} items, ${(serverItemValues as any[]).length} values`);
    } else if (isInitialSync && hasOutgoingData) {
      console.log(`   [OUT] Sending initial data: ${(serverLists as any[]).length} lists, ${(serverFields as any[]).length} fields, ${(serverItems as any[]).length} items, ${(serverItemValues as any[]).length} values`);
    }

    res.json({
      lists: normLists,
      fields: normFields,
      items: normItems,
      itemValues: normValues,
      orphanItemValues: (req as any)._orphanValues || [],
      serverTimestamp
    });
  } catch (error) {
    console.error('[ERROR] Sync error:', error);
    res.status(500).json({ error: 'Sync failed' });
  }
});

export default router;
