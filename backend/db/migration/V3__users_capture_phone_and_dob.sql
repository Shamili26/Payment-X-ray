-- ============================================================================
-- Migration: capture all registration fields on the users table
-- ============================================================================
-- The application runs with spring.jpa.hibernate.ddl-auto=validate, so the
-- physical schema must match the User entity which now also persists:
--   * phone_number   -> registrant's phone number (+91 followed by 10 digits)
--   * date_of_birth  -> registrant's date of birth (yyyy-MM-dd)
--
-- This script is IDEMPOTENT and SAFE TO RE-RUN. Run it against the PostgreSQL
-- `paymentdb` database before starting the app. Run the WHOLE script at once
-- (or with auto-commit ON) so a single failure can't roll back the columns.
-- ============================================================================

-- 1. Add the new columns (nullable first so existing rows don't break).
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone_number  VARCHAR(20);
ALTER TABLE users ADD COLUMN IF NOT EXISTS date_of_birth DATE;

-- 2. Backfill existing users, THEN enforce NOT NULL.
--    Rows created before this change have no phone/DOB. Give them safe
--    placeholder values so the NOT NULL constraint can be applied. Adjust
--    these defaults to whatever is appropriate for your data.
UPDATE users SET phone_number  = '+910000000000' WHERE phone_number  IS NULL;
UPDATE users SET date_of_birth = DATE '1900-01-01' WHERE date_of_birth IS NULL;

-- 3. Enforce NOT NULL automatically once (and only once) every row has values,
--    so this script never aborts the surrounding transaction.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM users WHERE phone_number IS NULL OR date_of_birth IS NULL) THEN
        RAISE NOTICE 'Skipping NOT NULL on users.phone_number/date_of_birth: NULL rows still exist. Backfill them and re-run.';
    ELSE
        ALTER TABLE users ALTER COLUMN phone_number  SET NOT NULL;
        ALTER TABLE users ALTER COLUMN date_of_birth SET NOT NULL;
    END IF;
END $$;

