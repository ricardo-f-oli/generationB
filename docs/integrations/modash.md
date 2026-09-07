# Modash — the creator-data vendor

Connected September 2026. This is what unblocked requirements **#10, #11, #25 and #26**, which the
August gap analysis listed as waiting on a vendor contract, and materially improved **#23** and
**#55**.

## Switching it on

Two variables, deliberately separate:

```bash
INSIGHTS_PROVIDER=modash     # choose the live vendor over the mock
MODASH_API_KEY=...           # pay for it
```

Setting the key without the provider changes nothing, which is what you want while testing a key.
Setting the provider without a key logs an error at startup and every call returns empty — it does
not silently invent figures.

Without both, `MockCreatorInsightsProvider` answers instead and logs every response as
`[MOCK MODASH]`, so nobody mistakes generated numbers for a client's real coverage.

## Where the code is

| File | Does |
|---|---|
| `foundation/insights/ModashClient` | Transport only. Auth, rate limit, retry, credit guard. Returns raw `JsonNode`. |
| `creators/internal/ModashCreatorInsightsProvider` | Maps Modash's shapes onto ours. |
| `creators/internal/MockCreatorInsightsProvider` | The fallback. Same interface. |
| `creators/internal/CreatorEnrichmentService` | Demographics, discovery, competitor mentions, budget. |
| `creators/api/CreatorDiscoveryController` | `/api/creators/discovery/*` and `/{id}/enrich`. |
| `creators/CreatorInsightsProvider` | The seam. Both providers implement it. |
| `src/test/.../ModashMappingTest` | Locks the response shapes against real captures. |

The transport is in `foundation` and the mapping is in `creators` on purpose. `ModashClient` knows
nothing about creators, coverage or brands; it could be pointed at a different vendor without
touching either module.

## Endpoints used, and what they cost

Confirmed against the live API, not just the docs.

| Requirement | Endpoint | Meter |
|---|---|---|
| #10 auto-clip | `GET /raw/ig/user-feed`, `GET /raw/tiktok/user-feed` | 1 raw request |
| #11 hashtag / mention discovery | `GET /raw/ig/hashtag-feed`, `GET /raw/ig/user-tags-feed` | 1 raw request |
| #25 competitor mentions | same hashtag feed, pointed at a competitor | 1 raw request |
| #26 audience demographics | `GET /{platform}/profile/{handle}/report` | **1 credit** |
| #23 natural-language search | `POST /ai/{platform}/text-search` | 0.025 / profile |
| handle lookup | `GET /{platform}/users` | free |
| balance | `GET /user/info` | free |

Raw requests and discovery credits are **separate allowances**, metered separately by Modash and
reported separately by `/user/info`. That is why `ModashClient.Meter` exists: clipping a whole
campaign draws on raw requests and must not eat the report allowance.

## The traps

Three things here will produce plausible wrong numbers rather than errors. All three are covered
by tests; do not "simplify" them away.

### 1. `engagementRate` means two different things

| Endpoint | Format | Example |
|---|---|---|
| `/profile/{handle}/report` | **fraction** | `0.0011` = 0.11% |
| `/ai/{platform}/text-search` | **percentage** | `1.47` = 1.47% |

Running the second through the fraction conversion turns a 1.5% rate into 147%. This happened
during the build and was caught against the live API, not in review.

### 2. Instagram publishes no view count. Ever.

Verified on a brand account and a creator account, carousels and reels alike: `view_count` and
`play_count` come back null on every item of `/raw/ig/user-feed`.

So `views` on `coverage_items` is **nullable**, and null means "the platform does not tell us" —
never zero. A client reading "0 views" concludes nobody saw the post. The UI prints "Not tracked",
the CSV leaves the cell blank, and the Excel totals row sums only the rows that were measured.

Because there is no view count to divide by, Instagram engagement rate is computed against
**follower count** instead, which is the convention on that platform anyway. TikTok does supply
`stats.playCount`, so TikTok rows carry real views and a views-based rate.

### 3. Three endpoints, three sets of field names

| Concept | Report | AI search | User lookup |
|---|---|---|---|
| name | `profile.fullname` | `fullName` | `fullname` |
| followers | `profile.followers` | `followersCount` | `followers` |
| avatar | `profile.picture` | `profilePicture` | `picture` |

One mapper for all three silently zeroed every follower count on one screen. There are now two
mappers (`mapAiSearchProfiles`, `mapUserLookup`) plus the report mapper, and a test for each.

## The credit guard

The trial account carries 100 credits and 100 raw requests. A report costs a whole credit, so a
loop over a shortlist can empty it in one click. `ModashClient` therefore:

- spaces calls to stay inside the documented **2 requests/second**, and backs off on a 429,
  honouring `Retry-After`;
- refuses any metered call that would take the balance below a reserve (default 2 of each);
- decrements a cached balance between refreshes so a burst inside one TTL window still sees the
  cost of the calls before it;
- logs the status only on failure — response bodies carry creator names and contact details.

Enrichment adds two more guards: a creator refreshed within `insights.enrichment.ttl-days`
(default 30) is skipped, and a bulk refresh is capped at `max-batch` (default 25) however large a
limit is requested.

`GET /api/creators/discovery/status` returns the live balance. The screens read it before offering
anything that costs money.

## Provenance

Enrichment stamps `creators.insights_source = 'MODASH'` and `insights_refreshed_at`. The creator
screen shows both, because a measured figure and a figure somebody typed in 2024 look identical
otherwise.

A field the vendor did not answer for is **left alone**, not blanked. Overwriting hand-researched
data with a null is worse than a stale value.
