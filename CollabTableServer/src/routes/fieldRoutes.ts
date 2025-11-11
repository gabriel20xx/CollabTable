import { Router, Request, Response } from 'express';
import { dbAdapter } from '../db';

const router = Router();

// Get fields for a list
router.get('/list/:listId', async (req: Request, res: Response) => {
  try {
  const fields = await dbAdapter.queryAll('SELECT * FROM fields WHERE listId = ? AND isDeleted = 0 ORDER BY "order" ASC', [req.params.listId]);
    const formattedFields = (fields as any[]).map(field => ({ ...field, isDeleted: !!field.isDeleted }));
    res.json(formattedFields);
  } catch (error) {
    res.status(500).json({ error: 'Failed to fetch fields' });
  }
});

// Create field
router.post('/', async (req: Request, res: Response) => {
  try {
    const { id, name, fieldType, fieldOptions, alignment, listId, order, createdAt, updatedAt, isDeleted } = req.body;
    await dbAdapter.execute(
      'INSERT INTO fields (id, name, fieldType, fieldOptions, alignment, listId, "order", createdAt, updatedAt, isDeleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)',
      [id, name, fieldType, fieldOptions, alignment ?? 'start', listId, order, createdAt, updatedAt, isDeleted ? 1 : 0]
    );
    const field = await dbAdapter.queryOne('SELECT * FROM fields WHERE id = ?', [id]);
    res.status(201).json({ ...(field as any), isDeleted: !!(field as any).isDeleted });
  } catch (error) {
    res.status(500).json({ error: 'Failed to create field' });
  }
});

// Update field
router.put('/:id', async (req: Request, res: Response) => {
  try {
    const updatedAt = Date.now();
    const { name, fieldType, fieldOptions, alignment, order } = req.body;
    const result = await dbAdapter.execute(
      'UPDATE fields SET name = ?, fieldType = ?, fieldOptions = ?, alignment = ?, "order" = ?, updatedAt = ? WHERE id = ?',
      [name, fieldType, fieldOptions, alignment ?? 'start', order, updatedAt, req.params.id]
    );
    
    if (result.changes === 0) {
      return res.status(404).json({ error: 'Field not found' });
    }
    
  const field = await dbAdapter.queryOne('SELECT * FROM fields WHERE id = ?', [req.params.id]);
    res.json({ ...(field as any), isDeleted: !!(field as any).isDeleted });
  } catch (error) {
    res.status(500).json({ error: 'Failed to update field' });
  }
});

// Delete field (soft delete)
router.delete('/:id', async (req: Request, res: Response) => {
  try {
    const updatedAt = Date.now();
    // Soft delete the field first
    const field = await dbAdapter.queryOne('SELECT * FROM fields WHERE id = ?', [req.params.id]);
    if (!field) {
      return res.status(404).json({ error: 'Field not found' });
    }
    await dbAdapter.execute(
      'UPDATE fields SET isDeleted = 1, updatedAt = ? WHERE id = ?',
      [updatedAt, req.params.id]
    );

    // Remove any item_values referencing this field (already handled in sync route, but ensure consistency for direct REST usage)
    await dbAdapter.execute('DELETE FROM item_values WHERE fieldId = ?', [req.params.id]);

    // Determine if this was the last remaining (non-deleted) field in its list.
    const listId = field.listId;
    const remaining = await dbAdapter.queryOne(
      'SELECT COUNT(*) as cnt FROM fields WHERE listId = ? AND isDeleted = 0',
      [listId]
    );
    const remainingCount = remaining ? (remaining.cnt ?? remaining.CNT ?? remaining.count ?? 0) : 0;

    if (remainingCount === 0) {
      // Last field deleted -> soft delete all items and purge their values for this list
      try {
        await dbAdapter.execute('UPDATE items SET isDeleted = 1, updatedAt = ? WHERE listId = ?', [updatedAt, listId]);
        await dbAdapter.execute('DELETE FROM item_values WHERE itemId IN (SELECT id FROM items WHERE listId = ?)', [listId]);
      } catch (cascadeErr) {
        console.warn('[FIELD DELETE] Cascade item cleanup failed:', cascadeErr);
      }
    }

    res.json({ message: 'Field deleted successfully', cascadeItemsDeleted: remainingCount === 0 });
  } catch (error) {
    res.status(500).json({ error: 'Failed to delete field' });
  }
});

export default router;
