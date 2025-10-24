import { Router, Request, Response } from 'express';
import { db } from '../database';

const router = Router();

// Get items for a list
router.get('/list/:listId', async (req: Request, res: Response) => {
  try {
    const items = db.prepare('SELECT * FROM items WHERE listId = ? AND isDeleted = 0 ORDER BY updatedAt DESC').all(req.params.listId);
    const formattedItems = (items as any[]).map(item => ({ ...item, isDeleted: !!item.isDeleted }));
    res.json(formattedItems);
  } catch (error) {
    res.status(500).json({ error: 'Failed to fetch items' });
  }
});

// Get item values
router.get('/:itemId/values', async (req: Request, res: Response) => {
  try {
    const values = db.prepare('SELECT * FROM item_values WHERE itemId = ?').all(req.params.itemId);
    res.json(values);
  } catch (error) {
    res.status(500).json({ error: 'Failed to fetch item values' });
  }
});

// Create item
router.post('/', async (req: Request, res: Response) => {
  try {
    const { id, listId, createdAt, updatedAt, isDeleted } = req.body;
    db.prepare(`
      INSERT INTO items (id, listId, createdAt, updatedAt, isDeleted)
      VALUES (?, ?, ?, ?, ?)
    `).run(id, listId, createdAt, updatedAt, isDeleted ? 1 : 0);
    
    const item = db.prepare('SELECT * FROM items WHERE id = ?').get(id);
    res.status(201).json({ ...(item as any), isDeleted: !!(item as any).isDeleted });
  } catch (error) {
    res.status(500).json({ error: 'Failed to create item' });
  }
});

// Create/update item value
router.post('/values', async (req: Request, res: Response) => {
  try {
    const { id, itemId, fieldId, value, updatedAt } = req.body;
    db.prepare(`
      INSERT INTO item_values (id, itemId, fieldId, value, updatedAt)
      VALUES (?, ?, ?, ?, ?)
      ON CONFLICT(id) DO UPDATE SET
        value = ?,
        updatedAt = ?
    `).run(id, itemId, fieldId, value, updatedAt, value, updatedAt);
    
    const itemValue = db.prepare('SELECT * FROM item_values WHERE id = ?').get(id);
    res.json(itemValue);
  } catch (error) {
    res.status(500).json({ error: 'Failed to save item value' });
  }
});

// Update item
router.put('/:id', async (req: Request, res: Response) => {
  try {
    const updatedAt = Date.now();
    const result = db.prepare(`
      UPDATE items 
      SET updatedAt = ?
      WHERE id = ?
    `).run(updatedAt, req.params.id);
    
    if (result.changes === 0) {
      return res.status(404).json({ error: 'Item not found' });
    }
    
    const item = db.prepare('SELECT * FROM items WHERE id = ?').get(req.params.id);
    res.json({ ...(item as any), isDeleted: !!(item as any).isDeleted });
  } catch (error) {
    res.status(500).json({ error: 'Failed to update item' });
  }
});

// Delete item (soft delete)
router.delete('/:id', async (req: Request, res: Response) => {
  try {
    const updatedAt = Date.now();
    const result = db.prepare(`
      UPDATE items 
      SET isDeleted = 1, updatedAt = ?
      WHERE id = ?
    `).run(updatedAt, req.params.id);
    
    if (result.changes === 0) {
      return res.status(404).json({ error: 'Item not found' });
    }
    
    res.json({ message: 'Item deleted successfully' });
  } catch (error) {
    res.status(500).json({ error: 'Failed to delete item' });
  }
});

export default router;
