import { Request, Response, NextFunction } from 'express';

/**
 * Extracts device identifier from headers/body and attaches it to req as `deviceId`.
 * - Preferred: `x-device-id` header
 * - Fallback: `req.body.deviceId` or `req.query.deviceId`
 */
export function deviceIdMiddleware(req: Request, _res: Response, next: NextFunction) {
  const headerId = (req.headers['x-device-id'] || req.headers['X-Device-Id']) as string | undefined;
  const bodyId = (req.body && typeof req.body === 'object') ? (req.body.deviceId as string | undefined) : undefined;
  const queryId = (req.query?.deviceId as string | undefined);
  const id = (headerId || bodyId || queryId || '').toString().trim();
  (req as any).deviceId = id.length > 0 ? id : undefined;
  next();
}
