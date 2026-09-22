const path = require('path');

// Load .env from service directory first, then fallback to backend root .env
require('dotenv').config({ path: path.resolve(__dirname, '.env') });
require('dotenv').config({ path: path.resolve(__dirname, '../../.env') });

const express = require('express');
const cors = require('cors');
const pool = require('./db');
const authMiddleware = require('./middleware/auth');

const app = express();
const PORT = process.env.PORT || 3003;

// Enable Cross-Origin Resource Sharing
app.use(cors());

// Middleware to parse incoming request bodies
app.use(express.json({
  type: ['application/json', 'application/*+json', 'text/plain']
}));
app.use(express.urlencoded({ extended: true }));

// Handle JSON formatting errors gracefully
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
// Update User Location Route (Protected)
// UPSERT into user_locations using PostGIS geography point
// -------------------------------------------------------------
app.post('/location/update', authMiddleware, async (req, res) => {
  try {
    if (!req.body || typeof req.body !== 'object') {
      return res.status(400).json({
        message: 'Request body is missing. Please provide a valid JSON body.',
      });
    }

    const { latitude, longitude } = req.body;

    if (latitude === undefined || longitude === undefined || latitude === null || longitude === null) {
      return res.status(400).json({
        message: 'Both latitude and longitude are required.',
      });
    }

    const lat = parseFloat(latitude);
    const lng = parseFloat(longitude);

    if (isNaN(lat) || isNaN(lng)) {
      return res.status(400).json({
        message: 'Latitude and longitude must be valid numbers.',
      });
    }

    // Validate geographic coordinate boundaries
    if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
      return res.status(400).json({
        message: 'Invalid coordinate range. Latitude must be between -90 and 90, Longitude between -180 and 180.',
      });
    }

    const userId = req.user.userId;

    // UPSERT: INSERT ... ON CONFLICT (user_id) DO UPDATE
    // Note: ST_MakePoint takes (longitude, latitude)
    const upsertQuery = `
      INSERT INTO user_locations (user_id, location, updated_at)
      VALUES ($1, ST_SetSRID(ST_MakePoint($2, $3), 4326)::geography, NOW())
      ON CONFLICT (user_id) DO UPDATE SET
        location = EXCLUDED.location,
        updated_at = NOW()
      RETURNING id, user_id, updated_at;
    `;

    const result = await pool.query(upsertQuery, [userId, lng, lat]);
    const updatedLocation = result.rows[0];

    console.log(`[Geo Service] Location updated for user ${userId}: (${lat}, ${lng})`);

    return res.status(200).json({
      message: 'Location updated',
      userId: updatedLocation.user_id,
      updated_at: updatedLocation.updated_at,
    });
  } catch (error) {
    console.error('[Error /location/update]:', error.message, error.stack);
    return res.status(500).json({
      message: 'Internal server error while updating location.',
      error: error.message,
    });
  }
});

// -------------------------------------------------------------
// Get Nearby Users Route (Protected)
// Finds users within radius meters using PostGIS ST_DWithin
// -------------------------------------------------------------
app.get('/nearby', authMiddleware, async (req, res) => {
  try {
    const { latitude, longitude, radius } = req.query;

    if (latitude === undefined || longitude === undefined) {
      return res.status(400).json({
        message: 'Query parameters "latitude" and "longitude" are required.',
      });
    }

    const lat = parseFloat(latitude);
    const lng = parseFloat(longitude);
    const rad = radius !== undefined ? parseFloat(radius) : 500; // default 500 meters

    if (isNaN(lat) || isNaN(lng) || isNaN(rad)) {
      return res.status(400).json({
        message: 'latitude, longitude, and radius must be valid numbers.',
      });
    }

    if (rad <= 0) {
      return res.status(400).json({
        message: 'Radius must be a positive number greater than 0 meters.',
      });
    }

    const currentUserId = req.user.userId;

    // PostGIS ST_DWithin query (excluding current requester user_id)
    const nearbyQuery = `
      SELECT user_id
      FROM user_locations
      WHERE ST_DWithin(location, ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography, $3)
        AND user_id != $4;
    `;

    const result = await pool.query(nearbyQuery, [lng, lat, rad, currentUserId]);
    const nearbyUserIds = result.rows.map(row => row.user_id);

    return res.status(200).json({
      nearbyUserIds,
      count: nearbyUserIds.length,
      radius: rad,
    });
  } catch (error) {
    console.error('[Error /nearby]:', error.message, error.stack);
    return res.status(500).json({
      message: 'Internal server error while searching nearby users.',
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
  console.log(`[Geo Service] Running on http://localhost:${PORT}`);
});

// Graceful shutdown handling
const handleShutdown = async (signal) => {
  console.log(`\n[Geo Service] Received ${signal}. Closing HTTP server & Database pool...`);
  server.close(async () => {
    try {
      await pool.end();
      console.log('[Geo Service] Database pool closed. Process exiting cleanly.');
      process.exit(0);
    } catch (err) {
      console.error('[Geo Service] Error during pool disconnect:', err.message);
      process.exit(1);
    }
  });
};

process.on('SIGTERM', () => handleShutdown('SIGTERM'));
process.on('SIGINT', () => handleShutdown('SIGINT'));
