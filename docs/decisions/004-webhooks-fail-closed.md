# 004 — An unverifiable webhook is rejected, not accepted

**Requirements** #30, #31 · **Decided** September 2026 · **Status** applied

## Context

`WebhookController` is `permitAll` — SendGrid cannot hold a session. Its entire signature check
was:

```java
return !"INVALID_SIG".equals(signature);
```

and the inbound reply endpoint had none at all. Anyone who knew the URL could mark a creator as
having replied, or file a spam report that suppresses that creator across every brand.

## Decision

Real ECDSA P-256 verification over the raw body, and **fail closed** in every ambiguous case.

| Situation | Result |
|---|---|
| Valid signature, fresh timestamp | accept |
| Signature does not verify | 403 |
| Verifies but timestamp outside 600s | 403 |
| Signature headers absent | 403 |
| **No public key configured** | **403** |

The last row is the decision. The obvious alternative — "if we have no key, accept everything so
it keeps working" — reproduces the original bug with extra steps.

Inbound Parse carries no signature from SendGrid at all, so it is protected by an unguessable path
segment compared in constant time, and stays closed until one is configured.

## Why

An unverified webhook is worse than a missing one: it looks like it works while accepting
anything. A deployment that forgets `SENDGRID_WEBHOOK_PUBLIC_KEY` should visibly stop receiving
events, with an error log naming the variable, rather than quietly accept forged ones.

The timestamp is checked only *after* the signature verifies. The timestamp is itself signed, so
an attacker cannot edit it to look fresh without breaking the signature — a test asserts exactly
that.

## Escape hatch

`SENDGRID_WEBHOOK_ALLOW_UNSIGNED=true` accepts unsigned calls for local development and logs a
warning on **every single one**, so it cannot be left on unnoticed. Set in the `local` profile
only.

## Consequence

The Inbound Parse URL changed from `/api/webhooks/sendgrid/inbound` to
`/api/webhooks/sendgrid/inbound/{token}`. The SendGrid setting must be updated at the same time as
the deploy or replies stop arriving.
