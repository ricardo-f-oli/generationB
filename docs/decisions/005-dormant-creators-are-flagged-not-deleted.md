# 005 — Retention flags dormant creators; a human deletes them

**Requirement** #37 · **Decided** September 2026 · **Status** applied

## Context

Storage limitation says personal data may not be kept longer than its purpose needs. A creator
record nobody has touched in years is real personal data with no live purpose.

Every other sweeper in the retention job deletes automatically. This one does not.

## Decision

`CreatorRetentionReview` counts and lists creators with no contact, gift, coverage or edit in
**36 months**. It deletes nothing. The GDPR screen shows the list; an admin actions it through the
existing right-to-erasure path, which anonymises the record and leaves a suppression entry so they
are never re-imported.

## Why not automatic

A cron job that quietly anonymises a client's contact list because a date passed is a worse
failure than holding the data a month longer. The damage is irreversible, invisible until someone
goes looking for a creator who is gone, and lands on the client rather than the agency.

ICO guidance asks for a documented **review** of whether data is still needed, not necessarily
automatic destruction. A list that an admin works through, with the decision recorded in the audit
trail, satisfies that and is defensible.

The other sweepers are automatic because their data has an objectively spent purpose — a delivered
parcel's address, an unconfirmed opt-in, a login attempt from last year. "This creator is no longer
worth keeping" is a judgement, not a fact.

## Why 36 months

The usual window for a marketing list is 24. Agency relationships are seasonal: a creator used for
one Christmas campaign is plausibly used two Christmases later. A two-year window would throw away
working contacts and the team would route around the feature.

## Reversing it

If the client later wants automatic anonymisation, `sweep()` already has the query and the cutoff.
Making it act is a few lines — but read this file first, and get it in writing.
