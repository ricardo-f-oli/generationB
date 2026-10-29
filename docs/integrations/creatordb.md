# CreatorDB

The paid creator-data vendor, adopted on 2026-10-23 so that one source covers the whole workflow
on Instagram, TikTok and YouTube: discovery, profiles, recent posts and audience demographics.
The free platform APIs ([platform-apis.md](platform-apis.md)) stay behind it as a fallback.

API reference: <https://docs.creatordb.app/api-v3/introduction>

## Switching it on

Set one variable on the backend (Render → the service → Environment):

```
CREATORDB_API_KEY=<key from the CreatorDB dashboard>
```

That alone selects CreatorDB; nothing else needs changing. Keep `META_ACCESS_TOKEN` /
`META_IG_USER_ID` / `YOUTUBE_API_KEY` set if you have them — they become the fallback, and Meta's
Hashtag Search is still what finds unsolicited brand mentions.

The startup log confirms it:

```
Creator data is coming from CreatorDB. Fallback for what it cannot answer: the platform APIs
```

To hold the key without using it, set `INSIGHTS_PROVIDER=platform` (or `mock`).

Never commit the key. It goes in the host's environment, or a local, untracked env file.

## What comes from where

| Feature | CreatorDB call | Credits | When CreatorDB has nothing |
|---|---|---|---|
| Profile refresh: followers, bio, engagement rate | `profile` + `performance` | ~5 | Instagram Business Discovery / YouTube Data API |
| Profile refresh: UK %, age band, gender split | `audience` | 10 | The creator's connected Instagram |
| Clip recent posts | `content-detail`, per platform the creator has a handle on | 3 each | The platform APIs |
| Creator search | `search`, hashtag filter, no per-result enrichment | 1 | Nothing |
| Brand mention / hashtag search | not offered by CreatorDB | — | Instagram Hashtag Search, always |

The source is shown on the creator profile as *"Profile and audience from CreatorDB"* and stored as
`insights_source = 'CREATORDB'`.

### Which handle is looked up

- **Instagram:** the main handle, only when the primary platform is Instagram. There is no
  separate Instagram handle column, and a TikTok handle looked up on Instagram can belong to
  someone else.
- **TikTok:** the TikTok handle, or the main handle when TikTok is primary.
- **YouTube:** the YouTube handle (or a `UC…` channel id), or the main handle when YouTube is primary.

### Engagement rate

The same definitions the profile screen states: Instagram (likes + comments) ÷ followers, YouTube
(likes + comments) ÷ views. TikTok uses CreatorDB's (likes + comments + shares) ÷ views.

## Keeping the credit spend down

There is no sandbox: every call, including during testing, spends real credits.

| Setting | Default | Why |
|---|---|---|
| `CREATORDB_TTL_HOURS` | 72 | A creator refreshed within this window is skipped by the daily job and bulk refresh (the profile button always refreshes). |
| Demographics | on demand | Bought only on a manual refresh or when the creator has none yet, never by the daily job for a creator who already has them. |
| `CREATORDB_POST_CACHE_MINUTES` | 60 | Clipping the same creator twice within an hour costs once. |
| `CREATORDB_MIN_CREDITS` | 100 | Calls stop when the balance CreatorDB reports reaches this, so a background job cannot drain the account. |

Rough costs:

- Manual "Refresh public profile" click: ~15 credits.
- Daily job: at most 25 creators × ~5 = 125 credits a day, and usually far fewer.
- "Check who has posted" on a campaign: 3 credits per creator per platform, then cached for an hour.

The balance left is in the CreatorDB dashboard, and is logged at debug level after every call
(`com.generationb.foundation.insights.CreatorDbClient`).

## Errors you may see in the logs

| Log | Meaning |
|---|---|
| `CreatorDB has no record for instagram/profile (NOT_FOUND)` | CreatorDB has not indexed the creator. Free. The free sources are tried next. |
| `... (UNAUTHORIZED) — check CREATORDB_API_KEY` | Wrong or revoked key. |
| `... (INSUFFICIENT_CREDITS)` | Balance spent; top up or wait for the plan refresh. |
| `... (KEY_SPEND_THRESHOLD_EXCEEDED)` | The key's own spend limit in the dashboard was reached. |
| `CreatorDB has N credits left, at or below the reserve` | The local reserve stopped calls; lower `CREATORDB_MIN_CREDITS` or top up. |

## Discovery (Creators → Discover)

`POST /api/creators/discovery/search` — platform, hashtags/keywords, creator country (GB or GBR),
follower range, offset. 1 credit per page of 25, sorted by size. Results already on the database
link to their profile; "Add to database" creates the creator with handle, platform and follower
count, and the profile refresh fills in the rest.

## Audience report (creator profile)

`GET /api/creators/{id}/audience-report` returns stored reports — free. `POST` (optionally
`?platform=TIKTOK`) buys a fresh one for 10 credits and stores it in `creator_audience_reports`
(raw CreatorDB JSON, one row per creator and platform). A profile refresh that buys demographics
stores the report too, so it is never bought twice. Anonymising a creator deletes their reports.

## Creators CreatorDB has not indexed

A lookup for an unknown creator costs nothing (404). The refresh and the audience report then
submit the creator for indexing (`/{platform}/submitCreators`, free) and say so; refreshing again
a day or two later picks them up.

## Known quirks (seen against the live API)

- YouTube handles come back from search as `@name`; they are stored without the `@`.
- Instagram posts whose owner hides like counts come back with `likes: 0`, not null.
- The Instagram engagement rate is (likes + comments) ÷ followers, so reels-only accounts read
  low (e.g. 0.06% for a 249K account averaging 152 interactions); CreatorDB's per-view reel rate
  for the same account is 0.85%.
