# Data retention

Requirement **#37**. UK GDPR's storage limitation principle: personal data may not be kept longer
than the purpose it was collected for needs it. Built September 2026; before that the platform
kept everything forever.

## How it is put together

There is no central "delete old things" class. Each module implements
`shared.RetentionSweeper` for the data it owns:

```java
public interface RetentionSweeper {
    String  dataset();               // "Gifting addresses"
    String  policy();                // the rule, in one sentence
    Outcome sweep(boolean dryRun);   // apply it, or count what would go
}
```

`foundation.internal.DataRetentionService` collects every implementation Spring registered and
runs each one through `RetentionRunner` **in its own transaction**. That isolation is the point: a
deadlock deleting email threads must not roll back the addresses already purged, nor — more
importantly — the evidence that they were.

A cutoff for a home address is not a cutoff for a login attempt, and the reasoning belongs beside
the data rather than in a list somewhere else. **Adding a table with personal data in it means
adding a sweeper.** Nothing enforces that automatically; it is written here so a reviewer can ask.

| Sweeper | Lives in |
|---|---|
| Gifting addresses | `gifting/internal/GiftingAddressRetention` |
| Unconfirmed waitlist | `marketing/internal/WaitlistRetention` |
| Email threads | `outreach/internal/EmailThreadRetention` |
| Audit trail | `foundation/internal/AuditLogRetention` |
| Login attempts, expired tokens | `foundation/internal/AuthDataRetention` |
| Dormant creators (reports only) | `creators/internal/CreatorRetentionReview` |

## The policies, and why those numbers

| Data | Kept | Reasoning |
|---|---|---|
| **Gifting addresses** | 90 days after delivered / returned / declined; 180 days after capture if never dispatched | The most sensitive data here. A creator gave a home address so a parcel could reach them; once it has arrived the purpose is spent. 90 days still covers a late return or a delivery dispute. Deleted, not anonymised — a partial address is still a home address. |
| **Unconfirmed waitlist sign-ups** | 30 days | Double opt-in was never completed, so there is no consent behind the address and no basis to keep it as a "maybe". Confirmed sign-ups are untouched. |
| **Outreach email threads** | 24 months | The most personal free text stored: creators write about rates, availability, sometimes their circumstances. Two years covers "what did we agree last season". The recipient row survives, so reply counts in #31 and #52 are unaffected — what goes is the message body, not the fact of it. |
| **Audit trail** | 24 months | The audit log (#36) is itself personal data: who changed what, when. Two years spans two campaign cycles. Personal fields are already redacted in the stored diff. |
| **Login attempts** | 90 days | Email plus IP is personal data even when the login failed. 90 days is the usual forensics window and covers a quarterly security review. |
| **Expired tokens** | 7 days past expiry | A short grace period so someone retrying a just-expired link still gets told why. Keeps the unique-digest indexes small too. |
| **Dormant creator records** | flagged at 36 months, **never auto-deleted** | See below. |
| **Consent records** | 6 years | Deliberately *not* swept. Proof of consent has to outlive the data it justifies. |
| **Coverage items** | kept | Campaign performance is a business record, and the personal data in it is a public handle. |

### Why dormant creators are only flagged

`CreatorRetentionReview` is the one sweeper that deletes nothing. It counts and lists creators
with no contact, gift, coverage or edit in 36 months, and the GDPR screen shows them for an admin
to action through the existing right-to-erasure path.

Storage limitation is a real obligation and a dormant creator record is real personal data. But a
cron job that quietly anonymises a client's contact list because a date passed is a worse failure
than holding it a month longer, and ICO guidance asks for a documented **review**, not necessarily
automatic destruction.

36 months rather than the 24 usual for marketing lists, because agency relationships are seasonal:
a creator used for one Christmas campaign is plausibly used two Christmases later, and a two-year
window would throw away working contacts.

## Evidence

Having a policy is not compliance. Being able to show it was applied is — that is the
accountability principle, and a log line is not evidence because it rotates away.

Every pass writes a row to `retention_runs`: dataset, the policy text **as it stood at the time**,
rows affected, whether it was a preview, the age of the oldest record still held, who triggered it,
and any error. The policy is copied rather than referenced so that changing a window later does
not rewrite history.

`oldest_remaining` is the tell-tale worth watching. A value that stops moving means a sweeper has
silently stopped matching anything.

The run log keeps 6 years — longer than any dataset it reports on.

## Operating it

Runs automatically at **03:00 Europe/London**, after the coverage digest window and well before
anyone is working. Not on startup: deleting personal data should not happen every time somebody
restarts the service to check a config change.

Settings → GDPR & data shows every policy, when it last ran and what it removed. The policy text
on that screen is generated from the sweepers, so the page cannot claim something the job does not
do — a written policy and a written job drift apart within a quarter, and the page is the one
people believe.

```
GET  /api/settings/retention          policies + last real pass of each
GET  /api/settings/retention/runs     the evidence log, newest first
POST /api/settings/retention/run      dryRun=true by default
```

`RETENTION_ENABLED=false` disables real sweeps but leaves previews working — useful on a restored
copy of production, where a real sweep would delete data that still exists in the live system.

Every window is an environment variable; see the `retention:` block in `application.yml`.
Changing one changes what the GDPR screen says, because both read the same source.
