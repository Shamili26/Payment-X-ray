-- ============================================================================
-- Migration: capture the 16-digit account number on the users table
-- ============================================================================
-- The application runs with spring.jpa.hibernate.ddl-auto=validate, so the
-- physical schema must match the User entity, which now also persists:
--   * account_number -> 16-digit account number captured at registration
--                       (also stored on the matching accounts row)
--
-- This script is IDEMPOTENT and SAFE TO RE-RUN. Run it against the PostgreSQL
-- `paymentdb` database before starting the app. Run the WHOLE script at once
-- (or with auto-commit ON).
-- ============================================================================

-- 1. Add the new column (nullable so pre-existing users are unaffected).
ALTER TABLE users ADD COLUMN IF NOT EXISTS account_number VARCHAR(16);

-- 2. (Optional) enforce uniqueness of the account number across users, matching
--    the unique account_number already present on the account table. Guarded so
--    re-running is safe.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_users_account_number'
    ) THEN
        ALTER TABLE users
            ADD CONSTRAINT uq_users_account_number UNIQUE (account_number);
    END IF;
END $$;

