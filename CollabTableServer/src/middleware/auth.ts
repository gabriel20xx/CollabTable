import { Request, Response, NextFunction } from 'express';

let warningLogged = false;

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
    return res.status(401).json({ 
      error: 'Unauthorized', 
      message: 'No authorization header provided' 
    });
  }

  // Parse the authorization header
  const parts = authHeader.split(' ');
  if (parts.length !== 2 || parts[0] !== 'Bearer') {
    return res.status(401).json({ 
      error: 'Unauthorized', 
      message: 'Invalid authorization header format. Expected: Bearer <password>' 
    });
  }

  const providedPassword = parts[1];

  // Validate password
  if (providedPassword !== serverPassword) {
    return res.status(401).json({ 
      error: 'Unauthorized', 
      message: 'Invalid password' 
    });
  }

  // Password is valid, continue
  next();
};
