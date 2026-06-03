import { Request, Response, NextFunction } from 'express';

let warningLogged = false;
const AUTH_LOG_THROTTLE_MS = Math.max(1000, Number(process.env.AUTH_LOG_THROTTLE_MS || 30000));
const lastAuthLogAt = new Map<string, number>();
const commonProbePathPatterns = [
  /^\/\./,
  /client_secret\.json$/i,
  /phpinfo/i,
  /\.git/i,
  /wp-admin/i,
  /wp-login/i,
  /id_rsa/i,
  /config(\.|$)/i
];

function isCommonProbePath(pathname: string) {
  return commonProbePathPatterns.some(pattern => pattern.test(pathname));
}

function shouldLogAuthWarning(kind: string, req: Request) {
  const key = `${kind}:${req.method}:${req.path}`;
  const now = Date.now();
  const lastSeen = lastAuthLogAt.get(key) || 0;
  if (now - lastSeen < AUTH_LOG_THROTTLE_MS) {
    return false;
  }
  lastAuthLogAt.set(key, now);
  return true;
}

export const authenticatePassword = (req: Request, res: Response, next: NextFunction) => {
  const serverPassword = process.env.SERVER_PASSWORD;

  // If no password is set in environment, skip authentication
  if (!serverPassword) {
    if (!warningLogged) {
      console.warn('Warning: SERVER_PASSWORD not set in environment variables. Authentication is disabled.');
      warningLogged = true;
    }
    return next();
  }

  // Get password from Authorization header (format: "Bearer <password>")
  const authHeader = req.headers.authorization;
  
  if (!authHeader) {
    if (!isCommonProbePath(req.path) && shouldLogAuthWarning('missingHeader', req)) {
      console.warn('[AUTH] Missing Authorization header', {
        path: req.path,
        method: req.method,
        ip: req.ip,
      });
    }
    return res.status(401).json({ 
      error: 'Unauthorized', 
      message: 'No authorization header provided' 
    });
  }

  // Parse the authorization header
  const parts = authHeader.split(' ');
  if (parts.length !== 2 || parts[0] !== 'Bearer') {
    if (shouldLogAuthWarning('invalidHeaderFormat', req)) {
      console.warn('[AUTH] Invalid authorization header format', {
        path: req.path,
        method: req.method,
        ip: req.ip,
        // Do not log full header to avoid leaking secrets; include prefix only
        headerPreview: authHeader.substring(0, Math.min(authHeader.length, 20)) + '...'
      });
    }
    return res.status(401).json({ 
      error: 'Unauthorized', 
      message: 'Invalid authorization header format. Expected: Bearer <password>' 
    });
  }

  const providedPassword = parts[1];

  // Validate password
  if (providedPassword !== serverPassword) {
    // Avoid logging the full password; log only length and a small preview
    if (shouldLogAuthWarning('invalidPassword', req)) {
      const safePreview = providedPassword ? `${providedPassword.substring(0, 2)}..(${providedPassword.length})` : 'null';
      console.warn('[AUTH] Invalid password', {
        path: req.path,
        method: req.method,
        ip: req.ip,
        providedPreview: safePreview,
      });
    }
    return res.status(401).json({ 
      error: 'Unauthorized', 
      message: 'Invalid password' 
    });
  }

  // Password is valid, continue
  next();
};
