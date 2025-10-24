import { Router, Request, Response } from 'express';
import { db } from '../database';

const router = Router();

// Get fields for a list
router.get('/list/:listId', async (req: Request, res: Response) => {
  try {
    const fields = db.prepare('SELECT * FROM fields WHERE listId = ? AND isDeleted = 0 ORDER BY `order` ASC').all(req.params.listId);
    const formattedFields = (fields as any[]).map(field => ({ ...field, isDeleted: !!field.isDeleted }));
    res.json(formattedFields);
  } catch (error) {
    res.status(500).json({ error: 'Failed to fetch fields' });
  }
});

// Create field
router.post('/', async (req: Request, res: Response) => {
  try {
    const { id, name, fieldType, fieldOptions, listId, order, createdAt, updatedAt, isDeleted } = req.body;
    db.prepare(`
      INSERT INTO fields (id, name, fieldType, fieldOptions, listId, \`order\`, createdAt, updatedAt, isDeleted)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    `).run(id, name, fieldType, fieldOptions, listId, order, createdAt, updatedAt, isDeleted ? 1 : 0);
    
    const field = db.prepare('SELECT * FROM fields WHERE id = ?').get(id);
    res.status(201).json({ ...(field as any), isDeleted: !!(field as any).isDeleted });
  } catch (error) {
    res.status(500).json({ error: 'Failed to create field' });
  }
});

// Update field
router.put('/:id', async (req: Request, res: Response) => {
  try {
    const updatedAt = Date.now();
    const { name, fieldType, fieldOptions, order } = req.body;
    const result = db.prepare(`
      UPDATE fields 
      SET name = ?, fieldType = ?, fieldOptions = ?, \`order\` = ?, updatedAt = ?
      WHERE id = ?
    `).run(name, fieldType, fieldOptions, order, updatedAt, req.params.id);
    
    if (result.changes === 0) {
      return res.status(404).json({ error: 'Field not found' });
    }
    
    const field = db.prepare('SELECT * FROM fields WHERE id = ?').get(req.params.id);
    res.json({ ...(field as any), isDeleted: !!(field as any).isDeleted });
  } catch (error) {
    res.status(500).json({ error: 'Failed to update field' });
  }
});

// Delete field (soft delete)
router.delete('/:id', async (req: Request, res: Response) => {
  try {
    const updatedAt = Date.now();
    const result = db.prepare(`
      UPDATE fields 
      SET isDeleted = 1, updatedAt = ?
      WHERE id = ?
    `).run(updatedAt, req.params.id);
    
    if (result.changes === 0) {
      return res.status(404).json({ error: 'Field not found' });
    }
    
    res.json({ message: 'Field deleted successfully' });
  } catch (error) {
    res.status(500).json({ error: 'Failed to delete field' });
  }
});

export default router;
