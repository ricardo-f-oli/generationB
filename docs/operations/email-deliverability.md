# Email deliverability

Requirement #28. Everything the application can do is done; what remains is DNS, and there is a
blocker in front of it.

## Check it yourself

Settings → `GET /api/settings/email/dns` (admin or director) resolves the records live and grades
each one. It is a real DNS lookup, not an echo of configuration — the failure mode here is silent,
because the app sends happily whether or not the domain is authenticated.

## The blocker: the sending domain is not ours

As of 7 September 2026, `btheagency.com` is **parked for sale on HugeDomains**. Three
independent confirmations:

```
$ nslookup -type=CNAME random-nonexistent-name.btheagency.com
random-nonexistent-name.btheagency.com  canonical name = traff-https.hugedomains.com
```

A name nobody created still resolves, so there is a wildcard DNS record — the hallmark of a
parking page.

```
$ nslookup -type=MX btheagency.com
(no answer)
```

No mail exchanger. The domain cannot receive email at all.

```
SPF on btheagency.com:  v=spf1 -all
```

`-all` with no `include:` is the record a parking service publishes to say **"no server on earth
is authorised to send mail as this domain"**.

That last one matters more than it looks. Unauthenticated mail lands in spam. Mail from a domain
publishing `v=spf1 -all` gets **rejected outright** by any receiver that honours SPF. Outreach
would not be going to junk; it would be bouncing.

The DKIM lookups appear to resolve, but only because the wildcard answers everything. The checker
grades them WARN rather than PASS for exactly that reason — "resolves, but not to SendGrid".

**Nothing can be configured until the agency controls a sending domain.** You cannot add a DKIM
record to a domain you do not own.

## Until then: the user sends it

Outreach is not blocked in the meantime — it just does not go through the platform.

Outreach composer → **Prepare emails to send**. The platform does everything it is actually good
at: the AI draft, the merge tokens resolved against real data, and the opt-out check. It then
hands you each finished email with a *Copy* button and an *Open in mail app* link, and you send
them from an address that already works.

Two details worth knowing:

- **Nothing is marked as sent by that screen.** The platform has no way of knowing whether you
  actually pressed send in your mail client, and a status reading SENT when nothing left the
  building is worse than no status. Tick off what you sent and press *Mark as sent* — that writes
  send history, so a hand-sent email still counts towards the duplicate flag and the coverage
  reconciliation.
- **Opted-out creators have their address withheld, not just flagged.** It is absent from the
  response entirely, with the reason shown in its place. Otherwise the whole opt-out enforcement
  would be one copy-paste away from being bypassed.

*Send via platform* is still there, greyed back to a secondary action. Today it produces bounces.

## Once there is a real domain

Set these first, so the app stops defaulting to the parked one:

```bash
OUTREACH_FROM_ADDRESS=noreply@<the-real-domain>
OUTREACH_REPLY_DOMAIN=reply.<the-real-domain>
```

Then four records. The two DKIM values are account-specific and come from SendGrid; the rest are
standard and written out in full here.

### 1. DKIM — from SendGrid

SendGrid → Settings → Sender Authentication → Authenticate Your Domain. It issues two CNAMEs:

```
s1._domainkey.<domain>   CNAME   s1.domainkey.uNNNNNN.wlNNN.sendgrid.net
s2._domainkey.<domain>   CNAME   s2.domainkey.uNNNNNN.wlNNN.sendgrid.net
```

Two, not one: SendGrid's automated security rotates between them. With only one in place, half
the mail fails DKIM — which is why the checker treats "1 of 2" as a warning rather than progress.

### 2. SPF

```
<domain>   TXT   "v=spf1 include:sendgrid.net ~all"
```

`~all` (softfail), not `-all`, while you are bedding it in. If the domain already has an SPF
record for another service, **merge the includes into the single existing record**. A domain
publishing two SPF records is a permanent error under RFC 7208 and every check fails — the
checker looks for this specifically, because it is what happens when two people each add one.

### 3. DMARC

```
_dmarc.<domain>   TXT   "v=DMARC1; p=none; rua=mailto:dmarc@<domain>"
```

Start at `p=none`: it rejects nothing and only sends you reports. Read them for a fortnight, then
move to `p=quarantine` once you can see that legitimate mail is passing.

Gmail and Yahoo have required DMARC from bulk senders since February 2024, so this is not
optional polish — its absence is a deliverability problem by itself.

### 4. Inbound MX — for reply capture (#30)

```
reply.<domain>   MX   10 mx.sendgrid.net
```

On the **subdomain**, not the apex, so it does not interfere with the agency's normal mail. Then
point SendGrid's Inbound Parse at that host with the URL:

```
https://<api-host>/api/webhooks/sendgrid/inbound/<OUTREACH_SENDGRID_INBOUND_TOKEN>
```

## Verifying

Re-run the DNS check. DNS caches, so allow up to an hour — and note that a `UNKNOWN` result means
the resolver did not answer, which says nothing about whether the record exists. It is not the
same as `FAIL`.

Once every row is PASS, send a test to a Gmail address and use *Show original*: it should read
`SPF: PASS`, `DKIM: PASS`, `DMARC: PASS`.
