-- Attributing a post to the creator who made it, without paying for a hashtag lookup.
--
-- THE PROBLEM
--
-- Instagram's hashtag search returns posts but no username. Meta will not tell you whose post it
-- is through that edge, so a mention found that way cannot be tied to a creator, a campaign or a
-- send list. It also costs one of only 30 unique hashtags per rolling 7 days, shared across every
-- brand, and it returns no like or comment counts.
--
-- THE WAY ROUND IT
--
-- Search the other way. Business Discovery is queried BY HANDLE, so every post it returns is
-- already attributed — we asked for that creator specifically. We are also already making that
-- call for auto-clipping. So scanning those captions for a campaign's hashtag costs nothing
-- extra, consumes none of the weekly allowance, and comes with the engagement figures that the
-- hashtag edge omits.
--
-- What it needs is something unambiguous to scan for, which is what this column is: one tag per
-- campaign, unique enough that an organic post will not collide with it. The creator gets it in
-- their brief; we match it against their own posts.
--
-- Hashtag search is still worth having, for genuinely unsolicited coverage from creators nobody
-- briefed. But it becomes the expensive fallback rather than the primary route.

ALTER TABLE campaigns
    ADD COLUMN IF NOT EXISTS tracking_hashtag VARCHAR(100);

COMMENT ON COLUMN campaigns.tracking_hashtag IS
    'The campaign tag a briefed creator is asked to post with. Matched against their own posts, '
    'read by handle, so attribution is exact and costs no hashtag allowance. Lower case, no #.';

-- Unique across the whole platform, not per brand. The point of the tag is that a match is
-- unambiguous, and two brands sharing one would break exactly that.
CREATE UNIQUE INDEX IF NOT EXISTS uq_campaign_tracking_hashtag
    ON campaigns(tracking_hashtag)
    WHERE tracking_hashtag IS NOT NULL;

-- Existing campaigns get one generated from their own id, so nothing is left unattributable.
--
-- Deliberately not derived from the campaign name: names get edited, and a tag that changes after
-- creators have been briefed silently stops matching the posts they already made. The id does not
-- change, so neither does the tag.
UPDATE campaigns
   SET tracking_hashtag = 'gb' || REPLACE(CAST(id AS VARCHAR), '-', '')
 WHERE tracking_hashtag IS NULL;

-- Belt and braces: an unattributable campaign is the thing this whole change exists to prevent.
ALTER TABLE campaigns
    ALTER COLUMN tracking_hashtag SET NOT NULL;

-- Which term actually matched, recorded on the coverage row.
--
-- "Why is this post in the log?" gets asked about every automatically clipped item, and the only
-- honest answer is the exact token that matched. Null means it matched nothing and is one of the
-- creator's ordinary posts.
ALTER TABLE coverage_items
    ADD COLUMN IF NOT EXISTS matched_term VARCHAR(150);
