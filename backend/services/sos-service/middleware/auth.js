const jwt = require('jsonwebtoken');

/**
 * Authentication Middleware:
 * Extracts "Bearer <token>" from the Authorization header,
 * verifies it using the shared JWT_SECRET,
 * and attaches the decoded payload (e.g. { userId, email }) to req.user.
 */
const authMiddleware = (req, res, next) => {
  try {
    const authHeader = req.headers['authorization'] || req.headers.authorization;

    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return res.status(401).json({
        message: 'Access denied. Missing or malformed Authorization header (Expected: Bearer <token>).',
      });
    }

    const token = authHeader.split(' ')[1];

    if (!token) {
      return res.status(401).json({
        message: 'Access denied. Token is empty.',
      });
    }

    const jwtSecret = process.env.JWT_SECRET || 'surakkha_super_secret_key_123';
    const decoded = jwt.verify(token, jwtSecret);

    // Attach decoded user payload to request
    req.user = decoded;
    next();
  } catch (error) {
    if (error.name === 'TokenExpiredError') {
      return res.status(401).json({
        message: 'Token has expired. Please login again to obtain a new token.',
      });
    }
    return res.status(401).json({
      message: 'Invalid token. Verification failed.',
      error: error.message,
    });
  }
};

module.exports = authMiddleware;
