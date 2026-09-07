# SendGrid webhooks

Two callbacks, two different authentication problems. Requirement **#30** (reply capture) and
**#31** (per-recipient status).

Both endpoints are `permitAll` in `SecurityConfig` — SendGrid cannot hold a session — so each one
authenticates the caller itself.

## What was wrong

Until September 2026 the event webhook's entire signature check was:

```java
return !"INVALID_SIG".equals(signature);   // accepts literally anything else
```

and the inbound reply endpoint had no check at all. Either was enough for anyone who knew the URL
to mark a creator as having replied, or to file a spam report that suppresses that creator across
every brand in the platform.

## Event webhook — signed

`POST /api/webhooks/sendgrid`

SendGrid signs with **ECDSA on NIST P-256, SHA-256**. The signed payload is the timestamp header
concatenated with the **raw** request body.

That last word is the whole trick: the controller takes `@RequestBody String` and parses it with
Jackson by hand, because re-serialising a parsed `List<Map>` changes whitespace and key order and
the signature stops matching. If you ever "tidy" that signature up into a typed body, verification
breaks and — depending on how the failure is handled — may break open.

### Setup

1. SendGrid → Settings → Mail Settings → Event Webhook
2. Switch on **Signed Event Webhook Requests**
3. Copy the verification key
4. `SENDGRID_WEBHOOK_PUBLIC_KEY=<the base64 key>`

### It fails closed

| Situation | Result |
|---|---|
| Valid signature, fresh timestamp | accepted |
| Signature does not verify | **403** |
| Verifies, but timestamp outside tolerance | **403** — replay, or this server's clock has drifted |
| Headers absent | **403** |
| No public key configured | **403**, with an error log saying exactly what to set |

An unverified webhook is worse than a missing one: it looks like it works while accepting
anything. `SENDGRID_WEBHOOK_ALLOW_UNSIGNED=true` exists for local development only and logs a
warning on **every** call it lets through, so it cannot be left on by accident. The `local`
profile turns it on; nothing else should.

`SENDGRID_WEBHOOK_TOLERANCE_SECONDS` defaults to 600. Repeated `STALE` rejections in production
almost always mean NTP, not an attack.

### Statuses it writes

`delivered` → `DELIVERED`, `open`/`click` → `OPENED`, `bounce`/`dropped` → `BOUNCED`,
`unsubscribe` → `UNSUBSCRIBED` **and** a `CreatorFlaggedEvent`, which is what puts them on the
global suppression list for every brand (#21). `spamreport` raises the same event.

A click never walks a `REPLIED` backwards. One malformed event is skipped rather than failing the
batch — SendGrid retries the whole batch on a non-2xx, which would replay the events that did
work.

## Inbound Parse — not signed

`POST /api/webhooks/sendgrid/inbound/{token}`

SendGrid offers **no signature** on Inbound Parse. The accepted practice, and what their own
documentation suggests, is an unguessable URL. So the token is a path segment, compared with
`MessageDigest.isEqual` — a timing oracle on a shared secret is still a timing oracle.

```bash
openssl rand -hex 32          # generate
OUTREACH_SENDGRID_INBOUND_TOKEN=<that value>
```

Then point SendGrid's Inbound Parse host at
`https://<host>/api/webhooks/sendgrid/inbound/<that value>`.

Empty token means the endpoint stays **closed** and logs why.

> This changed the URL. The old unauthenticated `/inbound` path no longer exists, so the Inbound
> Parse setting in SendGrid must be updated at the same time as the deploy.

### Where replies go

The reply is stored on the thread, the recipient moves to `REPLIED`, and the message is forwarded
to **whoever ran the outreach** — resolved via `OutreachCampaign.createdBy`. This used to be the
hard-coded string `manager@btheagency.com`, so every creator reply for every brand went to one
mailbox that may not exist.

`OUTREACH_REPLY_FALLBACK_ADDRESS` catches the case where the campaign owner has no email. Without
it the reply is still stored — it is only the notification that is lost, and that is logged.

## Still outstanding

Requirement #28 needs DKIM, SPF and DMARC records on the sending domain, plus an inbound MX record
pointing at SendGrid. Nothing in this codebase can supply those; they are DNS changes on
`btheagency.com`.

## Testing it locally

```bash
# with the local profile, unsigned is allowed and logs a warning
curl -X POST http://localhost:8080/api/webhooks/sendgrid \
  -H 'Content-Type: application/json' \
  -d '[{"event":"delivered","sg_message_id":"abc.123"}]'
```

`SendGridSignatureVerifierTest` generates a real P-256 key pair and asserts every rejection path,
including a tampered body, a foreign key, a replay, and a timestamp edited to look fresh.
