# Generation B — engineering documentation

Written for the person who picks this up next and needs to know *why*, not just *what*. The code
says what it does; these pages say what it is for, what was decided, and where to look.

> Earlier audit reports live alongside this file and are in Portuguese. Everything from
> September 2026 onward is in English, to match the code comments.

## Where things are

| I need to… | Read | Code lives in |
|---|---|---|
| Understand the creator-data vendor | [integrations/modash.md](integrations/modash.md) | `foundation/insights/`, `creators/internal/Modash*` |
| Set up or debug SendGrid callbacks | [operations/sendgrid-webhooks.md](operations/sendgrid-webhooks.md) | `outreach/api/WebhookController`, `outreach/internal/SendGridSignatureVerifier` |
| Get email actually delivered | [operations/email-deliverability.md](operations/email-deliverability.md) | `foundation/internal/EmailDnsChecker` |
| Answer "how long do we keep this?" | [compliance/data-retention.md](compliance/data-retention.md) | `shared/RetentionSweeper`, `*/internal/*Retention*` |
| Know why something was built that way | [decisions/](decisions/) | — |
| Hand it to the client to test | [operations/client-test-checklist.md](operations/client-test-checklist.md) | — |
| Deploy | [../../DEPLOY.md](../../DEPLOY.md) | — |

## The shape of the backend

Spring Modulith. Each top-level package under `com.generationb` is a module with a public surface
and an `internal` package nobody else may import. `ModulithTest` enforces this — it is not a
convention, it fails the build.

```
foundation/   auth, brands, audit, retention orchestration
              ai/       LLM client          (named interface "ai")
              email/    outbound email      (named interface "email")
              storage/  object storage      (named interface "storage")
              insights/ creator-data vendor (named interface "insights")
shared/       ports and events other modules talk through
creators/     creator database, matching, discovery, enrichment
campaigns/    kanban, cards, briefs-in-progress
briefs/       brief generation and the clause library
outreach/     email sending, replies, follow-ups
coverage/     clipping, digest, exports
gifting/      address capture, comp slips, dispatch
reporting/    metrics, templates, exports, KPI matching
marketing/    waitlist
attachments/  files hanging off cards and briefs
```

Cross-module calls go through a port in `shared` or a published type in the owning module's root
package. If you find yourself wanting to import someone else's `internal`, add a port instead —
that is what `CreatorLookupPort`, `BrandLookupPort` and `CoverageMetricsPort` are.

## Adding a table that holds personal data

Two things are not optional:

1. A `RetentionSweeper` implementation saying how long it is kept and why. See
   [compliance/data-retention.md](compliance/data-retention.md).
2. A line in the privacy notice that matches the sweeper's `policy()` string.

Nothing enforces this automatically. It is here so a reviewer can ask.

## Running it

```bash
# database
docker compose up -d

# backend, with the integrations live
SPRING_PROFILES_ACTIVE=local \
INSIGHTS_PROVIDER=modash MODASH_API_KEY=... \
mvn spring-boot:run

# frontend
cd ../generationBFE && npm run dev
```

Without `MODASH_API_KEY` the creator-data features fall back to a mock that logs every response
as `[MOCK MODASH]`. Without `GROQ_API_KEY` the AI features return a written fallback draft. Both
are deliberate: a fresh clone should run, and it should be obvious when a number is not real.

## Testing

| Layer | Count | Runs with |
|---|---|---|
| Backend unit + integration | 151 | `mvn test` (Testcontainers: Postgres + MinIO) |
| Frontend unit | 23 | `npm test` |
| End-to-end, desktop | 3 | `npx playwright test --project=chromium` |
| End-to-end, mobile | 34 | `npx playwright test --project=mobile` |

The mobile project runs on **WebKit at an iPhone 13 viewport** — the real iOS engine, not a
resized desktop window. It asserts the two things that actually make a page unusable on a phone:
the body never scrolls sideways, and controls meet the tap-size minimums (44px for buttons, the
WCAG 2.2 figure of 24px for links). Add a route to `ROUTES` in `e2e/responsive.spec.ts` when you
add a screen.

`npx playwright install webkit` is needed once before the mobile project will run.

### The vendor is never called for real

`support/ModashStub` serves captured Modash responses over a real localhost socket. That keeps
the transport honest — bearer token, retry on 429, `Retry-After`, the credit guard — while making
sure a CI run never spends the agency's metered credits. Three levels cover it: `ModashMappingTest`
(fields), `ModashClientTest` (transport), `ModashIntegrationTest` (request to database).
