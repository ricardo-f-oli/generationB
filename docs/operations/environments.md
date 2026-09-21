# Environments

Three, and what each one is for.

| | local | staging | production |
|---|---|---|---|
| Spring profile | `local` | `staging` | `prod` |
| Branch | any | `staging` | `main` |
| API | `http://localhost:8080/api` | `generationb-api-staging.onrender.com` | `generationb-api.onrender.com` |
| Database | Docker Postgres | Supabase project `staging` | Supabase project `prod` |
| Frontend | `npm run dev` | Vercel project `generation-bfe-staging` | Vercel project `generation-bfe` |
| Creator data | mock | mock | Meta + YouTube |
| Email | Mailpit (nothing leaves) | SendGrid sandbox | SendGrid, live |
| Retention sweeper | on | **off** | on |
| Sample dataset | kept | **removed** | kept |

The design rule: **staging differs from production in blast radius, never in code path.** Every
guard prod turns on is on in staging too — `JWT_SECRET` required, CORS declared explicitly,
secure cookies, S3 storage. A staging run that goes green because a control was relaxed has
tested nothing. What differs is that breaking staging costs nothing.

---

## Standing up staging: the checklist

Roughly 45 minutes, all on free tiers.

### 1. The branch

```bash
git checkout main && git pull
git checkout -b staging && git push -u origin staging
```

Both repos — `generationB` and `generationBFE`. From here, work merges into `staging`, gets
looked at on the deployed staging site, and only then merges into `main`.

### 2. Database — a second Supabase project

Supabase has no branching on the free tier, so staging is a *separate project* rather than a
branch. That is stricter isolation than the Neon branch this originally described, and it means
a staging reset is a project reset.

1. Supabase → **New project**, named `generationb-staging`.
2. **Connect → Session pooler**, copy the URI.

Two things that will cost you an afternoon if you skip them:

- Use the **session pooler** host (`aws-1-<region>.pooler.supabase.com:5432`), never the direct
  `db.<ref>.supabase.co`. The direct host is IPv6-only and Render's outbound is IPv4 — the app
  dies at boot with `Network unreachable`.
- Port **5432** (session mode), not 6543 (transaction mode). Transaction mode does not carry
  prepared statements, which Hibernate needs, and Flyway's lock needs a real session.

The username is `postgres.<project-ref>`.

### 3. Backend — a second Render service

`render.yaml` already declares `generationb-api-staging` on the `staging` branch. In Render:
**New → Blueprint**, point it at the repo, and it will offer both services. Render prompts for
every `sync: false` value.

Fill each one **separately from production's**. The four that matter most:

- `SPRING_DATASOURCE_*` — the **staging project's** session-pooler URL. Not production's.
- `JWT_SECRET` — generated per service, so a staging token is worthless against production.
- `OUTREACH_FROM_ADDRESS` — a staging sender. Never `noreply@btheagency.com`.
- `STORAGE_BUCKET` — `generationb-staging`. A separate R2 bucket.

Meta and YouTube credentials are deliberately left **unset** on staging. Meta's hashtag budget
(30 unique tags per rolling 7 days) is per *app* and shared with production: a staging run that
burns it is a production outage in mention discovery. Unset means the app runs on generated
sample data and logs every call as `[MOCK INSIGHTS]`.

### 4. Frontend — a second Vercel project

1. Vercel → **Add New → Project** → same `generationBFE` repo.
2. Name `generation-bfe-staging`, **Production Branch: `staging`**.
3. Build command: `npm run build:staging` — *not* `npm run build`.

That last line is load-bearing. Vite inlines `VITE_API_BASE_URL` at build time; a bundle built
with the default script has production's URL baked in and no amount of environment variables
will repoint it afterwards. `.env.staging` holds the staging URL and only `--mode staging`
reads it.

### 5. Close the loop on CORS

Once Vercel gives you the staging domain, set it on the staging Render service as **both**
`CORS_ALLOWED_ORIGINS` and `FRONTEND_URL`, then redeploy. The `staging` profile has no default
for either — a missing value fails at startup rather than silently allowing every origin.

### 6. Empty it for the client (handover only)

The staging profile already sets:

```yaml
spring.flyway:
  locations: classpath:db/migration,classpath:db/handover
```

which runs `V900__remove_demo_data.sql` and leaves the environment with no creators, campaigns,
coverage, gifting or reports — but with the clause library, board templates, report templates
and style taxonomy intact, because a campaign cannot be created without them.

That script is **not** in the default Flyway path, and must not be put there. In production it
is a data-loss event: `DELETE FROM creators` cannot tell a seeded Priya Patel from a creator the
agency signed last week. Locally and in CI the sample data is load-bearing — `TenantIsolationTest`
addresses a V33 campaign card by its id.

If you would rather keep the demo data on staging for your own testing, set
`FLYWAY_LOCATIONS=classpath:db/migration` on the staging service and redeploy.

### 7. Sign in for the first time

All four seeded accounts (`admin@`, `director@`, `am@`, `ae@` `generationb.dev`) are on
`Password123!` as a **single-use** password. The first login succeeds and then the API refuses
everything except `POST /api/auth/change-password` until a real password is set —
`PasswordChangeGate` enforces that server-side, so it cannot be skipped by calling the API
directly.

Hand the client the four addresses and the temporary password. Each person sets their own on
first login; after that the seeded credential is dead.

### 8. Verify

```bash
curl -fs https://generationb-api-staging.onrender.com/api/health/live
```

Then open the staging site, register a test creator at `/register`, and confirm the application
appears at `/creators/registrations`. If that round trip works, staging is real.

---

## Things that will bite you

**Free Render services sleep.** After ~15 minutes idle, the first request takes 30–60s while the
container wakes. Before a demo, hit the health endpoint once to warm it.

**Watch the compute quota, on any serverless Postgres.** This bit once already: Hikari was
configured with `minimum-idle: 1`, which holds a connection open forever, which stops the
database suspending, which bills compute 24/7 on a system with almost no users. The Neon free
allowance ran out and the API stopped booting. `minimum-idle` is now `0` — do not raise it.

**`npm run build` on the staging Vercel project points staging at production.** Worth repeating
because it fails silently and looks like staging "working".

**Handover cleanup runs once, and Flyway remembers it.** After `V900` has run, that database has
a history row for a script the default location set does not resolve. `spring.flyway.ignore-migration-patterns`
is set to `*:missing` so this does not fail validation on the next boot — do not remove it.

**The promotion path is `staging` → `main`.** Deploying a hotfix straight to `main` is fine in an
emergency; forgetting to merge it back into `staging` afterwards means the next staging deploy
reintroduces the bug.
