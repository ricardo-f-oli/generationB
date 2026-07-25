# Generation B

Creator Management Platform for talent/influencer agencies — built as a modular monolith with Spring Boot and Spring Modulith.

## Tech Stack

- **Java 21**, Spring Boot 3.4.1
- **Spring Modulith** — enforces module boundaries (`api` = public surface, `internal` = private implementation)
- **PostgreSQL** + **Flyway** for schema migrations
- **Spring Security** with stateless JWT authentication
- **MapStruct** + **Lombok** for entity/DTO mapping and boilerplate
- **SendGrid** for outbound/inbound email
- **Anthropic API** for AI-generated outreach templates and follow-up suggestions
- **OpenPDF** for brief exports

## Getting Started

### Prerequisites

- JDK 21
- Maven (or use the included `mvnw` wrapper, if present)
- A running PostgreSQL instance

### 1. Create the database

```sql
CREATE DATABASE generationb;
```

### 2. Configure environment variables

The app reads its datasource config from environment variables (see `src/main/resources/application.yml`), falling back to local defaults if unset:

| Variable | Default |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/generationb` |
| `SPRING_DATASOURCE_USERNAME` | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | `postgres` |

Optional integrations (fall back to mock values if unset, so the app runs without them):

| Variable | Purpose |
|---|---|
| `outreach.sendgrid.api-key` | SendGrid API key for sending outreach emails |
| `outreach.sendgrid.webhook-secret` | Validates inbound SendGrid webhook signatures |
| `anthropic.api-key` | Powers AI-generated outreach templates and follow-up suggestions |

### 3. Run the app

```bash
mvn spring-boot:run
```

Flyway will run migrations automatically on startup (`ddl-auto: validate`, so the schema is fully migration-driven). The API is served on `http://localhost:8080`.

### 4. Authenticate

There's no real user/password flow yet — `POST /api/auth/login` mints a JWT from whatever `email`, `role`, `brandId`, and `userId` you pass in (defaulting to a stub admin/brand if omitted):

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@agency.com","role":"ADMIN"}'
```

Use the returned token as a `Bearer` token on all other endpoints (everything under `/api/**` requires auth except `/api/auth/login`, `/api/creators/register`, and `/api/briefs/share/**`).

### Running tests

```bash
mvn test
```

Includes Spring Modulith tests that verify module boundaries aren't violated, plus integration tests (H2 for tests, Testcontainers-style patterns not required at runtime).

## What's Currently Functional

The codebase is organized as Spring Modulith application modules. Modules with real implementations:

- **`foundation`** — JWT auth/login, security config, audit logging (AOP-based), global exception handling, brand-scoped request context.
- **`briefs`** — Creative briefs: create/update/list/fetch, PDF export, share-via-link (with a public token-based view endpoint), AI-assisted generation, and contract clauses attached to a brief (with reordering).
- **`campaigns`** — Campaigns CRUD/archive, plus a Kanban board system per campaign: boards, cards, drag-and-drop move, bulk move, payment status tracking on cards.
- **`outreach`** — Outreach templates (CRUD + AI-generation via Anthropic), outreach campaigns with recipients (add/remove, preview merged content, send/schedule), email thread history per recipient, SendGrid inbound/outbound webhook handling, and AI-suggested follow-ups.

Modules that exist as module boundaries but currently have **no implementation** (empty except for the `@ApplicationModule` marker):

- **`creators`**
- **`gifting`**
- **`coverage`**
- **`reporting`**

Note: security currently permits `/api/creators/register`, but the `creators` module itself has no code yet — that route isn't implemented.

## Module Structure

Each module follows the same shape:

```
<module>/
  api/          <- REST controllers (public)
  internal/     <- entities, repositories, services (package-private, not accessible from other modules)
  *.java        <- DTOs, enums, commands (public, shared contract)
  package-info.java <- @ApplicationModule marker
```

Cross-module communication goes through Spring application events (see `shared/` for event/query types like `CardMovedEvent`, `CreatorFlaggedEvent`, `ResolveCreatorContactQuery`) rather than direct calls into another module's `internal` package.
