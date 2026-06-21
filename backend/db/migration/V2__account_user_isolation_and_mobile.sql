-- ============================================================================
-- Migration: per-user account isolation + capture mobile number on the account
-- ============================================================================
-- The application runs with spring.jpa.hibernate.ddl-auto=validate, so the
-- physical schema must be updated to match the Account entity which now has:
--   * user_id        -> owning user (FK to users.user_id), enforces isolation
--   * mobile_number  -> mobile number captured at registration
--
-- This script is IDEMPOTENT and SAFE TO RE-RUN. Run it against the PostgreSQL
-- `paymentdb` database before starting the app.
--
-- IMPORTANT: run the WHOLE script in one go (or with auto-commit ON). Do not
-- run only the NOT NULL step on its own — see the note in section 4.
-- ============================================================================

-- 1. Add the new columns (nullable first so existing rows don't break).
--    This is the step that fixes "column user_id does not exist".
ALTER TABLE account ADD COLUMN IF NOT EXISTS user_id       BIGINT;
ALTER TABLE account ADD COLUMN IF NOT EXISTS mobile_number VARCHAR(20);

-- 2. Add the foreign key to users (guarded so re-running is safe).
--    A FK tolerates NULL user_id values, so this is safe to add before backfill.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_account_user'
          AND table_name      = 'account'
    ) THEN
        ALTER TABLE account
            ADD CONSTRAINT fk_account_user
            FOREIGN KEY (user_id) REFERENCES users (user_id);
    END IF;
END $$;

-- 3. Helpful index for the user-scoped lookups used by the service layer.
CREATE INDEX IF NOT EXISTS idx_account_user_id ON account (user_id);

-- 4. Backfill existing accounts to an owner, THEN enforce NOT NULL.
--    Legacy accounts created before this change have no owner. Assign them an
--    owner first; the NOT NULL constraint can only be applied once no NULLs
--    remain. Uncomment / adjust ONE of the options below for your data:
--
--    Option A — assign all orphan accounts to a specific user:
--        UPDATE account SET user_id = <OWNER_USER_ID> WHERE user_id IS NULL;
--
--    Option B (dev only) — remove orphan accounts and their payments:
--        DELETE FROM payment WHERE account_id IN (SELECT account_id FROM account WHERE user_id IS NULL);
--        DELETE FROM account WHERE user_id IS NULL;
--
--    The block below enforces NOT NULL automatically once (and only once) every
--    account row has an owner. Until then it just prints a notice instead of
--    failing, so this script never aborts the transaction.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM account WHERE user_id IS NULL) THEN
        RAISE NOTICE 'Skipping NOT NULL on account.user_id: orphan rows exist. Backfill them (section 4) and re-run.';
    ELSE
        ALTER TABLE account ALTER COLUMN user_id SET NOT NULL;
    END IF;
END $$;

