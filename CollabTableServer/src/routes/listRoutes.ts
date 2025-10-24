import { Router, Request, Response } from 'express';
import { db } from '../database';

const router = Router();

// Get all lists
router.get('/', async (req: Request, res: Response) => {
  try {
    const lists = db.prepare('SELECT * FROM lists WHERE isDeleted = 0 ORDER BY updatedAt DESC').all();
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
    const list = db.prepare('SELECT * FROM lists WHERE id = ? AND isDeleted = 0').get(req.params.id);
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
    db.prepare(`
      INSERT INTO lists (id, name, createdAt, updatedAt, isDeleted)
      VALUES (?, ?, ?, ?, ?)
    `).run(id, name, createdAt, updatedAt, isDeleted ? 1 : 0);
    
    const list = db.prepare('SELECT * FROM lists WHERE id = ?').get(id);
    res.status(201).json({ ...(list as any), isDeleted: !!(list as any).isDeleted });
  } catch (error) {
    res.status(500).json({ error: 'Failed to create list' });
  }
});

// Update list
router.put('/:id', async (req: Request, res: Response) => {
  try {
    const updatedAt = Date.now();
    const result = db.prepare(`
      UPDATE lists 
      SET name = ?, updatedAt = ? 
      WHERE id = ?
    `).run(req.body.name, updatedAt, req.params.id);
    
    if (result.changes === 0) {
      return res.status(404).json({ error: 'List not found' });
    }
    
    const list = db.prepare('SELECT * FROM lists WHERE id = ?').get(req.params.id);
    res.json({ ...(list as any), isDeleted: !!(list as any).isDeleted });
  } catch (error) {
    res.status(500).json({ error: 'Failed to update list' });
  }
});

// Delete list (soft delete)
router.delete('/:id', async (req: Request, res: Response) => {
  try {
    const updatedAt = Date.now();
    const result = db.prepare(`
      UPDATE lists 
      SET isDeleted = 1, updatedAt = ? 
      WHERE id = ?
    `).run(updatedAt, req.params.id);
    
    if (result.changes === 0) {
      return res.status(404).json({ error: 'List not found' });
    }
    
    res.json({ message: 'List deleted successfully' });
  } catch (error) {
    res.status(500).json({ error: 'Failed to delete list' });
  }
});

export default router;
