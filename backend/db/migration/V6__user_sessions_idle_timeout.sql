-- ============================================================================
-- Migration: idle (inactivity) timeout for user sessions
-- ============================================================================
-- The application runs with spring.jpa.hibernate.ddl-auto=validate, so the
-- physical schema must match the UserSession entity. This adds last_activity_at,
-- the timestamp of the most recent authenticated request on a session. The JWT
-- filter compares it against app.session.idle-timeout-minutes (default 15) and
-- deactivates any session idle longer than that window.
--
-- This script is IDEMPOTENT and SAFE TO RE-RUN. Run it against the PostgreSQL
-- `paymentdb` database before starting the app.
-- ============================================================================

ALTER TABLE public.user_sessions
    ADD COLUMN IF NOT EXISTS last_activity_at timestamp without time zone;

-- Backfill existing rows so the NOT NULL constraint can be applied. Created_at
-- is the best available proxy for the last time the session was known active.
UPDATE public.user_sessions
    SET last_activity_at = created_at
    WHERE last_activity_at IS NULL;

ALTER TABLE public.user_sessions
    ALTER COLUMN last_activity_at SET DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE public.user_sessions
    ALTER COLUMN last_activity_at SET NOT NULL;

-- Supports cleanup / queries that scan sessions by inactivity.
CREATE INDEX IF NOT EXISTS idx_sessions_last_activity
    ON public.user_sessions USING btree (last_activity_at);