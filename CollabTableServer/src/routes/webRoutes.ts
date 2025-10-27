import { Router, Request, Response } from 'express';
import { dbAdapter } from '../db';

const router = Router();

// Serve the web UI
router.get('/', (req: Request, res: Response) => {
  res.send(`
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>CollabTable Server - Database Viewer</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
            background: #f5f5f5;
            padding: 20px;
        }
        .container {
            max-width: 1400px;
            margin: 0 auto;
        }
        h1 {
            color: #333;
            margin-bottom: 10px;
        }
        .header-right {
            display: flex;
            align-items: center;
            gap: 12px;
        }
        .refresh-info {
            font-size: 12px;
            color: #666;
            padding: 4px 8px;
            background: #f1f3f5;
            border-radius: 6px;
        }
        .stats {
            background: white;
            padding: 20px;
            border-radius: 8px;
            margin-bottom: 20px;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        }
        .stat-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 15px;
            margin-top: 15px;
        }
        .stat-card {
            background: #f8f9fa;
            padding: 15px;
            border-radius: 6px;
            border-left: 4px solid #007bff;
        }
        .stat-label {
            color: #666;
            font-size: 14px;
            margin-bottom: 5px;
        }
        .stat-value {
            color: #333;
            font-size: 24px;
            font-weight: bold;
        }
        .lists-container {
            display: grid;
            gap: 20px;
        }
        .list-card {
            background: white;
            border-radius: 8px;
            padding: 20px;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        }
        .list-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 15px;
            padding-bottom: 15px;
            border-bottom: 2px solid #e9ecef;
        }
        .list-name {
            font-size: 20px;
            font-weight: bold;
            color: #333;
        }
        .list-id {
            font-size: 12px;
            color: #999;
            font-family: monospace;
        }
        .fields-container {
            margin-bottom: 15px;
        }
        .field-tag {
            display: inline-block;
            background: #e7f3ff;
            color: #0066cc;
            padding: 6px 12px;
            border-radius: 4px;
            margin-right: 8px;
            margin-bottom: 8px;
            font-size: 14px;
        }
        .items-table {
            width: 100%;
            border-collapse: collapse;
            margin-top: 10px;
        }
        .items-table th {
            background: #f8f9fa;
            padding: 12px;
            text-align: left;
            font-weight: 600;
            color: #495057;
            border-bottom: 2px solid #dee2e6;
        }
        .items-table td {
            padding: 12px;
            border-bottom: 1px solid #dee2e6;
        }
        .no-data {
            text-align: center;
            color: #999;
            padding: 40px;
            font-style: italic;
        }
        .refresh-btn {
            background: #007bff;
            color: white;
            border: none;
            padding: 10px 20px;
            border-radius: 6px;
            cursor: pointer;
            font-size: 14px;
            margin-left: 10px;
        }
        .refresh-btn:hover {
            background: #0056b3;
        }
        .deleted {
            opacity: 0.5;
            background: #fff3cd !important;
        }
        .badge {
            display: inline-block;
            background: #dc3545;
            color: white;
            padding: 2px 8px;
            border-radius: 3px;
            font-size: 11px;
            margin-left: 8px;
        }
    </style>
</head>
<body>
    <div class="container">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
            <h1>CollabTable Server - Database Viewer</h1>
            <div class="header-right">
                <div id="refresh-info" class="refresh-info">Last refresh: -</div>
                <button class="refresh-btn" onclick="loadData()">Refresh</button>
            </div>
        </div>
        
        <div class="stats">
            <h2 style="margin-bottom: 10px;">Database Statistics</h2>
            <div class="stat-grid">
                <div class="stat-card">
                    <div class="stat-label">Total Lists</div>
                    <div class="stat-value" id="total-lists">-</div>
                </div>
                <div class="stat-card">
                    <div class="stat-label">Total Fields</div>
                    <div class="stat-value" id="total-fields">-</div>
                </div>
                <div class="stat-card">
                    <div class="stat-label">Total Items</div>
                    <div class="stat-value" id="total-items">-</div>
                </div>
                <div class="stat-card">
                    <div class="stat-label">Total Values</div>
                    <div class="stat-value" id="total-values">-</div>
                </div>
            </div>
        </div>
        
        <div id="lists-container" class="lists-container">
            <div class="no-data">Loading...</div>
        </div>
    </div>

    <script>
        function fmtDate(v) {
            if (v === null || v === undefined) return '-';
            const n = Number(v);
            if (!Number.isNaN(n)) {
                const ms = n < 1e12 ? n * 1000 : n; // seconds -> ms if needed
                const d = new Date(ms);
                return isNaN(d.getTime()) ? '-' : d.toLocaleString();
            }
            const t = Date.parse(v);
            if (!Number.isNaN(t)) {
                const d = new Date(t);
                return isNaN(d.getTime()) ? '-' : d.toLocaleString();
            }
            return '-';
        }
        async function loadData() {
            try {
                // Add cache-busting timestamp to URL
                const response = await fetch('/web/data?_=' + Date.now(), {
                    cache: 'no-store'
                });
                if (!response.ok) {
                    throw new Error('HTTP ' + response.status + ': ' + response.statusText);
                }
                const data = await response.json();
                
                // Validate response structure
                if (!data || typeof data !== 'object') {
                    throw new Error('Invalid response format');
                }
                
                // Provide defaults for missing properties
                const lists = data.lists || [];
                const fields = data.fields || [];
                const items = data.items || [];
                const values = data.values || [];
                const stats = data.stats || { lists: 0, fields: 0, items: 0, values: 0 };
                
                // Update stats
                document.getElementById('total-lists').textContent = stats.lists;
                document.getElementById('total-fields').textContent = stats.fields;
                document.getElementById('total-items').textContent = stats.items;
                document.getElementById('total-values').textContent = stats.values;
                
                // Update refresh info
                const refreshInfo = document.getElementById('refresh-info');
                if (refreshInfo) {
                    const ts = (data && data.timestamp) ? data.timestamp : Date.now();
                    refreshInfo.textContent = 'Last refresh: ' + fmtDate(ts);
                }

                // Render lists
                const container = document.getElementById('lists-container');
                
                if (lists.length === 0) {
                    container.innerHTML = '<div class="no-data">No lists found in database</div>';
                    return;
                }
                
                container.innerHTML = lists.map(list => {
                    const listFields = fields.filter(f => f.listId === list.id);
                    const listItems = items.filter(i => i.listId === list.id);
                    
                    return \`
                        <div class="list-card \${list.isDeleted ? 'deleted' : ''}">
                            <div class="list-header">
                                <div>
                                    <div class="list-name">
                                        \${list.name}
                                        \${list.isDeleted ? '<span class="badge">DELETED</span>' : ''}
                                    </div>
                                    <div class="list-id">ID: \${list.id}</div>
                                    <div class="list-id">Created: \${fmtDate(list.createdAt)}</div>
                                    <div class="list-id">Updated: \${fmtDate(list.effectiveUpdatedAt ?? list.updatedAt)}</div>
                                </div>
                            </div>
                            
                            <div class="fields-container">
                                <strong>Fields (\${listFields.length}):</strong><br>
                                \${listFields.length > 0 
                                    ? listFields.map(f => \`<span class="field-tag \${f.isDeleted ? 'deleted' : ''}">\${f.name} (\${f.fieldType})</span>\`).join('')
                                    : '<span style="color: #999;">No fields</span>'
                                }
                            </div>
                            
                            \${listItems.length > 0 ? \`
                                <table class="items-table">
                                    <thead>
                                        <tr>
                                            <th>Item ID</th>
                                            \${listFields.map(f => \`<th>\${f.name}</th>\`).join('')}
                                            <th>Updated</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        \${listItems.map(item => {
                                            const itemValues = values.filter(v => v.itemId === item.id);
                                            return \`
                                                <tr class="\${item.isDeleted ? 'deleted' : ''}">
                                                    <td>\${item.id.substring(0, 8)}...</td>
                                                    \${listFields.map(field => {
                                                        const value = itemValues.find(v => v.fieldId === field.id);
                                                        return \`<td>\${value ? value.value : '-'}</td>\`;
                                                    }).join('')}
                                                    <td>\${fmtDate(item.updatedAt)}</td>
                                                </tr>
                                            \`;
                                        }).join('')}
                                    </tbody>
                                </table>
                            \` : '<div style="color: #999; margin-top: 10px;">No items</div>'}
                        </div>
                    \`;
                }).join('');
                
            } catch (error) {
                console.error('Error loading data:', error);
                document.getElementById('lists-container').innerHTML = 
                    '<div class="no-data" style="color: #dc3545;">Error loading data: ' + (error.message || 'Unknown error') + '</div>';
            }
        }
        
        // Load data on page load
        loadData();
        
        // Auto-refresh every 5 seconds
        setInterval(loadData, 5000);
    </script>
</body>
</html>
  `);
});

