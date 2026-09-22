const path = require('path');

// Load .env from current directory first, then fallback to backend parent directory
require('dotenv').config({ path: path.resolve(__dirname, '.env') });
require('dotenv').config({ path: path.resolve(__dirname, '../../.env') });

const { Pool } = require('pg');

const pool = new Pool({
  host: process.env.DB_HOST || 'localhost',
  port: parseInt(process.env.DB_PORT || '5432', 10),
  database: process.env.DB_NAME || 'surakkha_bd',
  user: process.env.DB_USER || 'postgres',
  password: String(process.env.DB_PASSWORD || ''),
});

pool.on('connect', () => {
  console.log(`[Database] Connected to PostgreSQL (${process.env.DB_NAME || 'surakkha_bd'}).`);
});

pool.on('error', (err) => {
  console.error('[Database] Unexpected idle client error:', err.message);
});

pool.pool = pool;

module.exports = pool;
