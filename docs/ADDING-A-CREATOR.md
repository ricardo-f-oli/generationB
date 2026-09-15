# Adding a creator, and what data you get

The complete flow, and — more usefully — what each route actually gets you. There is no paid data
vendor: everything comes from Instagram's and YouTube's own free APIs, so **how a creator enters
the database changes what the platform can tell you about them.** That is the single most
important thing on this page.

> **Status, September 2026.** Built and working: adding creators by hand or CSV, *Refresh public
> profile* on a creator's page, the daily refresh for creators on active campaigns, auto-clipping
> and campaign-tag attribution. **Not built yet:** the account-connection flow (the *Request
> connection* button and the creator-facing OAuth screens described below) — it waits on Meta's
> app review. Until then demographics stay as entered by hand or carried over from the old provider.

---

## The short version

| | Their posts | Follower count | Audience demographics |
|---|---|---|---|
| **Instagram** | ✅ automatic | ✅ automatic | ⚠️ only if they connect |
| **YouTube** | ✅ automatic | ✅ automatic | ⚠️ only if they connect |
| **TikTok** | ⚠️ only if they connect | ⚠️ only if they connect | ❌ **never** |

"Automatic" means we can read it from a handle alone, with no involvement from the creator — for
Instagram, only if their account is a public **Business or Creator** account. Press **Refresh
public profile** on the creator's page, or let the 06:00 daily refresh pick up anyone on an active
campaign.

**TikTok demographics are not a setting we have not switched on.** TikTok's public API does not
expose audience age, gender or location to any commercial application, at any price. The creator
can see those figures in TikTok Studio on their own phone; there is no way for us to read them.

---

## Four ways a creator gets in

### 1. They apply through the sign-up form — *best*

`/register`, linked from the sidebar as **Creator sign-up**.

They fill in their details, answer the five permission questions, and — this is the part that
matters — **connect their accounts** at the end.

You get: everything. Posts, follower counts, and real audience demographics straight from
Instagram and YouTube.

This is the route to push people towards. Share the link in your outreach, put it in your email
signature, use it at events.

### 2. You add them by hand

**Creators → Add creator.** Name, handle, platform.

You get: their Instagram posts and follower count immediately, and the same for YouTube if you
put in their channel. No demographics, and no TikTok, until they connect.

Then **send them a connection request** (below) to fill in the gap.

### 3. Bulk import from a spreadsheet

**Creators → Import CSV.** Same as adding by hand, for a lot of people at once.

You get: the same as above — public data straight away, nothing personal until they connect. The
import screen will show you how many of the imported creators have no connection.

### 4. You find them through hashtag monitoring

**Coverage → Find brand mentions**.

You get: the posts, and the handle. Add them to the database from there and you are in the same
position as route 2.

---

## The campaign hashtag

Every campaign gets its own tag when you create it — something like **#katieloxsummersee7f3a**.
It appears on the campaign screen and goes into the brief.

**Ask every briefed creator to include it.** That one habit is what makes coverage tracking work
without costing anything.

Here is why. Instagram's hashtag search returns posts but **will not tell you who posted them**,
and it burns one of only 30 tag lookups a week shared across all your brands. So instead we read
your own creators' posts — which we already do, and which tells us exactly whose post it is —
and look for the campaign tag in the caption. Free, exact, and no allowance spent.

**Coverage → check campaign posts** does this for a whole campaign at once and tells you who has
posted and who has not.

The random tail on the tag is deliberate: **#summerseeding** would match strangers' posts and
credit your campaign with coverage it never earned.

A post that does not carry the tag is still logged as that creator's coverage — it just is not
attributed to the campaign, because most of what a creator posts has nothing to do with you.

---

> **A change worth knowing about.** Searching for *new* creators by describing them — "beauty
> creators in the north of England with a mostly female 25-34 audience" — is not available. No
> platform offers an index of people; Instagram indexes hashtags. Search runs on the creators
> already in our database, and new ones arrive through sign-up, import and hashtag monitoring.

---

## Asking a creator to connect

This is the step that turns a name in a list into a profile you can pitch to a client.

### How it works

1. Open the creator, press **Request connection**
2. They get an email with a single-use link — the same mechanism as the gifting address form, so
   it will look familiar
