# 002 — "Insurance" means the brand's product liability, and it blocks dispatch

**Requirement** #40 · **Decided** September 2026 · **Status** applied

## Context

Requirement #40 read "legal disclaimer + insurance" and was the last item in the gap analysis
still marked *Shell*. Policy consent was stored; insurance was not modelled anywhere. It was
flagged as needing a decision from the client, who handed the decision back.

"Is the agency insured" has no single answer in influencer gifting. There are three risks with
three different owners:

| Risk | Carried by |
|---|---|
| The product injures someone | the **brand's** product liability cover |
| The agency gives bad advice | the agency's professional indemnity |
| Someone is hurt on the agency's premises | the agency's public liability |

## Decision

Model only the first, and make it a control rather than a note.

- `brands` gains `product_liability_insurer`, `product_liability_policy_number`,
  `product_liability_expires_on`, `product_liability_cover_gbp`, `insurance_notes`
- `GiftingService.createDispatches` **refuses the whole run** when the brand's cover is missing or
  expired
- `brands.gifting_disclaimer` holds the wording printed on the comp slip and shown on the address
  form, seeded with a sensible default per brand

The agency's own professional indemnity and public liability are a fixed fact about the business,
not per-brand data. They belong in the MSA and the privacy notice, not in a column nothing would
ever read.

## Why

Only product liability is per-campaign, and only product liability is checkable *before* an
action. An agency that posts a client's cosmetics to a stranger's home without confirming the
client carries cover is the exposure worth engineering against — a cosmetic that burns someone, a
candle that sets light to something.

Expiry is the case that matters. A lapsed policy looks exactly like a valid one until somebody
checks, which is precisely why "somebody checks" is not a control.

The run is blocked rather than individual creators skipped, because cover is a property of the
brand: if it is missing then no parcel on the run should go, and saying so once is clearer than
saying it forty times.

## The default disclaimer

> This is a gift, sent with no obligation to post. If you do share it, please mark the post as a
> gift so your audience knows. Please check the ingredients or materials list before use if you
> have any allergies. Any issue with the product itself is the responsibility of the brand that
> made it, and we will put you in touch.

The "no obligation" sentence is load-bearing. Under the CAP Code a gift only becomes an
advertisement the creator must disclose if there is an agreement to post. Saying plainly that
there is none keeps a genuine gift a genuine gift, and protects the creator from accidentally
breaching the rules. It is per brand because cosmetics need an allergy line that a tote bag does
not.

## Escape hatch

`GIFTING_REQUIRE_PRODUCT_LIABILITY=false` turns the block into nothing. The `local` profile sets
it, because seeded demo brands have no policy on file and blocking a fresh clone would look like a
bug rather than a control. Production keeps the default of `true` — otherwise the recorded policy
is decorative, which is the state the requirement was already in.
