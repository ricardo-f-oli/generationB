-- V27 seeded the 30-day-old follower snapshot with each creator's *current* count, so every
-- creator showed exactly zero growth. The metric was right; the demo data was flat.
--
-- This backdates the baseline by a per-creator amount derived from the creator's own id, so the
-- figures are stable across environments (no random()) but not uniform. Only the historical row
-- is touched — today's snapshot stays as the real current count.
--
-- Growth lands between roughly 2% and 9%, which is a believable month for a UK creator.

UPDATE creator_follower_snapshots s
SET followers_count = GREATEST(
        1,
        FLOOR(s.followers_count / (1 + 0.02 + ((('x' || SUBSTR(MD5(s.creator_id::text), 1, 4))::bit(16)::int % 70) / 1000.0)))::int
    )
WHERE s.captured_on < CURRENT_DATE
  AND EXISTS (
      SELECT 1 FROM creator_follower_snapshots later
      WHERE later.creator_id = s.creator_id
        AND later.captured_on > s.captured_on
        AND later.followers_count = s.followers_count
  );
