const path = require('path');

// Load .env from service directory first, then fallback to backend root .env
require('dotenv').config({ path: path.resolve(__dirname, '.env') });
require('dotenv').config({ path: path.resolve(__dirname, '../../.env') });

const express = require('express');
const cors = require('cors');
const axios = require('axios');
const pool = require('./db');
const authMiddleware = require('./middleware/auth');

const app = express();
const PORT = process.env.PORT || 3002;
const GEO_SERVICE_URL = (process.env.GEO_SERVICE_URL || 'http://localhost:3003').replace(/\/$/, '');

// Enable CORS for all origins
app.use(cors());

// Middleware to parse incoming request bodies
app.use(express.json({
  type: ['application/json', 'application/*+json', 'text/plain']
}));
app.use(express.urlencoded({ extended: true }));

// Handle JSON syntax errors gracefully
app.use((err, req, res, next) => {
  if (err instanceof SyntaxError && err.status === 400 && 'body' in err) {
    return res.status(400).json({
      message: 'Malformed JSON payload. Please verify your request body syntax.',
    });
  }
  next(err);
});

// -------------------------------------------------------------
// Health Check Route (Public)
// -------------------------------------------------------------
app.get('/health', (req, res) => {
  return res.status(200).json({ status: 'ok' });
});

// -------------------------------------------------------------
// Create SOS Incident Route (Protected)
// -------------------------------------------------------------
app.post('/sos', authMiddleware, async (req, res) => {
  try {
    if (!req.body || typeof req.body !== 'object') {
      return res.status(400).json({
        message: 'Request body is missing. Please provide a valid JSON body.',
      });
    }

    const { latitude, longitude, sos_type } = req.body;

    // Validate coordinates
    if (latitude === undefined || longitude === undefined || latitude === null || longitude === null) {
      return res.status(400).json({
        message: 'Both latitude and longitude are required to trigger an SOS.',
      });
    }

    const lat = parseFloat(latitude);
    const lng = parseFloat(longitude);

    if (isNaN(lat) || isNaN(lng)) {
      return res.status(400).json({
        message: 'Latitude and longitude must be valid numbers.',
      });
    }

    const type = (sos_type && String(sos_type).trim()) ? String(sos_type).trim() : 'general';
    const userId = req.user.userId;

    // Insert new incident into database
    const insertQuery = `
      INSERT INTO incidents (user_id, latitude, longitude, sos_type, status)
      VALUES ($1, $2, $3, $4, 'pending')
      RETURNING id, user_id, latitude, longitude, sos_type, status, severity, created_at, updated_at;
    `;
    const values = [userId, lat, lng, type];
    const result = await pool.query(insertQuery, values);
    const newIncident = result.rows[0];

    // Log incident creation
    console.log(`New SOS incident created: ID ${newIncident.id} by user ${userId}`);

    // -------------------------------------------------------------
    // Internal Service Call: Query nearby users from Geo Service
    // -------------------------------------------------------------
    try {
      const authHeader = req.headers['authorization'] || req.headers.authorization;

      const geoResponse = await axios.get(`${GEO_SERVICE_URL}/nearby`, {
        params: {
          latitude: lat,
          longitude: lng,
          radius: 500,
        },
        headers: {
          Authorization: authHeader,
        },
        timeout: 5000, // 5 seconds timeout
      });

      const nearbyUserIds = geoResponse.data?.nearbyUserIds || [];
      console.log(`Nearby users found: ${JSON.stringify(nearbyUserIds)}`);
    } catch (geoError) {
      // Gracefully handle failure so SOS creation is never aborted
      console.error('[Geo Service Call Error]:', geoError.response?.data?.message || geoError.message);
    }

    return res.status(201).json({
      message: 'SOS incident created successfully.',
      id: newIncident.id,
      status: newIncident.status,
      created_at: newIncident.created_at,
      incident: {
        id: newIncident.id,
        status: newIncident.status,
        created_at: newIncident.created_at,
        latitude: newIncident.latitude,
        longitude: newIncident.longitude,
        sos_type: newIncident.sos_type,
        severity: newIncident.severity,
      },
    });
  } catch (error) {
    console.error('[Error /sos]:', error.message, error.stack);
    return res.status(500).json({
      message: 'Internal server error while creating SOS incident.',
      error: error.message,
    });
  }
});

// -------------------------------------------------------------
// Get Specific SOS Incident Route (Protected)
// -------------------------------------------------------------
app.get('/sos/:id', authMiddleware, async (req, res) => {
  try {
    const incidentId = parseInt(req.params.id, 10);

    if (isNaN(incidentId)) {
      return res.status(400).json({
        message: 'Incident ID must be a valid integer.',
      });
    }

    const query = 'SELECT * FROM incidents WHERE id = $1';
    const result = await pool.query(query, [incidentId]);

    if (result.rows.length === 0) {
      return res.status(404).json({
        message: 'Incident not found.',
      });
    }

    const incident = result.rows[0];

    // Only allow the owner of the incident to access it
    if (incident.user_id !== req.user.userId) {
      return res.status(403).json({
        message: 'Access denied. You are not authorized to view this incident.',
      });
    }

    return res.status(200).json({
      message: 'Incident retrieved successfully.',
      incident,
    });
  } catch (error) {
    console.error(`[Error /sos/${req.params.id}]:`, error.message, error.stack);
    return res.status(500).json({
      message: 'Internal server error while retrieving incident.',
      error: error.message,
    });
  }
});

// Catch-all 404 handler for undefined routes
app.use((req, res) => {
  res.status(404).json({ message: 'Route not found.' });
});

// Global error handling middleware
app.use((err, req, res, next) => {
  console.error('[Unhandled Server Error]:', err.message, err.stack);
  res.status(500).json({ message: 'Internal server error.' });
});

// Start Express server
const server = app.listen(PORT, () => {
  console.log(`[SOS Service] Running on http://localhost:${PORT}`);
});

// Graceful shutdown handling
const handleShutdown = async (signal) => {
  console.log(`\n[SOS Service] Received ${signal}. Closing HTTP server & Database pool...`);
  server.close(async () => {
    try {
      await pool.end();
      console.log('[SOS Service] Database pool closed. Process exiting cleanly.');
      process.exit(0);
    } catch (err) {
      console.error('[SOS Service] Error during pool disconnect:', err.message);
      process.exit(1);
    }
  });
};

process.on('SIGTERM', () => handleShutdown('SIGTERM'));
process.on('SIGINT', () => handleShutdown('SIGINT'));