3. They tap it, choose which accounts to connect, and authorise
4. Demographics appear on their profile within a minute

The link expires, works once, and needs no login. You can see who has opened theirs and not
finished — which is a different problem from someone who never opened it, and needs a different
nudge.

### What to say to them

The honest pitch works better than a technical one:

> *Connecting your Instagram lets brands see your audience — the age range, where they are, the
> gender split. It's what gets you shortlisted for paid campaigns rather than just gifting. It
> takes about thirty seconds, we can't post anything, and you can disconnect whenever you like.*

Both halves are true and worth saying: we get read access only, and they can revoke it at any
time from their own Instagram settings or by asking us.

### What we can and cannot see

**Can:** follower demographics, their posts, engagement figures.

**Cannot:** post on their behalf, read their DMs, see who follows them individually, or see
anything about accounts they have not connected.

---

## Requirements a creator needs to meet

Some of these are theirs, not ours, and no amount of chasing changes them.

| Requirement | Why | If not met |
|---|---|---|
| Instagram **Business or Creator** account | Meta only exposes data for professional accounts | Personal accounts return nothing at all — not posts, not demographics |
| Roughly **100+ followers** | Meta suppresses small-audience breakdowns | Posts still work; demographics come back empty |
| Correct handle on file | It is the lookup key | Silent "no data" — check for a rename |

**The personal-account one catches people out.** If a creator's Instagram posts are not appearing
and everything looks right, that is almost always why. Switching to a Creator account is free and
takes them about a minute in their Instagram settings.

---

## Keeping the data fresh

**Posts** update whenever you clip coverage — **Coverage → Clip creator activity**, or the
scheduled sweep.

**Demographics** are re-read every 30 days for connected creators, automatically. There is a
**Refresh** button on the profile if you need it sooner.

**A connection can break.** Tokens expire, and creators change their account type or disconnect.
The profile shows the connection as inactive when that happens, and they need a fresh request.

---

## Two limits to plan around

### Hashtags: 30 unique tags per week, shared

Instagram allows **30 distinct hashtags per rolling 7 days across the whole platform** — not per
brand. Four brands monitoring three tags each, plus a couple of competitors, is most of the
allowance gone.

Re-using a tag you have already used this week is free. Adding a new one costs a slot. Settings
shows how many are left and when the next one frees up.

Practically: agree each brand's monitored tags rather than adding them ad hoc, and prefer a few
specific tags over many broad ones.

### YouTube: cheap unless you search

Reading a channel and its videos costs almost nothing against the daily quota. *Searching* YouTube
costs a hundred times more. Clipping a creator's uploads all day is fine; running broad searches
repeatedly is not.

---

## Where this leaves each brand

The realistic target for a creator you are actively working with:

- ✅ On the database with the correct handle
- ✅ Instagram connected — this is the one that matters most
- ✅ YouTube connected if they use it
- ⚠️ TikTok connected if they use it — gets their videos, never their audience
- ✅ The five permission questions answered

**Creators → filter by "Not connected"** is your chase list. The number on the dashboard is the
one to drive down.

---

## Troubleshooting

**"No posts are appearing for this creator."**
Personal Instagram account (most likely), a changed handle, or a private account.

**"Demographics are blank even though they connected."**
Their audience is below Meta's reporting minimum, or the connection expired. The profile will say
which.

**"Their TikTok is not showing anything."**
They have not connected TikTok. There is no other route — see the top of this page.

**"Mention monitoring has stopped finding things."**
Probably the weekly hashtag allowance. Check Settings; a slot frees up seven days after it was
first used.

**"A creator wants their data removed."**
Settings → GDPR & data, or the erasure action on their profile. That revokes the connection,
anonymises the record and adds them to the suppression list so they are not re-imported.

---

## Current status

Built and working: reading public Instagram and YouTube data, hashtag monitoring with the
allowance guard, the connection storage with encrypted tokens, and demographics from a connected
Instagram account.

Still to come: the creator-facing connect screens and the OAuth handshake itself — the plumbing
behind them is in place, but Meta requires business verification and app review before the buttons
can go live, which takes days to weeks and needs company documents. TikTok's Display API needs its
own review, and gets content only.

Until that clears, everything on the public side works today and the connection request flow is
the part waiting on Meta.
