-- ============================================================================
-- Migration: OTP challenge table for payment MFA (mobile OTP verification)
-- ============================================================================
-- The application runs with spring.jpa.hibernate.ddl-auto=validate, so the
-- physical schema must match the OtpChallenge entity. This table holds a
-- short-lived, single-use, hashed OTP plus the validated (but not yet created)
-- payment details, until the user verifies the OTP sent to their mobile.
--
-- This script is IDEMPOTENT and SAFE TO RE-RUN. Run it against the PostgreSQL
-- `paymentdb` database before starting the app.
-- ============================================================================

CREATE TABLE IF NOT EXISTS otp_challenge (
    challenge_id    VARCHAR(36)    PRIMARY KEY,
    user_id         BIGINT         NOT NULL,
    otp_hash        VARCHAR(255)   NOT NULL,
    mobile_number   VARCHAR(20),
    account_id      BIGINT         NOT NULL,
    payee_id        BIGINT         NOT NULL,
    payment_amount  NUMERIC(15,2)  NOT NULL,
    payment_date    DATE           NOT NULL,
    memo            VARCHAR(100),
    expires_at      TIMESTAMP      NOT NULL,
    attempts        INTEGER        NOT NULL DEFAULT 0,
    consumed        BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP      NOT NULL DEFAULT NOW()
);

-- Helpful index for owner-scoped lookups and expiry cleanup.
CREATE INDEX IF NOT EXISTS idx_otp_challenge_user_id   ON otp_challenge (user_id);
CREATE INDEX IF NOT EXISTS idx_otp_challenge_expires_at ON otp_challenge (expires_at);

