# 🚦 SmartQueue — Real-Time Queue Management System

![Java](https://img.shields.io/badge/Java_17-ED8B00?style=for-the-badge&logo=java&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot_3.5-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)
![Next.js](https://img.shields.io/badge/Next.js_15-000000?style=for-the-badge&logo=nextdotjs&logoColor=white)
![Redis](https://img.shields.io/badge/Redis_7-DC382D?style=for-the-badge&logo=redis&logoColor=white)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-FF6600?style=for-the-badge&logo=rabbitmq&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL_8-005C84?style=for-the-badge&logo=mysql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2CA5E0?style=for-the-badge&logo=docker&logoColor=white)
![JWT](https://img.shields.io/badge/JWT-black?style=for-the-badge&logo=JSON%20web%20tokens)

> An enterprise-grade, production-ready system for hospitals, banks, and service centers to manage walk-in queues, pre-booked appointments, real-time updates, and multi-branch operations.

---

## 🏗️ Architecture

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│   Next.js    │────▶│  Spring Boot │────▶│    MySQL     │
│   Frontend   │◀────│   Backend    │◀────│   Database   │
└──────────────┘     └──────┬───────┘     └──────────────┘
       │                    │
       │ WebSocket          │
       │ (STOMP/SockJS)     │
       │                    ├────▶ Redis (Cache + Queue State)
       │                    │
       │                    └────▶ RabbitMQ (Notifications)
       │                              │
       └──────────────────────────────┘
```

---

## 🚀 Quick Start

### Prerequisites
- Docker & Docker Compose
- OR Java 17+, Node.js 20+, MySQL 8, Redis 7, RabbitMQ 3

### Option 1: Docker Compose (Recommended)

```bash
# Clone and start all services
git clone https://github.com/megituser/SMARTQUEUE.git
cd SMARTQUEUE
docker-compose up -d

# Frontend:             http://localhost:3000
# Backend API:          http://localhost:8080/api
# RabbitMQ Management:  http://localhost:15672 (guest/guest)
```

### Option 2: Local Development

```bash
# 1. Start infrastructure
docker-compose up mysql redis rabbitmq -d

# 2. Start backend
cd backend
./mvnw spring-boot:run

# 3. Start frontend
cd frontend
npm install
npm run dev
```

### Default Credentials

| Role | Email | Password |
|------|-------|----------|
| Super Admin | admin@smartqueue.com | admin123 |
| Hospital Admin | hospital.admin@smartqueue.com | admin123 |
| Bank Admin | bank.admin@smartqueue.com | admin123 |
| Hospital Staff | staff1@smartqueue.com | staff123 |
| Bank Staff | staff2@smartqueue.com | staff123 |
| Receptionist | receptionist@smartqueue.com | staff123 |

---

## 📋 Features

### Queue System
- **Token Generation** — Auto-numbered tokens per service (e.g., GC001, LT002)
- **Priority Queue** — VIP > HIGH > NORMAL with FIFO within priority
- **Auto-Advance** — Automatic queue advancement when service completes
- **Counter Assignment** — Intelligent routing based on service type

### Appointments
- **Slot Booking** — Visual time-slot picker with availability
- **Double-Booking Prevention** — Database-level + application-level checks
- **Check-In → Queue** — Appointments convert to HIGH priority queue tokens

### Real-Time
- **WebSocket Updates** — STOMP over SockJS for live queue status
- **Zero Polling** — All updates pushed via WebSocket
- **TV Display Mode** — Full-screen queue board for lobby screens

### Notifications
- **SMS** (Twilio) — Token issued, token called
- **Email** (SendGrid) — Appointment confirmations, reminders
- **WhatsApp** (Twilio) — Real-time alerts

### Security
- **JWT Authentication** — Access (15m) + Refresh (7d) tokens
- **RBAC** — SUPER_ADMIN, BRANCH_ADMIN, STAFF, RECEPTIONIST
- **Secure Endpoints** — Role-based API protection

---

## 🔧 API Endpoints

### Authentication
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/auth/register` | Register user |
| POST | `/api/v1/auth/login` | Login → JWT |
| POST | `/api/v1/auth/refresh` | Refresh token |
| POST | `/api/v1/auth/logout` | Revoke token |

### Queue
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/queue/tokens` | Issue new token |
| GET | `/api/v1/queue/tokens/{id}` | Get token status |
| GET | `/api/v1/queue/branch/{id}/status` | Live queue status |
| POST | `/api/v1/queue/tokens/{id}/cancel` | Cancel token |

### Counters
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/counters/{id}/open` | Open counter |
| POST | `/api/v1/counters/{id}/close` | Close counter |
| POST | `/api/v1/counters/{id}/next` | Call next token |
| POST | `/api/v1/counters/{id}/complete` | Complete service |
| POST | `/api/v1/counters/{id}/no-show` | Mark no-show |

### Appointments
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/appointments` | Book appointment |
| GET | `/api/v1/appointments/slots` | Get available slots |
| POST | `/api/v1/appointments/{id}/check-in` | Check in → queue |
| POST | `/api/v1/appointments/{id}/cancel` | Cancel appointment |

### Branches
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/branches` | List all branches |
| POST | `/api/v1/branches` | Create branch |
| GET | `/api/v1/branches/{id}/dashboard` | Dashboard stats |
| GET | `/api/v1/branches/{id}/services` | List services |

### WebSocket
| Endpoint | Description |
|----------|-------------|
| `/api/ws` | STOMP endpoint (SockJS) |
| `/topic/queue/{branchId}` | Queue status updates |
| `/topic/token/{tokenId}` | Individual token updates |
| `/topic/counter/{branchId}/{counterId}` | Counter updates |

---

## 🏢 Project Structure

```
SMARTQUEUE/
├── backend/                    # Spring Boot 3.5
│   ├── src/main/java/com/smartqueue/
│   │   ├── config/            # Security, WebSocket, Redis, RabbitMQ
│   │   ├── controller/        # REST API controllers
│   │   ├── service/           # Business logic
│   │   ├── repository/        # JPA repositories
│   │   ├── model/             # JPA entities + enums
│   │   ├── dto/               # Request/Response DTOs
│   │   ├── security/          # JWT + filters
│   │   ├── websocket/         # WebSocket services
│   │   ├── notification/      # RabbitMQ notification system
│   │   ├── scheduler/         # Cron jobs (no-show, reminders)
│   │   ├── cache/             # Redis cache services
│   │   └── exception/         # Global error handling
│   └── src/test/              # Unit + integration tests
├── frontend/                   # Next.js 15 + React 19
│   └── src/
│       ├── app/               # App Router pages
│       ├── lib/               # API client, WebSocket, types
│       ├── providers/         # Auth, React Query
│       └── hooks/             # Custom hooks
├── docker-compose.yml         # Full stack deployment
└── .github/workflows/ci.yml  # CI/CD pipeline
```

---

## 📊 Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 17, Spring Boot 3.5, Spring Security, Spring WebSocket |
| Frontend | Next.js 15, React 19, TypeScript, Tailwind CSS |
| Database | MySQL 8 (JPA/Hibernate) |
| Cache | Redis 7 (queue state, session cache) |
| Messaging | RabbitMQ 3 (notification events) |
| Auth | JWT (access + refresh tokens) |
| Real-Time | STOMP over SockJS |
| DevOps | Docker, GitHub Actions CI/CD |

---

## 📈 Performance

| Metric | Value |
|--------|-------|
| API Response Time | < 200ms (Redis-accelerated) |
| WebSocket Latency | < 50ms state propagation |
| Concurrent Users | Designed for 10,000+ |
| Queue Operations | < 100ms (Redis sorted sets) |

---

## 👨‍💻 Developer

**Arpit Pandey** — Backend Developer
- GitHub: [@megituser](https://github.com/megituser)
- Email: apandey9720@gmail.com

---

## 📜 License

MIT License — for academic and portfolio purposes.
