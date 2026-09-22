# Surakkha-BD — Geo Location & Proximity Service

Geospatial and proximity microservice for the **Surakkha-BD** platform.

This service tracks live user GPS locations and uses **PostgreSQL + PostGIS** spatial indexing to identify nearby emergency responders or citizens within a defined radius (e.g. 500m) around an incident.

---

## 🛠️ Microservice Overview

- **Port:** `3003`
- **Database:** PostgreSQL (`bpsn_db` / `surakkha_bd`) with **PostGIS** extension
- **Authentication:** Uses the shared `JWT_SECRET` from `auth-service` to authenticate requests via `Authorization: Bearer <token>`.

### PostGIS Table Schema
```sql
CREATE TABLE IF NOT EXISTS user_locations (
  id SERIAL PRIMARY KEY,
  user_id INTEGER REFERENCES users(id) UNIQUE,
  location GEOGRAPHY(POINT, 4326) NOT NULL,
  updated_at TIMESTAMP DEFAULT NOW()
);
```

---

## 🚀 Getting Started

### 1. Configure Environment Variables
Inside `backend/services/geo-service/`, copy `.env.example` to create your `.env` file:

```bash
cp .env.example .env
```
*(On Windows PowerShell: `Copy-Item .env.example .env`)*

Configure your `.env` file:
```env
PORT=3003
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
- **Development mode (auto-reload via nodemon):**
  ```bash
  npm run dev
  ```
- **Production mode:**
  ```bash
  npm start
  ```

Service will listen at: `http://localhost:3003`

---

## 📡 API Endpoints Documentation

### 1. Health Check
- **Method:** `GET`
- **URL:** `http://localhost:3003/health`

**cURL Command:**
```bash
curl http://localhost:3003/health
```

**Response (200 OK):**
```json
{
  "status": "ok"
}
```

---

### 2. Update User Location (Protected)
Updates or inserts (UPSERT) the authenticated user's current GPS location into the `user_locations` table using PostGIS geography.

- **Method:** `POST`
- **URL:** `http://localhost:3003/location/update`
- **Headers:**
  - `Content-Type: application/json`
  - `Authorization: Bearer <YOUR_JWT_TOKEN>`

#### Postman Body (raw JSON):
```json
{
  "latitude": 23.8103,
  "longitude": 90.4125
}
```

#### cURL Command:
```bash
curl -X POST http://localhost:3003/location/update \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <YOUR_JWT_TOKEN>" \
  -d "{\"latitude\": 23.8103, \"longitude\": 90.4125}"
```

#### Response (200 OK):
```json
{
  "message": "Location updated",
  "userId": 4,
  "updated_at": "2026-09-23T02:40:00.000Z"
}
```

---

### 3. Find Nearby Users (Protected / Internal Service Call)
Finds all users whose latest location is within `radius` meters from the given coordinates (excluding the requester user).

- **Method:** `GET`
- **URL:** `http://localhost:3003/nearby?latitude=23.8103&longitude=90.4125&radius=1000`
- **Headers:**
  - `Authorization: Bearer <YOUR_JWT_TOKEN>`
- **Query Parameters:**
  - `latitude` *(required)*: Latitude of the center point (e.g. incident location)
  - `longitude` *(required)*: Longitude of the center point
  - `radius` *(optional)*: Search radius in meters (default: `500`)

#### cURL Command:
```bash
curl "http://localhost:3003/nearby?latitude=23.8103&longitude=90.4125&radius=1000" \
  -H "Authorization: Bearer <YOUR_JWT_TOKEN>"
```

#### Response (200 OK):
```json
{
  "nearbyUserIds": [3],
  "count": 1,
  "radius": 1000
}
```
