import { Router, Request, Response } from 'express';
import { db } from '../database';

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

// Print stats every 30 seconds
setInterval(() => {
  if (syncStats.totalSyncs > 0) {
    const elapsed = Math.round((Date.now() - syncStats.lastReset) / 1000);
    console.log(`\n[STATS] Last ${elapsed}s: ${syncStats.totalSyncs} syncs`);
    console.log(`  Received: ${syncStats.listsReceived} lists, ${syncStats.fieldsReceived} fields, ${syncStats.itemsReceived} items, ${syncStats.valuesReceived} values`);
    console.log(`  Sent: ${syncStats.listsSent} lists, ${syncStats.fieldsSent} fields, ${syncStats.itemsSent} items, ${syncStats.valuesSent} values\n`);
    
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
}, 30000);

// Helper function to get prepared statements (lazy initialization)
function getUpsertStatements() {
  return {
    upsertList: db.prepare(`
      INSERT INTO lists (id, name, createdAt, updatedAt, isDeleted)
      VALUES (@id, @name, @createdAt, @updatedAt, @isDeleted)
      ON CONFLICT(id) DO UPDATE SET
        name = @name,
        updatedAt = @updatedAt,
        isDeleted = @isDeleted
    `),
    upsertField: db.prepare(`
      INSERT INTO fields (id, name, fieldType, fieldOptions, listId, \`order\`, createdAt, updatedAt, isDeleted)
      VALUES (@id, @name, @fieldType, @fieldOptions, @listId, @order, @createdAt, @updatedAt, @isDeleted)
      ON CONFLICT(id) DO UPDATE SET
        name = @name,
        fieldType = @fieldType,
        fieldOptions = @fieldOptions,
        \`order\` = @order,
        updatedAt = @updatedAt,
        isDeleted = @isDeleted
    `),
    upsertItem: db.prepare(`
      INSERT INTO items (id, listId, createdAt, updatedAt, isDeleted)
      VALUES (@id, @listId, @createdAt, @updatedAt, @isDeleted)
      ON CONFLICT(id) DO UPDATE SET
        updatedAt = @updatedAt,
        isDeleted = @isDeleted
    `),
    upsertItemValue: db.prepare(`
      INSERT INTO item_values (id, itemId, fieldId, value, updatedAt)
      VALUES (@id, @itemId, @fieldId, @value, @updatedAt)
      ON CONFLICT(id) DO UPDATE SET
        value = @value,
        updatedAt = @updatedAt
    `)
  };
}

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
    
    // Log only when there's actual data being synced
    const hasIncomingData = incomingLists > 0 || incomingFields > 0 || incomingItems > 0 || incomingValues > 0;
    if (hasIncomingData) {
      console.log(`[SYNC] Received: ${incomingLists} lists, ${incomingFields} fields, ${incomingItems} items, ${incomingValues} values`);
      // Log list details
      if (lists && lists.length > 0) {
        lists.forEach(list => {
          console.log(`  List: ${list.id} - "${list.name}" (updated: ${list.updatedAt}, deleted: ${list.isDeleted})`);
        });
      }
    }

    // Save incoming data from client using a transaction
    const transaction = db.transaction((data: SyncRequest) => {
      // Get prepared statements lazily
      const { upsertList, upsertField, upsertItem, upsertItemValue } = getUpsertStatements();

      if (data.lists && data.lists.length > 0) {
        for (const list of data.lists) {
          upsertList.run({
            id: list.id,
            name: list.name,
            createdAt: list.createdAt,
            updatedAt: list.updatedAt,
            isDeleted: list.isDeleted ? 1 : 0
          });
          console.log(`  Saved list ${list.id}`);
        }
      }

      if (data.fields && data.fields.length > 0) {
        for (const field of data.fields) {
          upsertField.run({
            id: field.id,
            name: field.name,
            fieldType: field.fieldType,
            fieldOptions: field.fieldOptions,
            listId: field.listId,
            order: field.order,
            createdAt: field.createdAt,
            updatedAt: field.updatedAt,
            isDeleted: field.isDeleted ? 1 : 0
          });
        }
      }

      if (data.items && data.items.length > 0) {
        for (const item of data.items) {
          upsertItem.run({
            id: item.id,
            listId: item.listId,
            createdAt: item.createdAt,
            updatedAt: item.updatedAt,
            isDeleted: item.isDeleted ? 1 : 0
          });
        }
      }

      if (data.itemValues && data.itemValues.length > 0) {
        for (const value of data.itemValues) {
          upsertItemValue.run({
            id: value.id,
            itemId: value.itemId,
            fieldId: value.fieldId,
            value: value.value,
            updatedAt: value.updatedAt
          });
        }
      }
    });

    transaction({ lastSyncTimestamp, lists, fields, items, itemValues });

    // Get updates from server since last sync
    // If lastSyncTimestamp is 0, get all data (initial sync)
    // IMPORTANT: Include deleted items so clients can sync deletions
    let serverLists, serverFields, serverItems, serverItemValues;

    if (lastSyncTimestamp === 0) {
      // Initial sync: send all non-deleted items
      serverLists = db.prepare('SELECT * FROM lists WHERE isDeleted = 0').all();
      serverFields = db.prepare('SELECT * FROM fields WHERE isDeleted = 0').all();
      serverItems = db.prepare('SELECT * FROM items WHERE isDeleted = 0').all();
      serverItemValues = db.prepare('SELECT * FROM item_values').all();
    } else {
      // Incremental sync: send all updates including deletions (use >= to include items updated exactly at lastSyncTimestamp)
      serverLists = db.prepare('SELECT * FROM lists WHERE updatedAt >= ?').all(lastSyncTimestamp);
      serverFields = db.prepare('SELECT * FROM fields WHERE updatedAt >= ?').all(lastSyncTimestamp);
      serverItems = db.prepare('SELECT * FROM items WHERE updatedAt >= ?').all(lastSyncTimestamp);
      serverItemValues = db.prepare('SELECT * FROM item_values WHERE updatedAt >= ?').all(lastSyncTimestamp);
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
    
    // Log only when sending data back
    const hasOutgoingData = (serverLists as any[]).length > 0 || (serverFields as any[]).length > 0 || (serverItems as any[]).length > 0 || (serverItemValues as any[]).length > 0;
    if (hasOutgoingData) {
      console.log(`[SYNC] Sending: ${(serverLists as any[]).length} lists, ${(serverFields as any[]).length} fields, ${(serverItems as any[]).length} items, ${(serverItemValues as any[]).length} values`);
    }

    res.json({
      lists: serverLists,
      fields: serverFields,
      items: serverItems,
      itemValues: serverItemValues,
      serverTimestamp
    });
  } catch (error) {
    console.error('[SYNC] Error:', error);
    res.status(500).json({ error: 'Sync failed' });
  }
});

export default router;
