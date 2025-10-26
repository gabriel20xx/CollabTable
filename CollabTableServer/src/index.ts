import express from 'express';
import cors from 'cors';
import dotenv from 'dotenv';
import { initializeDatabase } from './db';
import { authenticatePassword } from './middleware/auth';
import listRoutes from './routes/listRoutes';
import fieldRoutes from './routes/fieldRoutes';
import itemRoutes from './routes/itemRoutes';
import syncRoutes from './routes/syncRoutes';
import webRoutes from './routes/webRoutes';

dotenv.config();

const app = express();
const PORT = process.env.PORT || 3000;

// Middleware
app.use(cors());
app.use(express.json());

// Health check (no auth required)
app.get('/health', (req, res) => {
  res.json({ status: 'ok', timestamp: Date.now() });
});

// Apply authentication middleware to all API routes
app.use('/api', authenticatePassword);

// Routes (protected by auth)
app.use('/api/lists', listRoutes);
app.use('/api/fields', fieldRoutes);
app.use('/api/items', itemRoutes);
app.use('/api', syncRoutes);

// Web UI (no auth required, must be last to not interfere with API routes)
app.use('/', webRoutes);

// Initialize database and start server
(async () => {
  try {
    await initializeDatabase();
    console.log('Database synchronized successfully');
    console.log('Database models loaded:');
    console.log('- Lists');
    console.log('- Fields');
    console.log('- Items');
    console.log('- ItemValues');

    // Start HTTP server (WebSocket removed)
    app.listen(PORT, () => {
      console.log(`Server is running on port ${PORT}`);
      console.log(`Environment: ${process.env.NODE_ENV || 'development'}`);
      const dbClient = (process.env.DB_CLIENT || process.env.DB_TYPE || 'sqlite').toLowerCase();
      if (dbClient === 'postgres' || dbClient === 'postgresql') {
        const source = process.env.DATABASE_URL ? 'DATABASE_URL' : 'PGHOST/PGDATABASE';
        console.log(`Database: PostgreSQL (${source})`);
      } else {
        console.log(`Database: ${process.env.DB_PATH || './data/collabtable.db'}`);
      }
    });
  } catch (error) {
    console.error('Database initialization error:', error);
    process.exit(1);
  }
})();

export default app;
