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

## Seeded Accounts

Four accounts are seeded by `V15__seed_dev_users.sql`, and V44 puts every one of them on a
**single-use password**: `Password123!` works once, and the session it opens can do nothing
except set a real password. Every other endpoint answers `403 PASSWORD_CHANGE_REQUIRED` until
it is changed.

| Role | Email | Username |
|---|---|---|
| **ADMIN** | `admin@generationb.dev` | `admin` |
| **DIRECTOR** | `director@generationb.dev` | `director` |
| **ACCOUNT_MANAGER** | `am@generationb.dev` | `am` |
| **ACCOUNT_EXECUTIVE** | `ae@generationb.dev` | `ae` |

This is enforced in `PasswordChangeGate`, not in the frontend. A flag the UI merely respects is
bypassed by anyone who calls the API directly with the token the login just handed them, and
this password is printed in a file in the repository.

Local development note: after changing these passwords locally you will not get them back by
restarting — V44 has already run. `docker compose down -v` resets the database and replays
every migration, which puts them back on `Password123!`.

---

## Authentication API Endpoints

- `POST /api/auth/login` — accepts `{ "identifier": "admin@generationb.dev", "password": "Password123!" }`
- `POST /api/auth/refresh` — accepts `{ "refreshToken": "..." }`
- `POST /api/auth/logout` — accepts `{ "refreshToken": "..." }`
- `POST /api/auth/forgot-password` — accepts `{ "email": "admin@generationb.dev" }`
- `POST /api/auth/reset-password` — accepts `{ "token": "...", "newPassword": "..." }`
- `POST /api/auth/change-password` — accepts `{ "currentPassword": "...", "newPassword": "..." }`, returns a fresh token pair
- `GET /api/auth/me` — returns authenticated user data

---

## Production Free Deployment Guide

### 1. Database: Supabase (Free Managed PostgreSQL)
1. Create a free account on [Supabase.com](https://supabase.com) and a project.
2. **Connect → Session pooler** and copy that URI. Do *not* use the direct
   `db.<ref>.supabase.co` one: it resolves to IPv6 only, and Render's outbound is IPv4, so the
   app fails to start with `Network unreachable`.
3. Session pooler means port **5432**, not 6543. Transaction mode (6543) drops prepared
   statements, which Hibernate needs, and Flyway's migration lock needs a real session.

The username is `postgres.<project-ref>`, not plain `postgres`.

### 2. Backend: Render (Free Web Service)
1. Create a free account on [Render.com](https://render.com).
2. Create a **Web Service** connected to your GitHub repository `generationB`.
3. Select **Docker** environment.
4. Set Environment Variables:
   - `SPRING_PROFILES_ACTIVE`: `prod`
   - `SPRING_DATASOURCE_URL`: `jdbc:postgresql://aws-1-<region>.pooler.supabase.com:5432/postgres?sslmode=require`
   - `SPRING_DATASOURCE_USERNAME`: `postgres.<project-ref>`
   - `SPRING_DATASOURCE_PASSWORD`: `<your-db-password>`
   - `JWT_SECRET`: `<generate-a-long-random-256bit-string>`
   - `RESEND_API_KEY`: `<your-resend-api-key>`
   - `FRONTEND_URL`: `https://generation-bfe.vercel.app`
   - `CORS_ALLOWED_ORIGINS`: `https://generation-bfe.vercel.app`

### 3. Email: Resend (Free Transactional Email)
1. Create a free account on [Resend.com](https://resend.com).
2. Generate an API Key and set it in Render as `RESEND_API_KEY`.

### 4. Frontend: Vercel
`VITE_API_BASE_URL` is baked in at build time from `.env.production`, so there is nothing to set
in Vercel for a production deploy. Staging is a separate project built with
`npm run build:staging`, which reads `.env.staging` instead.

See `docs/operations/environments.md` for the full local / staging / production breakdown.
