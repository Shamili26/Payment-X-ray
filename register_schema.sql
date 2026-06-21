-- =============================================================
--  PAYMENT APP — REGISTER PAGE EXTENDED SCHEMA
--  Database  : PostgreSQL 12+
--  Purpose   : Additional tables/columns for the Register page
--              fields beyond the base users_auth_schema.sql
--  Run AFTER : users_auth_schema.sql
--  How to use: psql -U <user> -d paymentdb -f register_schema.sql
-- =============================================================

-- =============================================================
-- 1. EXTEND TABLE: users  (new columns for Register page)
-- =============================================================
-- phone_number  — collected during sign-up, must be E.164
-- date_of_birth — age verification / KYC compliance
-- preferred_currency — ISO 4217 code chosen at registration

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS phone_number       VARCHAR(20),
    ADD COLUMN IF NOT EXISTS date_of_birth      DATE,
    ADD COLUMN IF NOT EXISTS preferred_currency CHAR(3)
        CONSTRAINT chk_currency
        CHECK (preferred_currency ~ '^[A-Z]{3}$');

-- Unique index: one account per phone number
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_phone
    ON users(phone_number)
    WHERE phone_number IS NOT NULL;

-- =============================================================
-- 2. TABLE: user_profiles  (extended profile data, 1-to-1)
-- =============================================================
-- Keeps the core users table lean while storing optional
-- KYC / profile enrichment data.

