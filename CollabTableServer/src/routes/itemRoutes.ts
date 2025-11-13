import { Router, Request, Response } from 'express';
import { dbAdapter } from '../db';
import { enqueueNotification } from '../notifications';

const router = Router();

// Get items for a list
router.get('/list/:listId', async (req: Request, res: Response) => {
  try {
  const items = await dbAdapter.queryAll('SELECT * FROM items WHERE listId = ? AND isDeleted = 0 ORDER BY updatedAt DESC', [req.params.listId]);
    const formattedItems = (items as any[]).map(item => ({ ...item, isDeleted: !!item.isDeleted }));
    res.json(formattedItems);
  } catch (error) {
    res.status(500).json({ error: 'Failed to fetch items' });
  }
});

// Get item values
router.get('/:itemId/values', async (req: Request, res: Response) => {
  try {
  const values = await dbAdapter.queryAll('SELECT * FROM item_values WHERE itemId = ?', [req.params.itemId]);
    res.json(values);
  } catch (error) {
    res.status(500).json({ error: 'Failed to fetch item values' });
  }
});

// Create item
router.post('/', async (req: Request, res: Response) => {
  try {
    const { id, listId, createdAt, updatedAt, isDeleted } = req.body;
    await dbAdapter.execute(
      'INSERT INTO items (id, listId, createdAt, updatedAt, isDeleted) VALUES (?, ?, ?, ?, ?)',
      [id, listId, createdAt, updatedAt, isDeleted ? 1 : 0]
    );
    const item = await dbAdapter.queryOne('SELECT * FROM items WHERE id = ?', [id]);
    await enqueueNotification(dbAdapter, (req as any).deviceId, 'created', 'item', id, listId, Date.now());
    res.status(201).json({ ...(item as any), isDeleted: !!(item as any).isDeleted });
  } catch (error) {
    res.status(500).json({ error: 'Failed to create item' });
  }
});

// Create/update item value
router.post('/values', async (req: Request, res: Response) => {
  try {
    const { id, itemId, fieldId, value, updatedAt } = req.body;
    await dbAdapter.execute(
      'INSERT INTO item_values (id, itemId, fieldId, value, updatedAt) VALUES (?, ?, ?, ?, ?) ON CONFLICT(id) DO UPDATE SET value = ?, updatedAt = ?',
      [id, itemId, fieldId, value, updatedAt, value, updatedAt]
    );
    const itemValue = await dbAdapter.queryOne('SELECT * FROM item_values WHERE id = ?', [id]);
    // Notify list content updated (coarse event) using field's listId
    try {
      const field = await dbAdapter.queryOne('SELECT listId FROM fields WHERE id = ?', [fieldId]);
      const listId = field ? ((field as any).listId ?? (field as any).listid) : undefined;
      if (listId) {
        await enqueueNotification(dbAdapter, (req as any).deviceId, 'listContentUpdated', 'value', id, listId, Date.now());
      }
    } catch {}
    res.json(itemValue);
  } catch (error) {
    res.status(500).json({ error: 'Failed to save item value' });
  }
});

// Update item
router.put('/:id', async (req: Request, res: Response) => {
  try {
    const updatedAt = Date.now();
    const result = await dbAdapter.execute(
      'UPDATE items SET updatedAt = ? WHERE id = ?',
      [updatedAt, req.params.id]
    );
    
    if (result.changes === 0) {
      return res.status(404).json({ error: 'Item not found' });
    }
    
  const item = await dbAdapter.queryOne('SELECT * FROM items WHERE id = ?', [req.params.id]);
    await enqueueNotification(dbAdapter, (req as any).deviceId, 'updated', 'item', req.params.id, (item as any)?.listId ?? (item as any)?.listid, updatedAt);
    res.json({ ...(item as any), isDeleted: !!(item as any).isDeleted });
  } catch (error) {
    res.status(500).json({ error: 'Failed to update item' });
  }
});

// Delete item (soft delete)
router.delete('/:id', async (req: Request, res: Response) => {
  try {
    const updatedAt = Date.now();
    const result = await dbAdapter.execute(
      'UPDATE items SET isDeleted = 1, updatedAt = ? WHERE id = ?',
      [updatedAt, req.params.id]
    );
    
    if (result.changes === 0) {
      return res.status(404).json({ error: 'Item not found' });
    }
    try {
      const item = await dbAdapter.queryOne('SELECT * FROM items WHERE id = ?', [req.params.id]);
      const listId = item ? ((item as any).listId ?? (item as any).listid) : undefined;
      await enqueueNotification(dbAdapter, (req as any).deviceId, 'deleted', 'item', req.params.id, listId, updatedAt);
    } catch {}
    res.json({ message: 'Item deleted successfully' });
  } catch (error) {
    res.status(500).json({ error: 'Failed to delete item' });
  }
});

export default router;
