# SmartQueue – Deployment Guide

## Production Deployment with Docker Compose

### 1. Configure Environment

Create a `.env` file in the project root:

```env
# Database
MYSQL_ROOT_PASSWORD=<strong-password>
MYSQL_DATABASE=smartqueue
MYSQL_USER=smartqueue
MYSQL_PASSWORD=<strong-password>

# Redis
REDIS_PASSWORD=<redis-password>

# RabbitMQ
RABBITMQ_USER=smartqueue
RABBITMQ_PASSWORD=<rabbitmq-password>

# JWT
JWT_SECRET=<base64-encoded-secret-at-least-256-bits>

# CORS
CORS_ORIGINS=https://yourdomain.com

# Notifications (optional)
SMS_ENABLED=true
TWILIO_ACCOUNT_SID=xxx
TWILIO_AUTH_TOKEN=xxx
TWILIO_PHONE_NUMBER=+1xxx
EMAIL_ENABLED=true
SENDGRID_API_KEY=xxx
SENDGRID_FROM_EMAIL=noreply@yourdomain.com
```

### 2. Build and Deploy

```bash
# Build all images
docker-compose -f docker-compose.yml build

# Start in detached mode
docker-compose -f docker-compose.yml up -d

# Check health
docker-compose ps
docker-compose logs -f backend
```

### 3. Production Checklist

- [ ] Change all default passwords
- [ ] Generate strong JWT secret: `openssl rand -base64 64`
- [ ] Set `spring.jpa.hibernate.ddl-auto` to `validate` (not `update`)
- [ ] Run database migrations separately (Flyway/Liquibase)
- [ ] Enable TLS/HTTPS via reverse proxy (nginx/Traefik)
- [ ] Configure log aggregation (ELK/Loki)
- [ ] Set up health check monitoring
- [ ] Configure backup for MySQL volumes
- [ ] Set Redis password and persistence
- [ ] Rate limit public endpoints

### 4. Scaling

```bash
# Scale backend instances
docker-compose up -d --scale backend=3
```

For horizontal scaling:
- Use a load balancer (nginx, HAProxy, Traefik) in front of backend instances
- Enable Redis-backed session/WebSocket stickiness or use a WebSocket broker (RabbitMQ STOMP)
- Connect all instances to the same MySQL + Redis + RabbitMQ

### 5. Monitoring

- **RabbitMQ**: http://your-host:15672 (management UI)
- **Health Endpoint**: `GET /api/actuator/health` (add spring-boot-starter-actuator)
- **Metrics**: Export to Prometheus/Grafana via Micrometer
