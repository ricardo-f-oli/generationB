-- Row-Level Security on every table in `public`. A Flyway callback, not a versioned migration:
-- it runs after every migrate, so a table added by any later migration is covered without anyone
-- having to remember it.
--
-- Supabase puts a REST API (PostgREST) in front of the `public` schema, reachable with the
-- project URL and the anon key. With RLS off, that API could read, edit and delete every row —
-- including `users`, with its password hashes. Supabase flagged it as rls_disabled_in_public and
-- sensitive_columns_exposed. The Data API has also been switched off in the dashboard; this is the
-- second lock, and the one that survives someone switching it back on.
--
-- This app never uses that API: it connects over JDBC as the role that owns these tables, and
-- PostgreSQL does not apply RLS to a table's owner (no FORCE ROW LEVEL SECURITY here, on purpose).
-- So enabling RLS with no policies changes nothing for the app and denies everything to the API.
--
-- Why not a V__ migration: that tried to ALTER flyway_schema_history while Flyway held a lock on
-- it from its other connection, and the deploy hung forever. By afterMigrate that lock is gone.
--
-- Only tables without RLS are touched. ALTER TABLE takes an exclusive lock, and on a normal boot
-- — every table already done — this must be a no-op rather than locking the whole schema while
-- the previous instance is still serving traffic.
DO $$
DECLARE
    t record;
BEGIN
    FOR t IN
        SELECT c.relname
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public'
          AND c.relkind IN ('r', 'p')
          AND NOT c.relrowsecurity
    LOOP
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', t.relname);
    END LOOP;
END
$$;