CREATE TABLE IF NOT EXISTS user_profiles (
    profile_id          BIGSERIAL     PRIMARY KEY,
    user_id             BIGINT        NOT NULL UNIQUE
                            REFERENCES users(user_id) ON DELETE CASCADE,

    -- Address (collected post-registration or during KYC)
    address_line1       VARCHAR(100),
    address_line2       VARCHAR(100),
    city                VARCHAR(80),
    state_province      VARCHAR(80),
    postal_code         VARCHAR(20),
    country_code        CHAR(2)
        CONSTRAINT chk_country
        CHECK (country_code ~ '^[A-Z]{2}$'),          -- ISO 3166-1 alpha-2

    -- KYC status
    kyc_status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
                            CONSTRAINT chk_kyc_status
                            CHECK (kyc_status IN ('PENDING','SUBMITTED','VERIFIED','REJECTED')),
    kyc_verified_at     TIMESTAMP,

    -- Profile photo (stored as external URL / S3 key, not binary)
    avatar_url          VARCHAR(500),

    -- Timestamps
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_profiles_user    ON user_profiles(user_id);
CREATE INDEX IF NOT EXISTS idx_profiles_kyc     ON user_profiles(kyc_status);
CREATE INDEX IF NOT EXISTS idx_profiles_country ON user_profiles(country_code);

-- =============================================================
-- 3. TABLE: user_preferences  (notification & UI settings)
-- =============================================================
CREATE TABLE IF NOT EXISTS user_preferences (
    preference_id           BIGSERIAL   PRIMARY KEY,
    user_id                 BIGINT      NOT NULL UNIQUE
                                REFERENCES users(user_id) ON DELETE CASCADE,

    -- Notifications
    email_notifications     BOOLEAN     NOT NULL DEFAULT TRUE,
    sms_notifications       BOOLEAN     NOT NULL DEFAULT FALSE,
    push_notifications      BOOLEAN     NOT NULL DEFAULT TRUE,

    -- Security
    two_factor_enabled      BOOLEAN     NOT NULL DEFAULT FALSE,
    two_factor_method       VARCHAR(10)
        CONSTRAINT chk_2fa_method
        CHECK (two_factor_method IN ('TOTP','SMS','EMAIL') OR two_factor_method IS NULL),

    -- Localisation (defaults to the preferred_currency/timezone at register time)
    timezone                VARCHAR(50) NOT NULL DEFAULT 'UTC',
    locale                  VARCHAR(10) NOT NULL DEFAULT 'en-US',   -- BCP-47

    created_at              TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_prefs_user ON user_preferences(user_id);

-- =============================================================
-- 4. TABLE: email_verifications
-- =============================================================
-- One-time tokens sent to the user's email after registration
-- to confirm the address is valid.

CREATE TABLE IF NOT EXISTS email_verifications (
    verification_id     BIGSERIAL     PRIMARY KEY,
    user_id             BIGINT        NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    token               VARCHAR(128)  NOT NULL UNIQUE,   -- UUID or secure random hex
    is_used             BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at          TIMESTAMP     NOT NULL DEFAULT (CURRENT_TIMESTAMP + INTERVAL '24 hours')
);

CREATE INDEX IF NOT EXISTS idx_email_verif_user  ON email_verifications(user_id);
CREATE INDEX IF NOT EXISTS idx_email_verif_token ON email_verifications(token);

-- =============================================================
-- 5. TABLE: audit_log  (registration & login events)
-- =============================================================
CREATE TABLE IF NOT EXISTS audit_log (
    log_id          BIGSERIAL     PRIMARY KEY,
    user_id         BIGINT        REFERENCES users(user_id) ON DELETE SET NULL,
    event_type      VARCHAR(50)   NOT NULL,   -- e.g. USER_REGISTERED, USER_LOGIN, PASSWORD_CHANGED
    ip_address      VARCHAR(45),
    user_agent      VARCHAR(255),
    metadata        JSONB,                    -- flexible extra context (device, location, …)
    occurred_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_user   ON audit_log(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_event  ON audit_log(event_type);
CREATE INDEX IF NOT EXISTS idx_audit_time   ON audit_log(occurred_at DESC);

-- =============================================================
-- 6. FUNCTION + TRIGGER: auto-create profile & preferences
--    rows when a new user is inserted into users
-- =============================================================
CREATE OR REPLACE FUNCTION fn_init_user_records()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    INSERT INTO user_profiles (user_id)
        VALUES (NEW.user_id)
        ON CONFLICT (user_id) DO NOTHING;

    INSERT INTO user_preferences (user_id)
        VALUES (NEW.user_id)
        ON CONFLICT (user_id) DO NOTHING;

    INSERT INTO audit_log (user_id, event_type, metadata)
        VALUES (NEW.user_id, 'USER_REGISTERED',
                jsonb_build_object('username', NEW.username, 'email', NEW.email));

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_init_user_records ON users;
CREATE TRIGGER trg_init_user_records
    AFTER INSERT ON users
    FOR EACH ROW
    EXECUTE FUNCTION fn_init_user_records();

-- =============================================================
-- 7. FUNCTION + TRIGGER: keep updated_at current
-- =============================================================
CREATE OR REPLACE FUNCTION fn_touch_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;

-- Apply to every table that has updated_at
DO $$
DECLARE
    tbl TEXT;
BEGIN
    FOREACH tbl IN ARRAY ARRAY['users','user_profiles','user_preferences']
    LOOP
        EXECUTE format('
            DROP TRIGGER IF EXISTS trg_touch_%1$s ON %1$s;
            CREATE TRIGGER trg_touch_%1$s
                BEFORE UPDATE ON %1$s
                FOR EACH ROW EXECUTE FUNCTION fn_touch_updated_at();
        ', tbl);
    END LOOP;
END;
$$;

-- =============================================================
-- 8. SAMPLE SUPPORTED CURRENCIES  (reference data)
-- =============================================================
CREATE TABLE IF NOT EXISTS supported_currencies (
    currency_code   CHAR(3)       PRIMARY KEY,   -- ISO 4217
    currency_name   VARCHAR(60)   NOT NULL,
    symbol          VARCHAR(5)    NOT NULL,
    is_active       BOOLEAN       NOT NULL DEFAULT TRUE
);

INSERT INTO supported_currencies (currency_code, currency_name, symbol) VALUES
    ('USD', 'US Dollar',           '$'),
    ('EUR', 'Euro',                '€'),
    ('GBP', 'British Pound',       '£'),
    ('JPY', 'Japanese Yen',        '¥'),
    ('CAD', 'Canadian Dollar',     'CA$'),
    ('AUD', 'Australian Dollar',   'A$'),
    ('CHF', 'Swiss Franc',         'Fr'),
    ('INR', 'Indian Rupee',        '₹'),
    ('BRL', 'Brazilian Real',      'R$'),
    ('MXN', 'Mexican Peso',        'MX$')
ON CONFLICT (currency_code) DO NOTHING;

-- FK from users.preferred_currency → supported_currencies
ALTER TABLE users
    ADD CONSTRAINT fk_users_currency
    FOREIGN KEY (preferred_currency)
    REFERENCES supported_currencies(currency_code)
    ON UPDATE CASCADE
    ON DELETE RESTRICT
    NOT VALID;   -- validates only new/updated rows; run VALIDATE CONSTRAINT separately
