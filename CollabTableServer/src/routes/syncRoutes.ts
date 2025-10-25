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
const UPSERT_FIELD = 'INSERT INTO fields (id, name, fieldType, fieldOptions, listId, "order", createdAt, updatedAt, isDeleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(id) DO UPDATE SET name = EXCLUDED.name, fieldType = EXCLUDED.fieldType, fieldOptions = EXCLUDED.fieldOptions, "order" = EXCLUDED."order", updatedAt = EXCLUDED.updatedAt, isDeleted = EXCLUDED.isDeleted';
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
        }
      }

      if (fields && fields.length > 0) {
        for (const field of fields) {
          await tx.execute(UPSERT_FIELD, [
            field.id,
            field.name,
            field.fieldType,
            field.fieldOptions,
            field.listId,
            field.order,
            field.createdAt,
            field.updatedAt,
            field.isDeleted ? 1 : 0
          ]);
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
        }
      }

      if (itemValues && itemValues.length > 0) {
        for (const value of itemValues) {
          await tx.execute(UPSERT_ITEM_VALUE, [
            value.id,
            value.itemId,
            value.fieldId,
            value.value,
            value.updatedAt
          ]);
        }
      }
    });

    // IMPORTANT: Get updates from server AFTER saving incoming data
    // This ensures clients receive back any data they just sent (for confirmation)
    // If lastSyncTimestamp is 0, get all data (initial sync)
    // IMPORTANT: Include deleted items so clients can sync deletions
    let serverLists, serverFields, serverItems, serverItemValues;

    if (lastSyncTimestamp === 0) {
      serverLists = await dbAdapter.queryAll('SELECT * FROM lists WHERE isDeleted = 0');
      serverFields = await dbAdapter.queryAll('SELECT * FROM fields WHERE isDeleted = 0');
      serverItems = await dbAdapter.queryAll('SELECT * FROM items WHERE isDeleted = 0');
      serverItemValues = await dbAdapter.queryAll('SELECT * FROM item_values');
    } else {
      serverLists = await dbAdapter.queryAll('SELECT * FROM lists WHERE updatedAt >= ?', [lastSyncTimestamp]);
      serverFields = await dbAdapter.queryAll('SELECT * FROM fields WHERE updatedAt >= ?', [lastSyncTimestamp]);
      serverItems = await dbAdapter.queryAll('SELECT * FROM items WHERE updatedAt >= ?', [lastSyncTimestamp]);
      serverItemValues = await dbAdapter.queryAll('SELECT * FROM item_values WHERE updatedAt >= ?', [lastSyncTimestamp]);
    }

    // Convert isDeleted from INTEGER (0/1) to BOOLEAN
    serverLists = (serverLists as any[]).map(list => ({ ...list, isDeleted: !!list.isDeleted }));
    serverFields = (serverFields as any[]).map(field => ({ ...field, isDeleted: !!field.isDeleted }));
    serverItems = (serverItems as any[]).map(item => ({ ...item, isDeleted: !!item.isDeleted }));

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
      lists: serverLists,
      fields: serverFields,
      items: serverItems,
      itemValues: serverItemValues,
      serverTimestamp
    });
  } catch (error) {
    console.error('[ERROR] Sync error:', error);
    res.status(500).json({ error: 'Sync failed' });
  }
});

export default router;
