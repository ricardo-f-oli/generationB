# 001 — An absent view count is null, never zero

**Requirements** #10, #49 · **Decided** September 2026 · **Status** applied

## Context

Auto-clipping pulls a creator's posts from Modash's raw feeds. Verified against the live API on
both a brand account and a creator account, carousels and reels alike: Instagram returns
`view_count: null` and `play_count: null` on **every** item. It does not publish the number.

The `coverage_items.views` column was `NOT NULL DEFAULT 0`.

## Decision

`views` is nullable. Null means "the platform does not publish this". It is not a synonym for
zero, and nothing may render it as one.

- UI prints "Not tracked"
- CSV export leaves the cell empty
- Excel export leaves the cell empty and the totals row sums only measured rows
- The digest email says "not tracked" per row
- Aggregates already used `COALESCE(SUM(views), 0)`, which skips nulls correctly

Rows already logged against the mock provider with a fabricated `0` were set to null by migration
`V36`, but only where `source IN ('AUTO_CLIP','MENTION')` — a zero somebody typed into the manual
form is a deliberate statement and stays.

## Why

A client reading "0 views" concludes the campaign was not seen. That is a different and much worse
statement than "Instagram does not tell us", and it is the exact class of bug the previous pass
fixed for impressions. Applying the same treatment to the column the vendor turned out not to fill
is consistency, not scope creep.

## Consequence

Instagram posts had no denominator for an engagement rate, which would have made every Instagram
row read `0%`. Engagement rate is therefore computed against **follower count** where views are
absent — which is the convention on Instagram anyway — and against views where the platform
supplies them, as TikTok does via `stats.playCount`.

Zero still means "could not be computed", which the reporting aggregates already read correctly:
they average `NULLIF(er, 0)`, so an uncomputed row does not drag the mean down.

## Reversing it

Don't. If a future vendor does supply Instagram view counts, they populate the column and null
simply stops appearing. Nothing needs to change.
