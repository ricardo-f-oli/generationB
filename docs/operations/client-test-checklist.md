# Before the client tests it

Everything below is a configuration step, not development work. Ordered by what stops the app
starting, then by what silently does nothing if you skip it.

## 1. Will not start without these

```bash
SPRING_DATASOURCE_URL=...        # Neon connection string
SPRING_DATASOURCE_USERNAME=...
SPRING_DATASOURCE_PASSWORD=...
JWT_SECRET=...                   # 256-bit minimum; refuses to boot otherwise
FRONTEND_URL=https://...         # the deployed frontend
CORS_ALLOWED_ORIGINS=https://... # same, no trailing slash
```

Deliberate: production must state its own origins and secret rather than inherit a default that
would work everywhere.

## 2. Two settings that will surprise you

### Gifting dispatch is blocked until insurance is on file

`GIFTING_REQUIRE_PRODUCT_LIABILITY` defaults to **true** in production. Requirement #40: the
platform refuses to post a brand's product to a creator's home unless that brand's product
liability cover is recorded and in date.

If nobody has entered a policy, **"Create dispatches" will refuse with a message naming the
brand.** That is the control working, not a bug. Two options for a test run:

- add a policy per brand under Settings — this is what you want eventually; or
- set `GIFTING_REQUIRE_PRODUCT_LIABILITY=false` for the test, and remember to remove it.

### Webhooks reject everything until the key is set

`SENDGRID_WEBHOOK_PUBLIC_KEY` has no default and the endpoint **fails closed** — every delivery
event gets a 403 with an error in the log naming the variable. Requirement #30: an unverified
webhook is worse than a missing one, because it looks like it works.

Get the key from SendGrid → Settings → Mail Settings → Event Webhook, after switching on *Signed
Event Webhook Requests*.

## 3. Integrations, in the order they matter

| Variable | Without it |
|---|---|
| `META_ACCESS_TOKEN` + `META_IG_USER_ID`, and/or `YOUTUBE_API_KEY` | Profile refresh and auto-clipping run on the mock, logged as `[MOCK INSIGHTS]`. All free — no paid data vendor |
| `GROQ_API_KEY` | AI drafting returns a written fallback rather than generated copy |
| `OUTREACH_SENDGRID_API_KEY` | Nothing is emailed |
| `SENDGRID_WEBHOOK_PUBLIC_KEY` | Delivery events rejected (see above) |
| `OUTREACH_SENDGRID_INBOUND_TOKEN` | Reply capture endpoint stays closed |
| `STORAGE_*` (R2/S3) | Card attachments fail — prod must not use local disk |

### The Inbound Parse URL changed

It is now `/api/webhooks/sendgrid/inbound/{token}`, where the token is
`OUTREACH_SENDGRID_INBOUND_TOKEN`. Generate one with `openssl rand -hex 32` and update the host
in SendGrid at the same time as the deploy, or replies stop arriving.

## 4. What will not work properly in a test, and why

- **Outreach email deliverability.** Requirement #28 needs DKIM, SPF and DMARC on the sending
  domain. Mail sends without them; it lands in spam more often than it should. Test the workflow,
  not the inbox placement.
- **Creator data is free but partial.** Instagram only answers for public Business/Creator
  accounts, TikTok has no public API at all, and audience demographics (UK %, age, gender) only
  arrive for creators who connect their account. Existing figures carried over from the old data
  provider are labelled as such on the creator page.
- **The fulfilment export** produces a workbook with the columns a courier manifest needs, but not
  EC Group's own template — that needs one real file from them.
- **Report templates** are ours rather than each brand's, for the same reason.

## 5. Sensible order to walk through it

1. Sign in, look at the dashboard **on a phone** — that is new and worth seeing first.
2. Creators → open a creator with a YouTube handle → *Refresh public profile*, then *Clip recent
   posts*. Subscribers, engagement rate and their latest videos (views, reach, engagement) arrive
   from YouTube. Instagram data is entered by hand until Meta approves the app.
3. Campaigns → Brief builder: write a brief, tick some clauses under Terms, download the PDF.
4. Coverage → *Log a post* for an Instagram creator, with the campaign and a caption carrying the
   campaign hashtag: reach comes from the creator's followers, engagement rate is calculated.
   *Check campaign posts* does the same automatically for YouTube creators on the campaign board.
5. Settings → GDPR & data: read the retention policies, press *Preview what is due*. It changes
   nothing.
6. Gifting: this is where the insurance control bites. Either add a policy or flip the flag.
