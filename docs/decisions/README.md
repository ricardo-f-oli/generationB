# Decisions

Short records of choices that were not obvious, kept so a later reader can disagree with the
*reasoning* rather than guess at it.

Several of these were originally left open for the client to answer. In September 2026 the client
handed the decision back — "take creative freedom" — so these are the calls taken on their behalf,
with the trade-off written down. Any of them can be reversed; the point is that reversing one
should be a decision too.

| # | Decision | Requirement |
|---|---|---|
| [001](001-instagram-has-no-view-count.md) | Absent view counts are null, never zero | #10, #49 |
| [002](002-insurance-is-the-brands-product-liability.md) | "Insurance" means the brand's product liability, and it blocks dispatch | #40 |
| [003](003-five-granular-opt-in-questions.md) | Five opt-in questions, not one consent box | #20, #21 |
| [004](004-webhooks-fail-closed.md) | Unverifiable webhooks are rejected, not accepted | #30, #31 |
| [005](005-dormant-creators-are-flagged-not-deleted.md) | Retention flags dormant creators; a human deletes them | #37 |

## Still open

Not decided here, because they need something from outside this codebase rather than a judgement
call:

- **#28 domain authentication** — DKIM, SPF, DMARC and an inbound MX record on `btheagency.com`.
  DNS changes; no code can substitute.
- **#42 fulfilment export** — the export produces a workbook with the columns a courier manifest
  needs. Matching EC Group's own template is a one-line column mapping once they send a real file.
- **#50 per-brand report templates** — the engine and three seeded defaults exist. The brands'
  actual client-facing formats have not been shared, so the templates are ours rather than theirs.
