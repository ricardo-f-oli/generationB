# 003 — Five opt-in questions, not one consent box

**Requirements** #20, #21 · **Decided** September 2026 · **Status** applied

## Context

The public registration form had a single checkbox: *"I agree to B. The Agency storing my details
in line with their privacy policy."* The gap analysis noted the five opt-in questions "still need
confirming". Worse, the server recorded `DATA_STORAGE` **and** `MARKETING_EMAIL` consent
unconditionally, whatever the creator had ticked.

## Decision

Five questions, one per genuine processing purpose. Only the first is required.

| # | Consent type | Question | Required |
|---|---|---|---|
| 1 | `DATA_STORAGE` | Keep my details on file so Generation B can consider me for campaigns. | yes |
| 2 | `MARKETING_EMAIL` | Email me about paid campaigns and collaborations that suit my content. | no |
| 3 | `GIFTING_ADDRESS` | Send me products to try. I understand my address is shared with the fulfilment partner who posts the parcel. | no |
| 4 | `BRAND_SHARING` | Share my profile, audience figures and rates with the client brands Generation B works with. | no |
| 5 | `CONTENT_REUSE` | Use posts I have published in campaign reports and in Generation B's own marketing. | no |

Each answer is stored twice: as current state on `creators.consent_*`, so a screen or a send check
can read it without aggregating; and as an evidence row in `consent_records` with a timestamp,
source and IP.

**Refusals are recorded too.** "Said no on 4 March" is a materially different fact from "nothing on
file", and only the first can be defended.

## Why five, and why those five

UK GDPR requires consent to be specific and granular. One tick covering storage, marketing,
address sharing and content reuse is not freely given consent to any of them, and it is worthless
as evidence if a creator later says they never agreed to a client brand seeing their rates.

3 and 4 are separated deliberately: they are the two purposes that involve a **third party**
seeing personal data, and the two a creator is most likely to want to answer differently.

Only the first is mandatory because it is the only one without which there is nothing to store at
all. Making any of the others a condition of joining would make that consent invalid.

## Consequence — it has to bite

`CreatorLookupAdapter.isSuppressed` now returns true when `consent_marketing_email` is false. A
creator who did not tick question 2 cannot be added to an outreach list, at the same place an
unsubscribe is enforced (#21). Otherwise the question on the form is decoration.

## Existing creators

Migration `V38` sets `consent_marketing_email` and `consent_gifting_address` to true for already
approved creators, because the old single box did cover storage and being contacted — that is what
the old form said.

It leaves `consent_brand_sharing` and `consent_content_reuse` **false**. The old wording never
mentioned sharing profiles with client brands or reusing content, so those must be asked again
rather than assumed. Assuming consent nobody gave is the failure this whole change is about.
