import { Router, Request, Response } from 'express';
import { dbAdapter } from '../db';

const router = Router();

// GET /api/notifications/poll?since=timestampMs
router.get('/notifications/poll', async (req: Request, res: Response) => {
  try {
    const sinceRaw = (req.query.since as string) || '0';
    const since = Number(sinceRaw);
    const deviceId = (req as any).deviceId as string | undefined;

    const params: any[] = [isFinite(since) && since > 0 ? since : 0];
    let sql = 'SELECT * FROM notifications WHERE createdAt > ?';

    if (deviceId && deviceId.length > 0) {
      sql += ' AND (deviceIdOrigin IS NULL OR deviceIdOrigin <> ?)';
      params.push(deviceId);
    }

    sql += ' ORDER BY createdAt ASC LIMIT 500';

    const rows = await dbAdapter.queryAll(sql, params);

    // Normalize keys in case of differing DB casing
    const pick = (obj: any, a: string, b: string) => obj[a] ?? obj[b];
    const events = (rows as any[]).map(r => ({
      id: r.id,
      deviceIdOrigin: pick(r, 'deviceIdOrigin', 'deviceidorigin') || undefined,
      eventType: pick(r, 'eventType', 'eventtype'),
      entityType: pick(r, 'entityType', 'entitytype'),
      entityId: pick(r, 'entityId', 'entityid') || undefined,
      listId: pick(r, 'listId', 'listid') || undefined,
      createdAt: pick(r, 'createdAt', 'createdat')
    }));

    res.json({ notifications: events, serverTimestamp: Date.now() });
  } catch (e) {
    console.error('[NOTIF] Poll failed:', e);
    res.status(500).json({ error: 'Failed to poll notifications' });
  }
});

export default router;
