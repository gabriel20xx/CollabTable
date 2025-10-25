import { Router, Request, Response } from 'express';
import { dbAdapter } from '../db';

const router = Router();

// Get all lists
router.get('/', async (req: Request, res: Response) => {
  try {
  const lists = await dbAdapter.queryAll('SELECT * FROM lists WHERE isDeleted = 0 ORDER BY updatedAt DESC');
    // Convert isDeleted from INTEGER to BOOLEAN
    const formattedLists = (lists as any[]).map(list => ({ ...list, isDeleted: !!list.isDeleted }));
    res.json(formattedLists);
  } catch (error) {
    res.status(500).json({ error: 'Failed to fetch lists' });
  }
});

// Get single list
router.get('/:id', async (req: Request, res: Response) => {
  try {
  const list = await dbAdapter.queryOne('SELECT * FROM lists WHERE id = ? AND isDeleted = 0', [req.params.id]);
    if (!list) {
      return res.status(404).json({ error: 'List not found' });
    }
    res.json({ ...(list as any), isDeleted: !!(list as any).isDeleted });
  } catch (error) {
    res.status(500).json({ error: 'Failed to fetch list' });
  }
});

// Create list
router.post('/', async (req: Request, res: Response) => {
  try {
    const { id, name, createdAt, updatedAt, isDeleted } = req.body;
    await dbAdapter.execute(
      'INSERT INTO lists (id, name, createdAt, updatedAt, isDeleted) VALUES (?, ?, ?, ?, ?)',
      [id, name, createdAt, updatedAt, isDeleted ? 1 : 0]
    );
    const list = await dbAdapter.queryOne('SELECT * FROM lists WHERE id = ?', [id]);
    res.status(201).json({ ...(list as any), isDeleted: !!(list as any).isDeleted });
  } catch (error) {
    res.status(500).json({ error: 'Failed to create list' });
  }
});

// Update list
router.put('/:id', async (req: Request, res: Response) => {
  try {
    const updatedAt = Date.now();
    const result = await dbAdapter.execute(
      'UPDATE lists SET name = ?, updatedAt = ? WHERE id = ?',
      [req.body.name, updatedAt, req.params.id]
    );

    if (result.changes === 0) {
      return res.status(404).json({ error: 'List not found' });
    }
    const list = await dbAdapter.queryOne('SELECT * FROM lists WHERE id = ?', [req.params.id]);
    res.json({ ...(list as any), isDeleted: !!(list as any).isDeleted });
  } catch (error) {
    res.status(500).json({ error: 'Failed to update list' });
  }
});

// Delete list (soft delete)
router.delete('/:id', async (req: Request, res: Response) => {
  try {
    const updatedAt = Date.now();
    const result = await dbAdapter.execute(
      'UPDATE lists SET isDeleted = 1, updatedAt = ? WHERE id = ?',
      [updatedAt, req.params.id]
    );

    if (result.changes === 0) {
      return res.status(404).json({ error: 'List not found' });
    }
    
    res.json({ message: 'List deleted successfully' });
  } catch (error) {
    res.status(500).json({ error: 'Failed to delete list' });
  }
});

export default router;
