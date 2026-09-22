# Surakkha-BD — SOS Emergency Incident Service

Emergency incident dispatch microservice for the **Surakkha-BD** platform.

This service receives emergency SOS triggers from the mobile app (or other clients), verifies the user identity using JWT issued by the **Auth Service**, and stores incident coordinates into PostgreSQL for emergency dispatch.

---

## 🛠️ Microservice Overview

- **Port:** `3002`
- **Database:** PostgreSQL (`bpsn_db` / `surakkha_bd`)
- **Shared Authentication:** Uses the same `JWT_SECRET` as `auth-service` to authenticate requests via `Authorization: Bearer <token>`.

### Incidents Table Schema
```sql
CREATE TABLE IF NOT EXISTS incidents (
  id SERIAL PRIMARY KEY,
  user_id INTEGER REFERENCES users(id),
  latitude DOUBLE PRECISION NOT NULL,
  longitude DOUBLE PRECISION NOT NULL,
  sos_type VARCHAR(50) DEFAULT 'general',
  status VARCHAR(20) DEFAULT 'pending',
  severity INTEGER DEFAULT 0,
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW()
);
```

---

## 🚀 Getting Started

### 1. Configure Environment Variables
Inside `backend/services/sos-service/`, copy `.env.example` to create your `.env` file:

```bash
cp .env.example .env
```
*(On Windows PowerShell: `Copy-Item .env.example .env`)*

Open `.env` and provide your credentials (make sure `JWT_SECRET` matches your `auth-service`):

```env
PORT=3002
DB_HOST=localhost
DB_PORT=5432
DB_NAME=surakkha_bd
DB_USER=postgres
DB_PASSWORD=your_postgres_password
JWT_SECRET=surakkha_super_secret_key_123
```

### 2. Install Dependencies
```bash
npm install
```

### 3. Run the Service
- **Development mode (with auto-reload via nodemon):**
  ```bash
  npm run dev
  ```
- **Production mode:**
  ```bash
  npm start
  ```

Server will run on: `http://localhost:3002`

---

## 📡 API Endpoints Documentation

### 1. Health Check
Public endpoint to check if the SOS microservice is running.

- **Method:** `GET`
- **URL:** `http://localhost:3002/health`

**cURL command:**
```bash
curl http://localhost:3002/health
```

**Response (200 OK):**
```json
{
  "status": "ok"
}
```

---

### 2. Create SOS Incident (Protected)
Records an emergency SOS incident with live GPS coordinates.

- **Method:** `POST`
- **URL:** `http://localhost:3002/sos`
- **Headers:**
  - `Content-Type: application/json`
  - `Authorization: Bearer <YOUR_JWT_TOKEN>`

#### How to set Authorization in Postman:
1. Go to the **Headers** tab in Postman.
2. Add a key: `Authorization`
3. Value: `Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...` *(Paste the token received from `POST /login` on Auth Service)*
*(Or select the **Auth** tab in Postman, select Type: **Bearer Token**, and paste your token).*

#### Postman Body (raw JSON):
```json
{
  "latitude": 23.8103,
  "longitude": 90.4125,
  "sos_type": "general"
}
```

#### cURL Command:
```bash
curl -X POST http://localhost:3002/sos \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <YOUR_JWT_TOKEN>" \
  -d "{\"latitude\": 23.8103, \"longitude\": 90.4125, \"sos_type\": \"general\"}"
```

#### Success Response (201 Created):
```json
{
  "message": "SOS incident created successfully.",
  "incident": {
    "id": 1,
    "status": "pending",
    "created_at": "2026-09-23T01:50:00.000Z",
    "latitude": 23.8103,
    "longitude": 90.4125,
    "sos_type": "general",
    "severity": 0
  }
}
```

#### Error Responses:
- `400 Bad Request`: If `latitude` or `longitude` is missing or invalid numbers.
- `401 Unauthorized`: If the Bearer token is missing, expired, or invalid.
- `500 Internal Server Error`: For database or server issues.

---

### 3. Get Specific Incident Details (Protected)
Retrieves the incident by ID, allowed only if the logged-in user is the owner of the incident.

- **Method:** `GET`
- **URL:** `http://localhost:3002/sos/:id`
- **Headers:**
  - `Authorization: Bearer <YOUR_JWT_TOKEN>`

#### cURL Command:
```bash
curl http://localhost:3002/sos/1 \
  -H "Authorization: Bearer <YOUR_JWT_TOKEN>"
```

#### Success Response (200 OK):
```json
{
  "message": "Incident retrieved successfully.",
  "incident": {
    "id": 1,
    "user_id": 1,
    "latitude": 23.8103,
    "longitude": 90.4125,
    "sos_type": "general",
    "status": "pending",
    "severity": 0,
    "created_at": "2026-09-23T01:50:00.000Z",
    "updated_at": "2026-09-23T01:50:00.000Z"
  }
}
```

#### Error Responses:
- `401 Unauthorized`: Missing or invalid token.
- `403 Forbidden`: If the user tries to view an incident belonging to another user.
- `404 Not Found`: If no incident exists with that ID.
