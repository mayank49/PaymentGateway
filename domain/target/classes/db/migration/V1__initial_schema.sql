-- =============================================================
-- V1__initial_schema.sql
-- =============================================================
-- Conventions used throughout:
--   All amounts in smallest currency unit (paise/cents) : BIGINT
--   UUIDs as primary keys : gen_random_uuid()
--   All timestamps as TIMESTAMPTZ (with timezone) : maps to Instant in Java
--   Enums defined at DB level : catches invalid values before they hit Java
--   JSONB for flexible metadata : queryable, indexed if needed
-- =============================================================


-- -------------------------------------------------------------
-- ENUMS
-- Define at DB level so the database itself rejects invalid values.
-- If you add a new enum value in Java, add it here too in a V2 migration.
-- -------------------------------------------------------------

CREATE TYPE intent_status AS ENUM (
    'REQUIRES_PAYMENT_METHOD',
    'PROCESSING',
    'SUCCEEDED',
    'FAILED',
    'REQUIRES_ACTION',
    'UNKNOWN',
    'CANCELED'
);

CREATE TYPE transaction_status AS ENUM (
    'PENDING',
    'AUTHORIZED',
    'CAPTURED',
    'FAILED',
    'PENDING_VERIFICATION',
    'REFUNDED'
);

CREATE TYPE reconciliation_status AS ENUM (
    'MATCHED',
    'UNMATCHED',
    'DISCREPANCY'
);

CREATE TYPE payment_method_type AS ENUM (
    'CARD',
    'UPI',
    'NET_BANKING',
    'WALLET',
    'EMI'
);

-- -------------------------------------------------------------
-- MERCHANT
-- SHA-256 of the raw API key is stored. Raw key is shown once at registration, never stored.
-- -------------------------------------------------------------

CREATE TABLE merchant (
	id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
	name VARCHAR(200) NOT NULL,
	email VARCHAR(255) NOT NULL UNIQUE,
	api_key_hash VARCHAR(64) NOT NULL UNIQUE,
	settlement_account_number VARCHAR(50),
    settlement_ifsc VARCHAR(20),
    webhook_url VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ
);

CREATE INDEX idx_merchant_email ON merchant (email);
CREATE INDEX idx_merchant_api_key_hash ON merchant (api_key_hash);

-- -------------------------------------------------------------
-- PAYMENT INTENT
-- -------------------------------------------------------------

CREATE TABLE payment_intent (
	id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
	merchant_id UUID NOT NULL REFERENCES merchant (id),
	amount BIGINT NOT NULL CHECK (amount > 0),
	currency CHAR(3) NOT NULL,
	status intent_status NOT NULL DEFAULT 'REQUIRES_PAYMENT_METHOD',
	idempotency_key VARCHAR(255) NOT NULL UNIQUE,
	client_secret VARCHAR(100) NOT NULL,
	payment_method_type payment_method_type,
	metdata JSONB NOT NULL DEFAULT '{}'.
	processor_reference VARCHAR(255),
	created_at TIMESTAMPTZ NOT NULL,
	updated_at TIMESTAMPTZ
);

-- Merchant dashboard queries always filter by merchant_id
CREATE INDEX idx_intent_merchant_id ON payment_intent (merchant_id);
-- Idempotency check on every create request
CREATE INDEX idx_intent_idempotency_key ON payment_intent (idempotency_key);
-- Reconciliation filters by status (UNKNOWN, PROCESSING)
CREATE INDEX idx_intent_status ON payment_intent (status);
-- Date range queries, "all intents from today"
CREATE INDEX idx_intent_created_at ON payment_intent (created_at);

-- -------------------------------------------------------------
-- TRANSACTION
-- One row per actual attempt to move money through the processor.
-- Never updated after creation. Retries and refunds create new rows.
-- -------------------------------------------------------------

CREATE TABLE transaction (
	id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
	intent_id UUID NOT NULL REFERENCES payment_intent (id),
	processor VARCHAR(50) NOT NULL,
	processor_txn_id VARCHAR(255),
	status transaction_status NOT NULL DEFAULT 'PENDING',
	amount BIGINT NOT NULL CHECK (amount > 0),
	currency CHAR(3) NOT NULL,
	auth_code VARCHAR(20),
	error_message VARCHAR(500),
	parent_transaction_id UUID REFERENCES transaction (id),
	created_at TIMESTAMPTZ NOT NULL,
	updated_at TIMESTAMPTZ
);

-- Most lookups: "all transactions for this intent"
CREATE INDEX idx_txn_intent_id ON transaction (intent_id);
-- Reconciliation: match our rows to Razorpay's settlement file by their ID
CREATE INDEX idx_txn_processor_txn_id ON transaction (processor_txn_id);
-- Reconciliation date range queries
CREATE INDEX idx_txn_created_at ON transaction (created_at);
-- Finding all PENDING_VERIFICATION rows
CREATE INDEX idx_txn_status ON transaction (status);

-- -------------------------------------------------------------
-- WEBHOOK EVENT
-- Persisted record of every notification we need to send to a merchant.
-- The scheduler polls this table and retries on exponential backoff.
-- -------------------------------------------------------------

CREATE TABLE webhook_event (
	id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
	merchant_id UUID NOT NULL REFERENCES merchant (id),
	intent_id UUID NOT NULL REFERENCES payment_intent (id),
	event_type VARCHAR(50) NOT NULL,
	payload JSONB NOT NULL,
	attempt_count SMALLINT NOT NULL DEFAULT 0,
	max_attempts SMALLINT NOT NULL DEFAULT 4,
	last_attempt_at TIMESTAMPTZ,
	next_attempt_at TIMESTAMPTZ,
	http_status SMALLINT,
	created_at TIMESTAMPTZ NOT NULL,
	updated_at TIMESTAMPTZ
);

CREATE INDEX idx_webhook_intent_id ON webhook_event (intent_id);
CREATE INDEX idx_webhook_merchant_id ON webhook_event (merchant_id);
CREATE INDEX idx_webhook_next_attempt_at ON webhook_event (next_attempt_at) WHERE delivered = false;

-- -------------------------------------------------------------
-- MERCHANT LEDGER
-- One row per transaction per reconciliation run.
-- Tracks the result of matching our records against Razorpay's settlement file.
-- -------------------------------------------------------------

CREATE TABLE merchant_ledger (
	id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
	merchant_id UUID NOT NULL REFERENCES merchant (id),
	transaction_id UUID NOT NULL REFERENCES transaction (id) UNIQUE,
	processor_txn_id VARCHAR(255),
	internal_amount BIGINT,
	processor_amount BIGINT,
	status reconciliation_status NOT NULL DEFAULT 'UNMATCHED',
	settlement_date DATE,
	processor_id VARCHAR(20),
	processor_data JSONB,
	created_at TIMESTAMPTZ NOT NULL,
	updated_at TIMESTAMPTZ
);

CREATE INDEX idx_ledger_merchant_id ON merchant_ledger (merchant_id);
CREATE INDEX idx_ledger_settlement_date ON merchant_ledger (settlement_date);
CREATE INDEX idx_ledger_status ON merchant_ledger (status);
CREATE INDEX idx_ledger_processor_id ON merchant_ledger (processor_id);