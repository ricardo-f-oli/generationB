# Generation B — Backend API

Creator Management Platform for talent/influencer agencies — built as a modular monolith with Spring Boot and Spring Modulith.

## Tech Stack

- **Java 21**, **Spring Boot 3.4.1**
- **Spring Modulith** — enforces module boundaries
- **PostgreSQL** + **Flyway** schema migrations
- **Spring Security** with JWT authentication & refresh tokens
- **Lombok** for entity/DTO boilerplate
- **Docker & Docker Compose** for local orchestration
- **Mailpit** for local email inspection

---

## Quick Start (Local Docker Environment)

Run the backend, PostgreSQL database, and Mailpit email inspector with a single command:

```bash
docker compose up --build
```

Services will start:
- **Backend API**: `http://localhost:8080/api`
- **PostgreSQL DB**: `localhost:5432` (`generationb`)
- **Mailpit Web UI**: `http://localhost:8025` (captures dev password reset emails)

---

## Local Test Accounts (Dev Seed Data)

All test accounts are seeded automatically via Flyway migration `V15__seed_dev_users.sql` with default password: `Password123!`

| Role | Email | Username | Password |
|---|---|---|---|
| **ADMIN** | `admin@generationb.dev` | `admin` | `Password123!` |
| **DIRECTOR** | `director@generationb.dev` | `director` | `Password123!` |
| **ACCOUNT_MANAGER** | `am@generationb.dev` | `am` | `Password123!` |
| **ACCOUNT_EXECUTIVE** | `ae@generationb.dev` | `ae` | `Password123!` |

---

## Authentication API Endpoints

- `POST /api/auth/login` — accepts `{ "identifier": "admin@generationb.dev", "password": "Password123!" }`
- `POST /api/auth/refresh` — accepts `{ "refreshToken": "..." }`
- `POST /api/auth/logout` — accepts `{ "refreshToken": "..." }`
- `POST /api/auth/forgot-password` — accepts `{ "email": "admin@generationb.dev" }`
- `POST /api/auth/reset-password` — accepts `{ "token": "...", "newPassword": "..." }`
- `GET /api/auth/me` — returns authenticated user data

---

## Production Free Deployment Guide

### 1. Database: Neon (Free Managed PostgreSQL)
1. Create a free account on [Neon.tech](https://neon.tech).
2. Create a project named `generationb`.
3. Copy the PostgreSQL connection string (`postgres://user:password@ep-xxx.neon.tech/generationb?sslmode=require`).

### 2. Backend: Render (Free Web Service)
1. Create a free account on [Render.com](https://render.com).
2. Create a **Web Service** connected to your GitHub repository `generationB`.
3. Select **Docker** environment.
4. Set Environment Variables:
   - `SPRING_PROFILES_ACTIVE`: `prod`
   - `SPRING_DATASOURCE_URL`: `jdbc:postgresql://<neon-host>:5432/generationb?sslmode=require`
   - `SPRING_DATASOURCE_USERNAME`: `<neon-username>`
   - `SPRING_DATASOURCE_PASSWORD`: `<neon-password>`
   - `JWT_SECRET`: `<generate-a-long-random-256bit-string>`
   - `RESEND_API_KEY`: `<your-resend-api-key>`
   - `FRONTEND_URL`: `https://generation-bfe.vercel.app`
   - `CORS_ALLOWED_ORIGINS`: `https://generation-bfe.vercel.app`

### 3. Email: Resend (Free Transactional Email)
1. Create a free account on [Resend.com](https://resend.com).
2. Generate an API Key and set it in Render as `RESEND_API_KEY`.

### 4. Frontend: Vercel
1. Set `VITE_USE_MOCK_DATA=false` in Vercel project environment variables.
2. Set `VITE_API_BASE_URL=https://<your-render-app>.onrender.com/api`.