// API endpoint to get all data (no auth required for web UI)
router.get('/web/data', async (req: Request, res: Response) => {
  try {
    // Add cache control headers to prevent stale data
    res.setHeader('Cache-Control', 'no-cache, no-store, must-revalidate');
    res.setHeader('Pragma', 'no-cache');
    res.setHeader('Expires', '0');
    
    // Exclude deleted lists and items from web UI
    const lists = await dbAdapter.queryAll('SELECT * FROM lists WHERE isDeleted = 0 ORDER BY updatedAt DESC');
    const fields = await dbAdapter.queryAll('SELECT * FROM fields ORDER BY listId ASC, "order" ASC');
    const items = await dbAdapter.queryAll('SELECT * FROM items WHERE isDeleted = 0 ORDER BY listId ASC, updatedAt DESC');
    const values = await dbAdapter.queryAll('SELECT * FROM item_values');
    
    // Convert isDeleted from INTEGER to BOOLEAN
        // Normalize timestamps to milliseconds since epoch
        const toMillis = (v: any): number | null => {
            if (v === null || v === undefined) return null;
            if (typeof v === 'number') {
                return v < 1e12 ? Math.trunc(v * 1000) : Math.trunc(v);
            }
            if (typeof v === 'string') {
                if (/^\d+$/.test(v)) {
                    const n = Number(v);
                    return n < 1e12 ? Math.trunc(n * 1000) : Math.trunc(n);
                }
                const t = Date.parse(v);
                return Number.isNaN(t) ? null : Math.trunc(t);
            }
            return null;
        };
        // Normalize key casing across SQLite (camelCase) and Postgres (lowercase)
        const pick = (obj: any, a: string, b: string) => obj[a] ?? obj[b];
        let formattedLists = (lists as any[]).map(list => ({
            ...list,
            isDeleted: !!pick(list, 'isDeleted', 'isdeleted'),
            createdAt: toMillis(pick(list, 'createdAt', 'createdat')),
            updatedAt: toMillis(pick(list, 'updatedAt', 'updatedat'))
        }));
        const formattedFields = (fields as any[]).map(field => ({
            ...field,
            listId: pick(field, 'listId', 'listid'),
            isDeleted: !!pick(field, 'isDeleted', 'isdeleted'),
            createdAt: toMillis(pick(field, 'createdAt', 'createdat')),
            updatedAt: toMillis(pick(field, 'updatedAt', 'updatedat'))
        }));
        const formattedItems = (items as any[]).map(item => ({
            ...item,
            listId: pick(item, 'listId', 'listid'),
            isDeleted: !!pick(item, 'isDeleted', 'isdeleted'),
            createdAt: toMillis(pick(item, 'createdAt', 'createdat')),
            updatedAt: toMillis(pick(item, 'updatedAt', 'updatedat'))
        }));
        const formattedValues = (values as any[]).map(v => ({
            ...v,
            itemId: pick(v, 'itemId', 'itemid'),
            fieldId: pick(v, 'fieldId', 'fieldid'),
            updatedAt: toMillis(pick(v, 'updatedAt', 'updatedat'))
        }));
        // Compute effective updatedAt for lists based on latest change in the list itself or its children
        const itemIdToListId = new Map<string, string>();
        for (const it of formattedItems as any[]) {
            if (it && it.id && it.listId) itemIdToListId.set(it.id, it.listId);
        }
        const listToMaxField = new Map<string, number>();
        for (const f of formattedFields as any[]) {
            if (!f || !f.listId) continue;
            const t = typeof f.updatedAt === 'number' ? f.updatedAt : 0;
            const prev = listToMaxField.get(f.listId) || 0;
            if (t > prev) listToMaxField.set(f.listId, t);
        }
        const listToMaxItem = new Map<string, number>();
        for (const it of formattedItems as any[]) {
            if (!it || !it.listId) continue;
            const t = typeof it.updatedAt === 'number' ? it.updatedAt : 0;
            const prev = listToMaxItem.get(it.listId) || 0;
            if (t > prev) listToMaxItem.set(it.listId, t);
        }
        const listToMaxValue = new Map<string, number>();
        for (const val of formattedValues as any[]) {
            if (!val || !val.itemId) continue;
            const lId = itemIdToListId.get(val.itemId);
            if (!lId) continue;
            const t = typeof val.updatedAt === 'number' ? val.updatedAt : 0;
            const prev = listToMaxValue.get(lId) || 0;
            if (t > prev) listToMaxValue.set(lId, t);
        }
        formattedLists = formattedLists.map((l: any) => {
            const fMax = listToMaxField.get(l.id) || 0;
            const iMax = listToMaxItem.get(l.id) || 0;
            const vMax = listToMaxValue.get(l.id) || 0;
            const base = typeof l.updatedAt === 'number' ? l.updatedAt : 0;
            const eff = Math.max(base, fMax, iMax, vMax);
            return { ...l, effectiveUpdatedAt: eff === 0 ? l.updatedAt : eff };
        });
    
    res.json({
      lists: formattedLists,
      fields: formattedFields,
      items: formattedItems,
            values: formattedValues,
      stats: {
        lists: formattedLists.length,
        fields: formattedFields.length,
                items: formattedItems.length,
                values: formattedValues.length
      },
      timestamp: Date.now() // Add timestamp for debugging
    });
  } catch (error) {
    console.error('Error fetching data:', error);
    res.status(500).json({ error: 'Failed to fetch data' });
  }
});

export default router;
