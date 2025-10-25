import { Server as HttpServer, IncomingMessage } from 'http';
import { WebSocketServer, WebSocket } from 'ws';
import { dbAdapter } from './db';

interface SyncRequest {
  lastSyncTimestamp: number;
  lists: any[];
  fields: any[];
  items: any[];
  itemValues: any[];
}

interface MessageEnvelope<T = any> {
  id?: string;
  type: string;
  payload?: T;
}

function checkAuth(req: any): boolean {
  const serverPassword = process.env.SERVER_PASSWORD;
  if (!serverPassword) return true; // auth disabled
  const auth = req.headers?.authorization as string | undefined;
  if (!auth) return false;
  const parts = auth.split(' ');
  return parts.length === 2 && parts[0] === 'Bearer' && parts[1] === serverPassword;
}

const UPSERT_LIST = 'INSERT INTO lists (id, name, createdAt, updatedAt, isDeleted) VALUES (?, ?, ?, ?, ?) ON CONFLICT(id) DO UPDATE SET name = EXCLUDED.name, updatedAt = EXCLUDED.updatedAt, isDeleted = EXCLUDED.isDeleted';
const UPSERT_FIELD = 'INSERT INTO fields (id, name, fieldType, fieldOptions, listId, "order", createdAt, updatedAt, isDeleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(id) DO UPDATE SET name = EXCLUDED.name, fieldType = EXCLUDED.fieldType, fieldOptions = EXCLUDED.fieldOptions, "order" = EXCLUDED."order", updatedAt = EXCLUDED.updatedAt, isDeleted = EXCLUDED.isDeleted';
const UPSERT_ITEM = 'INSERT INTO items (id, listId, createdAt, updatedAt, isDeleted) VALUES (?, ?, ?, ?, ?) ON CONFLICT(id) DO UPDATE SET updatedAt = EXCLUDED.updatedAt, isDeleted = EXCLUDED.isDeleted';
const UPSERT_ITEM_VALUE = 'INSERT INTO item_values (id, itemId, fieldId, value, updatedAt) VALUES (?, ?, ?, ?, ?) ON CONFLICT(id) DO UPDATE SET value = EXCLUDED.value, updatedAt = EXCLUDED.updatedAt';

async function processSync(data: SyncRequest) {
  await dbAdapter.transaction(async (tx) => {
    for (const list of data.lists || []) {
      await tx.execute(UPSERT_LIST, [list.id, list.name, list.createdAt, list.updatedAt, list.isDeleted ? 1 : 0]);
    }
    for (const field of data.fields || []) {
      await tx.execute(UPSERT_FIELD, [field.id, field.name, field.fieldType, field.fieldOptions, field.listId, field.order, field.createdAt, field.updatedAt, field.isDeleted ? 1 : 0]);
    }
    for (const item of data.items || []) {
      await tx.execute(UPSERT_ITEM, [item.id, item.listId, item.createdAt, item.updatedAt, item.isDeleted ? 1 : 0]);
    }
    for (const val of data.itemValues || []) {
      await tx.execute(UPSERT_ITEM_VALUE, [val.id, val.itemId, val.fieldId, val.value, val.updatedAt]);
    }
  });

  let serverLists: any[], serverFields: any[], serverItems: any[], serverItemValues: any[];
  const since = data.lastSyncTimestamp;
  if (since === 0) {
    serverLists = await dbAdapter.queryAll('SELECT * FROM lists WHERE isDeleted = 0');
    serverFields = await dbAdapter.queryAll('SELECT * FROM fields WHERE isDeleted = 0');
    serverItems = await dbAdapter.queryAll('SELECT * FROM items WHERE isDeleted = 0');
    serverItemValues = await dbAdapter.queryAll('SELECT * FROM item_values');
  } else {
    serverLists = await dbAdapter.queryAll('SELECT * FROM lists WHERE updatedAt >= ?', [since]);
    serverFields = await dbAdapter.queryAll('SELECT * FROM fields WHERE updatedAt >= ?', [since]);
    serverItems = await dbAdapter.queryAll('SELECT * FROM items WHERE updatedAt >= ?', [since]);
    serverItemValues = await dbAdapter.queryAll('SELECT * FROM item_values WHERE updatedAt >= ?', [since]);
  }

  serverLists = serverLists.map(l => ({ ...l, isDeleted: !!l.isDeleted }));
  serverFields = serverFields.map(f => ({ ...f, isDeleted: !!f.isDeleted }));
  serverItems = serverItems.map(i => ({ ...i, isDeleted: !!i.isDeleted }));

  return {
    lists: serverLists,
    fields: serverFields,
    items: serverItems,
    itemValues: serverItemValues,
    serverTimestamp: Date.now(),
  };
}

export function initWebSocket(server: HttpServer) {
  const wss = new WebSocketServer({ server, path: '/api/ws' });
  console.log('WebSocket server initialized at /api/ws');

  wss.on('connection', (ws: WebSocket, req: IncomingMessage) => {
    if (!checkAuth(req)) {
      ws.close(1008, 'Unauthorized');
      return;
    }

  ws.on('message', async (raw: Buffer) => {
      try {
        const msg = JSON.parse(raw.toString()) as MessageEnvelope;
        if (msg.type === 'ping') {
          const resp: MessageEnvelope = { type: 'pong', id: msg.id };
          ws.send(JSON.stringify(resp));
          return;
        }
        if (msg.type === 'sync') {
          const respData = await processSync(msg.payload as SyncRequest);
          const resp: MessageEnvelope = { type: 'syncResponse', id: msg.id, payload: respData };
          ws.send(JSON.stringify(resp));
          return;
        }
      } catch (e) {
        const err: MessageEnvelope = { type: 'error', payload: { message: 'Invalid message' } };
        ws.send(JSON.stringify(err));
      }
    });
  });
}
