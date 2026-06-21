-- =============================================================
--  PAYMENT APP — PAYMENT WORKFLOW SCHEMA
--  Database  : PostgreSQL 12+
--  Run AFTER : users_auth_schema.sql, register_schema.sql
--  How to use: psql -U postgres -d paymentdb -f payment_schema.sql
-- =============================================================

-- =============================================================
-- TABLE: account  (from-accounts the user can debit)
-- =============================================================
CREATE TABLE IF NOT EXISTS account (
    account_id       BIGSERIAL      PRIMARY KEY,
    account_number   VARCHAR(30)    NOT NULL UNIQUE,
    account_name     VARCHAR(100)   NOT NULL,
    account_balance  NUMERIC(15,2)  NOT NULL DEFAULT 0.00,
    account_status   VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE'
                         CONSTRAINT chk_acc_status
                         CHECK (account_status IN ('ACTIVE','INACTIVE','SUSPENDED')),
    updated_datetime TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_account_status ON account(account_status);

-- =============================================================
-- TABLE: payee  (individuals, mobile/internet providers, etc.)
-- =============================================================
CREATE TABLE IF NOT EXISTS payee (
    payee_id         BIGSERIAL      PRIMARY KEY,
    payee_number     VARCHAR(30)    NOT NULL UNIQUE,
    payee_name       VARCHAR(100)   NOT NULL,
    amount_due       NUMERIC(15,2),
    due_date         DATE,
    updated_datetime TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =============================================================
-- TABLE: fee  (fee tier table per document spec)
-- =============================================================
--  | Min Amount | Max Amount | Fee  |
--  |          0 |         99 |   10 |
--  |        100 |        999 |   25 |
--  |       1000 |       9999 |   50 |
--  |      10000 |      99999 |  100 |
--  |     100000 |       NULL |  500 |

CREATE TABLE IF NOT EXISTS fee (
    fee_id           BIGSERIAL      PRIMARY KEY,
    fee_amount       NUMERIC(10,2)  NOT NULL,
    amount_min       NUMERIC(15,2)  NOT NULL,
    amount_max       NUMERIC(15,2),           -- NULL = no upper bound
    updated_datetime TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_fee_range CHECK (amount_max IS NULL OR amount_max >= amount_min)
);

-- Seed fee tiers from the document
INSERT INTO fee (fee_amount, amount_min, amount_max) VALUES
    (10.00,    0.00,     99.99),
    (25.00,  100.00,    999.99),
    (50.00,  1000.00,  9999.99),
    (100.00, 10000.00, 99999.99),
    (500.00, 100000.00, NULL)
ON CONFLICT DO NOTHING;

-- =============================================================
-- TABLE: payment
-- =============================================================
CREATE TABLE IF NOT EXISTS payment (
    payment_id       BIGSERIAL      PRIMARY KEY,
    account_id       BIGINT         NOT NULL REFERENCES account(account_id),
    payee_id         BIGINT         NOT NULL REFERENCES payee(payee_id),
    fee_id           BIGINT         NOT NULL REFERENCES fee(fee_id),
    payment_amount   NUMERIC(15,2)  NOT NULL,
    payment_date     DATE           NOT NULL,
    memo             VARCHAR(100),
    status           VARCHAR(20)    NOT NULL DEFAULT 'PENDING'
                         CONSTRAINT chk_pay_status
                         CHECK (status IN ('PENDING','COMPLETED','CANCELLED')),
    updated_datetime TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_payment_date CHECK (payment_date >= CURRENT_DATE)
);

CREATE INDEX IF NOT EXISTS idx_payment_account  ON payment(account_id);
CREATE INDEX IF NOT EXISTS idx_payment_payee    ON payment(payee_id);
CREATE INDEX IF NOT EXISTS idx_payment_date     ON payment(payment_date);
CREATE INDEX IF NOT EXISTS idx_payment_status   ON payment(status);

-- =============================================================
-- TRIGGER: auto-update updated_datetime on every UPDATE
-- =============================================================
CREATE OR REPLACE FUNCTION fn_payment_touch_updated()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN NEW.updated_datetime = CURRENT_TIMESTAMP; RETURN NEW; END;
$$;

DO $$ DECLARE tbl TEXT;
BEGIN
    FOREACH tbl IN ARRAY ARRAY['account','payee','fee','payment']
    LOOP
        EXECUTE format('
            DROP TRIGGER IF EXISTS trg_touch_%1$s ON %1$s;
            CREATE TRIGGER trg_touch_%1$s
                BEFORE UPDATE ON %1$s
                FOR EACH ROW EXECUTE FUNCTION fn_payment_touch_updated();
        ', tbl);
    END LOOP;
END; $$;

-- =============================================================
-- SEED DATA — sample accounts and payees for development
-- =============================================================
INSERT INTO account (account_number, account_name, account_balance, account_status) VALUES
    ('ACC-001001', 'Savings Account',        150000.00, 'ACTIVE'),
    ('ACC-001002', 'Current Account',         85000.00, 'ACTIVE'),
    ('ACC-001003', 'Salary Account',          62000.00, 'ACTIVE'),
    ('ACC-001004', 'Joint Account',           30000.00, 'INACTIVE')
ON CONFLICT (account_number) DO NOTHING;

INSERT INTO payee (payee_number, payee_name, amount_due, due_date) VALUES
    ('PAY-MOB001', 'Airtel Mobile',         499.00, CURRENT_DATE + 5),
    ('PAY-MOB002', 'Jio Prepaid',           299.00, CURRENT_DATE + 10),
    ('PAY-NET001', 'ACT Fibernet',          999.00, CURRENT_DATE + 3),
    ('PAY-NET002', 'Hathway Broadband',     799.00, CURRENT_DATE + 7),
    ('PAY-CC001',  'HDFC Credit Card',     5500.00, CURRENT_DATE + 2),
    ('PAY-CC002',  'SBI Credit Card',      3200.00, CURRENT_DATE + 14),
    ('PAY-ELEC01', 'BESCOM Electricity',   1200.00, CURRENT_DATE + 6),
    ('PAY-GAS001', 'Indane Gas',            800.00, CURRENT_DATE + 20)
ON CONFLICT (payee_number) DO NOTHING;
