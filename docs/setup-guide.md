# SmartQueue – Setup Guide

## Development Environment Setup

### Prerequisites

- **Java 17+** (OpenJDK or Eclipse Temurin)
- **Node.js 20+** with npm
- **Docker & Docker Compose** (for infrastructure services)
- **Maven 3.9+** (or use the Maven wrapper `./mvnw`)

### 1. Start Infrastructure Services

```bash
# Start MySQL, Redis, and RabbitMQ
docker-compose up mysql redis rabbitmq -d
```

This starts:
- **MySQL** on port 3306 (root/smartqueue123, database: smartqueue)
- **Redis** on port 6379
- **RabbitMQ** on port 5672 (management UI at http://localhost:15672)

### 2. Start the Backend

```bash
cd backend

# Using Maven wrapper (recommended)
./mvnw spring-boot:run

# Or if Maven is installed globally
mvn spring-boot:run
```

The backend starts on **http://localhost:8080**. On first run with `dev` profile, it automatically:
- Creates the database schema (JPA ddl-auto: update)
- Seeds demo data (branches, services, counters, users)

### 3. Start the Frontend

```bash
cd frontend
npm install    # First time only
npm run dev
```

The frontend starts on **http://localhost:3000**.

### 4. Verify Everything Works

1. Open http://localhost:3000 → Landing page loads
2. Click "Staff Login" → Login with `admin@smartqueue.com` / `admin123`
3. Dashboard should load with demo branch data

## Environment Variables

### Backend (`application.yml` or env vars)

| Variable | Default | Description |
|:---------|:--------|:------------|
| MYSQL_HOST | localhost | MySQL hostname |
| MYSQL_PORT | 3306 | MySQL port |
| MYSQL_DB | smartqueue | Database name |
| MYSQL_USER | root | MySQL user |
| MYSQL_PASSWORD | smartqueue123 | MySQL password |
| REDIS_HOST | localhost | Redis hostname |
| REDIS_PORT | 6379 | Redis port |
| RABBITMQ_HOST | localhost | RabbitMQ hostname |
| JWT_SECRET | (default base64) | JWT signing secret |
| CORS_ORIGINS | http://localhost:3000 | Allowed CORS origins |
| SMS_ENABLED | false | Enable SMS notifications |
| EMAIL_ENABLED | false | Enable email notifications |

### Frontend (`.env.local`)

| Variable | Default | Description |
|:---------|:--------|:------------|
| NEXT_PUBLIC_API_URL | http://localhost:8080/api | Backend API URL |
| NEXT_PUBLIC_WS_URL | http://localhost:8080/api/ws | WebSocket endpoint |

## Enabling Real Notifications

### Twilio (SMS + WhatsApp)
1. Create a Twilio account at https://twilio.com
2. Set environment variables:
   ```
   TWILIO_ACCOUNT_SID=your_sid
   TWILIO_AUTH_TOKEN=your_token
   TWILIO_PHONE_NUMBER=+1234567890
   SMS_ENABLED=true
   WHATSAPP_ENABLED=true
   ```

### SendGrid (Email)
1. Create a SendGrid account at https://sendgrid.com
2. Set environment variables:
   ```
   SENDGRID_API_KEY=your_api_key
   SENDGRID_FROM_EMAIL=noreply@yourdomain.com
   EMAIL_ENABLED=true
   ```

## Troubleshooting

| Issue | Solution |
|:------|:---------|
| MySQL connection refused | Ensure MySQL container is running: `docker ps` |
| Redis connection failed | Check Redis container: `docker logs smartqueue-redis` |
| CORS errors in browser | Verify `CORS_ORIGINS` includes your frontend URL |
| JWT token expired | Refresh tokens auto-rotate; check `jwt.access-token-expiration` |
| WebSocket not connecting | Ensure `/api/ws` endpoint is accessible from frontend |
