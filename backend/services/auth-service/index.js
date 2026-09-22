const path = require('path');

// Load .env from current directory first, then fallback to backend parent directory
require('dotenv').config({ path: path.resolve(__dirname, '.env') });
require('dotenv').config({ path: path.resolve(__dirname, '../../.env') });

const express = require('express');
const bcrypt = require('bcrypt');
const jwt = require('jsonwebtoken');
const pool = require('./db');

const app = express();
const PORT = process.env.PORT || 3001;

// Middleware to parse incoming request bodies
// Accepts application/json, application/*+json, and text/plain (in case client headers vary)
app.use(express.json({
  type: ['application/json', 'application/*+json', 'text/plain']
}));
app.use(express.urlencoded({ extended: true }));

// Handle JSON syntax/formatting errors gracefully
app.use((err, req, res, next) => {
  if (err instanceof SyntaxError && err.status === 400 && 'body' in err) {
    console.error('[Error] Malformed JSON received:', err.message);
    return res.status(400).json({
      message: 'Malformed JSON payload. Please verify your request body syntax.',
    });
  }
  next(err);
});

// -------------------------------------------------------------
// Health Check Route
// -------------------------------------------------------------
app.get('/health', (req, res) => {
  return res.status(200).json({ status: 'ok' });
});

// -------------------------------------------------------------
// User Registration Route
// -------------------------------------------------------------
app.post('/register', async (req, res) => {
  try {
    // Guard against undefined or missing request body
    if (!req.body || typeof req.body !== 'object') {
      return res.status(400).json({
        message: 'Request body is missing. Please provide a valid JSON body with Content-Type: application/json.',
      });
    }

    const { name, email, phone, password } = req.body;

    // Validate required fields
    if (!name || !email || !password) {
      return res.status(400).json({
        message: 'Name, email, and password are required fields.',
      });
    }

    const trimmedEmail = String(email).trim().toLowerCase();

    // Check if user already exists
    const existingUser = await pool.query(
      'SELECT id FROM users WHERE LOWER(email) = $1',
      [trimmedEmail]
    );

    if (existingUser.rows.length > 0) {
      return res.status(409).json({
        message: 'A user with this email already exists.',
      });
    }

    // Hash the password with bcrypt
    const saltRounds = 10;
    const passwordHash = await bcrypt.hash(String(password), saltRounds);

    // Insert new user into database
    const insertQuery = `
      INSERT INTO users (name, email, phone, password_hash)
      VALUES ($1, $2, $3, $4)
      RETURNING id, name, email, phone, created_at;
    `;
    const values = [
      String(name).trim(),
      trimmedEmail,
      phone ? String(phone).trim() : null,
      passwordHash
    ];
    const result = await pool.query(insertQuery, values);

    const newUser = result.rows[0];

    return res.status(201).json({
      message: 'User registered successfully.',
      user: newUser,
    });
  } catch (error) {
    console.error('[Error /register]:', error.message, error.stack);
    return res.status(500).json({
      message: 'Internal server error during registration.',
      error: error.message,
    });
  }
});

// -------------------------------------------------------------
// User Login Route
// -------------------------------------------------------------
app.post('/login', async (req, res) => {
  try {
    // Guard against undefined or missing request body
    if (!req.body || typeof req.body !== 'object') {
      return res.status(400).json({
        message: 'Request body is missing. Please provide a valid JSON body with Content-Type: application/json.',
      });
    }

    const { email, password } = req.body;

    // Validate input presence
    if (!email || !password) {
      return res.status(400).json({
        message: 'Email and password are required.',
      });
    }

    const trimmedEmail = String(email).trim().toLowerCase();

    // Find user by email
    const userResult = await pool.query(
      'SELECT id, name, email, phone, password_hash, created_at FROM users WHERE LOWER(email) = $1',
      [trimmedEmail]
    );

    if (userResult.rows.length === 0) {
      return res.status(401).json({
        message: 'Invalid email or password.',
      });
    }

    const user = userResult.rows[0];

    // Verify password with bcrypt
    const isPasswordValid = await bcrypt.compare(String(password), user.password_hash);
    if (!isPasswordValid) {
      return res.status(401).json({
        message: 'Invalid email or password.',
      });
    }

    // Sign JWT token valid for 15 minutes
    const jwtSecret = process.env.JWT_SECRET || 'surakkha_secret_fallback_key';
    const token = jwt.sign(
      {
        userId: user.id,
        email: user.email,
      },
      jwtSecret,
      { expiresIn: '15m' }
    );

    // Return token and sanitized user details
    return res.status(200).json({
      message: 'Login successful.',
      token,
      user: {
        id: user.id,
        name: user.name,
        email: user.email,
        phone: user.phone,
        created_at: user.created_at,
      },
    });
  } catch (error) {
    console.error('[Error /login]:', error.message, error.stack);
    return res.status(500).json({
      message: 'Internal server error during login.',
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
  console.log(`[Auth Service] Running on http://localhost:${PORT}`);
});

// Graceful shutdown handling
const handleShutdown = async (signal) => {
  console.log(`\n[Auth Service] Received ${signal}. Closing HTTP server & Database pool...`);
  server.close(async () => {
    try {
      await pool.end();
      console.log('[Auth Service] Database pool closed. Process exiting cleanly.');
      process.exit(0);
    } catch (err) {
      console.error('[Auth Service] Error during pool disconnect:', err.message);
      process.exit(1);
    }
  });
};

process.on('SIGTERM', () => handleShutdown('SIGTERM'));
process.on('SIGINT', () => handleShutdown('SIGINT'));
