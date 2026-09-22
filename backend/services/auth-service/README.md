# Surakkha-BD — Auth Microservice

Authentication and user management microservice for the **Surakkha-BD** (Bangladesh Public Safety Network) emergency response platform.

Built with **Node.js**, **Express**, **PostgreSQL** (`pg`), **bcrypt**, and **JSON Web Tokens (JWT)**.

---

## 🛠️ Requirements & Prerequisites

- **Node.js** (v18 or higher recommended)
- **PostgreSQL** running locally or accessible via network
- Database created: `bpsn_db`
- `users` table already created with the following schema:
  ```sql
  CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(150) UNIQUE NOT NULL,
    phone VARCHAR(20),
    password_hash TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
  );
  ```

---

## 🚀 Getting Started

### 1. Configure Environment Variables
Inside this folder (`backend/services/auth-service`), copy `.env.example` to create your `.env` file:

```bash
cp .env.example .env
```
*(On Windows PowerShell, you can run: `Copy-Item .env.example .env`)*

Open `.env` and fill in your actual PostgreSQL database credentials and a strong secret key for JWT:

```env
PORT=3001
DB_HOST=localhost
DB_PORT=5432
DB_NAME=bpsn_db
DB_USER=postgres
DB_PASSWORD=your_postgres_password_here
JWT_SECRET=your_super_secret_jwt_key_here
```

### 2. Install Dependencies
```bash
npm install
```

### 3. Run the Service

- **Development mode (with auto-reload using nodemon):**
  ```bash
  npm run dev
  ```

- **Production mode:**
  ```bash
  npm start
  ```

Once running, the service will listen at `http://localhost:3001`.

---

## 📡 API Endpoints & Testing

### 1. Health Check
Checks if the auth microservice is running.

- **Method:** `GET`
- **URL:** `http://localhost:3001/health`

**cURL command:**
```bash
curl http://localhost:3001/health
```

**Expected Response (200 OK):**
```json
{
  "status": "ok"
}
```

---

### 2. User Registration
Registers a new user, hashes their password with bcrypt, and stores them in PostgreSQL.

- **Method:** `POST`
- **URL:** `http://localhost:3001/register`
- **Headers:** `Content-Type: application/json`

**Postman Body (raw JSON):**
```json
{
  "name": "Shakil Ahmed",
  "email": "shakil@example.com",
  "phone": "+8801700000000",
  "password": "SecurePassword123"
}
```

**cURL command:**
```bash
curl -X POST http://localhost:3001/register \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"Shakil Ahmed\",\"email\":\"shakil@example.com\",\"phone\":\"+8801700000000\",\"password\":\"SecurePassword123\"}"
```

**Success Response (201 Created):**
```json
{
  "message": "User registered successfully.",
  "user": {
    "id": 1,
    "name": "Shakil Ahmed",
    "email": "shakil@example.com",
    "phone": "+8801700000000",
    "created_at": "2026-09-22T14:50:00.000Z"
  }
}
```

**Error Responses:**
- `400 Bad Request`: If `name`, `email`, or `password` is missing.
- `409 Conflict`: If the email is already registered.
- `500 Internal Server Error`: For database or server issues.

---

### 3. User Login
Authenticates an existing user and returns a signed JWT token valid for 15 minutes.

- **Method:** `POST`
- **URL:** `http://localhost:3001/login`
- **Headers:** `Content-Type: application/json`

**Postman Body (raw JSON):**
```json
{
  "email": "shakil@example.com",
  "password": "SecurePassword123"
}
```

**cURL command:**
```bash
curl -X POST http://localhost:3001/login \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"shakil@example.com\",\"password\":\"SecurePassword123\"}"
```

**Success Response (200 OK):**
```json
{
  "message": "Login successful.",
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "user": {
    "id": 1,
    "name": "Shakil Ahmed",
    "email": "shakil@example.com",
    "phone": "+8801700000000",
    "created_at": "2026-09-22T14:50:00.000Z"
  }
}
```

**Error Responses:**
- `400 Bad Request`: If `email` or `password` is missing.
- `401 Unauthorized`: If the email does not exist or password does not match.
- `500 Internal Server Error`: For server errors.
