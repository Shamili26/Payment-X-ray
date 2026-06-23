-- ============================================================================
-- Migration: brute-force login lockout for users
-- ============================================================================
-- The application runs with spring.jpa.hibernate.ddl-auto=validate, so the
-- physical schema must match the User entity. This adds the two columns that
-- back the account-lockout feature:
--   failed_login_attempts  consecutive failed logins (reset to 0 on success)
--   locked_until           timestamp until which the account is locked, after
--                          app.security.lockout.max-attempts failures; NULL
--                          when the account is not locked.
-- AuthService increments the counter on a BadCredentials failure and sets
-- locked_until once the threshold is reached; User.isAccountNonLocked() returns
-- false while locked_until is in the future, so Spring Security rejects the
-- login with 403 (even with the correct password) until the window passes.
--
-- This script is IDEMPOTENT and SAFE TO RE-RUN. Run it against the PostgreSQL
-- `paymentdb` database before starting the app.
-- ============================================================================

ALTER TABLE public.users
    ADD COLUMN IF NOT EXISTS failed_login_attempts integer NOT NULL DEFAULT 0;

ALTER TABLE public.users
    ADD COLUMN IF NOT EXISTS locked_until timestamp without time zone;