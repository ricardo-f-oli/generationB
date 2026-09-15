# Creator data straight from the platforms

Where every piece of creator data comes from. There is no paid vendor: Modash was removed in
September 2026. Meta and Google answer what they will for free, and
`PlatformApiCreatorInsightsProvider` routes each question to whichever can answer it.

For the client-facing version of this — how to add a creator and what you get — see
[../ADDING-A-CREATOR.md](../ADDING-A-CREATOR.md).

## Switching it on

Setting either credential selects this provider automatically:

```bash
META_ACCESS_TOKEN=...        # long-lived Page token
META_IG_USER_ID=...          # the agency's own IG Business account id
YOUTUBE_API_KEY=...          # plain API key, nothing else needed
INSIGHTS_TOKEN_ENCRYPTION_KEY=$(openssl rand -base64 32)
```

`INSIGHTS_PROVIDER` only overrides that — `platform` or `mock`. With no credential at all the app
runs on generated sample data and logs every call as `[MOCK INSIGHTS]`.

Startup logs which sources are live and which are not, because a half-configured integration
returns empty results that look identical to "this creator posts nothing".

## Instagram before Meta's approval

Everything stays free, so until Meta approves the app there is no automatic Instagram data:
follower counts are typed on the creator and posts are logged by hand on the coverage screen
(likes, comments, views). Engagement rate and reach are calculated from those figures exactly as
they are for clipped posts.

When the approval arrives, set `META_ACCESS_TOKEN` and `META_IG_USER_ID` in Render and redeploy.
Instagram lookups go through `foundation/insights/InstagramProfiles`, which maps every source onto
one `InstagramProfile` shape and one canonical post URL (`https://www.instagram.com/p/{code}/`), so a
post logged by hand is not logged again when auto-clipping finds it.

## Existing creators, without asking them

The agency's current roster does not have to connect anything to stay up to date. For any creator
whose primary platform is Instagram and whose account is a public **Business or Creator** account,
Business Discovery returns follower count, bio and recent posts by handle alone. For any creator
with a YouTube handle, the Data API returns subscriber count and channel description.

- **Refresh public profile** on a creator's page calls `POST /api/creators/{id}/enrich`.
- `POST /api/creators/profile-refresh?limit=N` refreshes the N stalest (admin and director).
- Every day at 06:00 Europe/London, creators with a card on an **ACTIVE** campaign are refreshed
  (`insights.enrichment.cron`, batch `insights.enrichment.max-batch`), and a follower snapshot is
  recorded so growth reporting has two points to compare.

`creators.insights_source` records what answered: `INSTAGRAM_PUBLIC`, `YOUTUBE_PUBLIC`,
`INSTAGRAM_CONNECTED`, or `LEGACY` for figures carried over from Modash. A lookup that returns
nothing never blanks what is on file.

What this **cannot** give an unconnected creator: audience demographics (UK %, age, gender), anything
from a personal Instagram account, and anything at all from TikTok. Those are platform rules, not
missing code. Scraping (instaloader, TikTokApi and similar) breaches the platforms' terms and gets
accounts banned, so it is deliberately not used.

## What answers what

| Question | Instagram | TikTok | YouTube |
|---|---|---|---|
| A creator's posts (#10) | Business Discovery, **no permission needed** | connected only | Data API, public |
| Hashtag / mentions (#11, #25) | Hashtag Search, **30 tags / 7 days** | — | search, 100 units |
| Audience demographics (#26) | connected only | **never** | connected only |
| Creator search (#23) | — | — | keyword only |

### Business Discovery is the useful one

Given any public **professional** account's handle you get follower count, bio and recent posts
with likes, comments and permalinks — without that creator's involvement. That is what makes
auto-clipping work for the whole roster rather than only the connected part.

It does not see personal accounts. A creator whose posts never appear has almost certainly not
switched to a Creator account, and no amount of retrying changes that.

### Hashtags are the scarce resource

**30 unique tags per rolling 7 days, per app** — shared across every brand, not per brand. That is
the binding constraint on mention discovery, so `HashtagBudget` treats it exactly as the old
a metered allowance: tracked in the database (the window is seven days; a restart must
not appear to reset it), reserved against, and refused rather than silently overspent.

Re-using a tag already inside the window is free, which is why the resolved id is cached. The same
table is both the ledger and the cache.

### Demographics need the creator

Instagram and YouTube report a creator's own audience to an app that creator has authorised.
The platform's own figures rather than a model, and consented to explicitly by the
person it describes, which is a cleaner GDPR position than buying a profile from a broker.

Meta suppresses breakdowns below its reporting minimum, so a small account legitimately returns
nothing. That is reported as "not available", never as zero.

## The two genuine losses

**TikTok demographics.** Not a permissions problem and not a budget one. TikTok's Research API is
restricted to academic and non-profit institutions — their own FAQ says commercial users are
ineligible — and the Display API, which any approved app can use once a creator connects, returns
videos and follower counts and nothing else. There is no endpoint for audience age, gender or
location at any tier a commercial agency can reach. The connection model supports TikTok so
content works; nothing will ever write demographics from it.

**Creator search by description.** No platform offers a creator index. Meta indexes hashtags, not
people, so search runs against our own database. `searchCreators` returns empty and logs why, rather than returning nothing
and letting the screen imply nobody matched.

## Where the code is

| File | Does |
|---|---|
| `foundation/insights/MetaGraphClient` | Business Discovery, Hashtag Search, Insights |
| `foundation/insights/YouTubeDataClient` | channels, uploads, video stats, search |
| `foundation/insights/HashtagBudget` | the 30-per-7-days allowance |
| `foundation/insights/TokenCipher` | AES-256-GCM for creators' OAuth tokens |
| `creators/internal/PlatformApiCreatorInsightsProvider` | routing and mapping |
| `creators/internal/CreatorPlatformConnection` | who has connected what |
| `creators/internal/InsightsProviderCondition` | platform APIs or mock |
| `creators/internal/CreatorEnrichmentService` | refreshing existing creators from public profiles |
| `creators/internal/CreatorProfileRefreshScheduler` | the daily refresh for active campaigns |

## Tokens

Creators' OAuth tokens are encrypted at rest, not hashed — they are live credentials for someone
else's account and have to be usable. AES-256-GCM, fresh nonce per encryption, key from
`INSIGHTS_TOKEN_ENCRYPTION_KEY`.

Without a key, connections are refused outright. Falling back to plaintext so the feature kept
working would put third-party credentials in a database column while everything looked fine.

Rotating the key without re-encrypting makes existing connections unreadable and every creator has
to reconnect. The decrypt failure says so explicitly rather than looking like an outage.

## Setup that is not code

- **Meta**: an app, **Business Verification** (company documents, days to weeks), App Review for
  the insights scopes, and a Facebook Page linked to an Instagram Business account. Business
  Discovery and Hashtag Search are both made *as* that account, which is why `META_IG_USER_ID` is
  required alongside the token.
- **YouTube**: enable Data API v3 on a Google Cloud project, create an API key. Minutes.
- **TikTok**: a developer app and Login Kit review. Content only.

## Still to build

The creator-facing connect screens and the OAuth handshake. The storage, consent model, request
tokens and the demographics read are all in place; what is missing is the redirect flow, and that
cannot be tested until Meta's app review clears.
