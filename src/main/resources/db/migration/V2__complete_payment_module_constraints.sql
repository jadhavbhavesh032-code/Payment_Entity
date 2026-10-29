-- Idempotency keys are backfilled before NOT NULL is enforced so that this
-- migration remains safe for payment attempts and refunds created under V1.
ALTER TABLE payments.payment_transaction
    ADD COLUMN idempotency_key VARCHAR(100);

UPDATE payments.payment_transaction
SET idempotency_key =
        'legacy-transaction-'
        || CAST(transaction_id AS VARCHAR)
WHERE idempotency_key IS NULL;

ALTER TABLE payments.payment_transaction
    ALTER COLUMN idempotency_key SET NOT NULL;

ALTER TABLE payments.payment_transaction
    ADD CONSTRAINT chk_transaction_idempotency_key
        CHECK (BTRIM(idempotency_key) <> ''),
    ADD CONSTRAINT uk_transaction_payment_idempotency
        UNIQUE (payment_id, idempotency_key),
    ADD CONSTRAINT uk_transaction_idempotency_key
        UNIQUE (idempotency_key);


ALTER TABLE payments.refund
    ADD COLUMN idempotency_key VARCHAR(100);

UPDATE payments.refund
SET idempotency_key =
        'legacy-refund-'
        || CAST(refund_id AS VARCHAR)
WHERE idempotency_key IS NULL;

ALTER TABLE payments.refund
    ALTER COLUMN idempotency_key SET NOT NULL;

ALTER TABLE payments.refund
    ADD CONSTRAINT chk_refund_idempotency_key
        CHECK (BTRIM(idempotency_key) <> ''),
    ADD CONSTRAINT uk_refund_payment_idempotency
        UNIQUE (payment_id, idempotency_key),
    ADD CONSTRAINT uk_refund_idempotency_key
        UNIQUE (idempotency_key);


-- A composite candidate key lets the refund foreign key prove that the
-- referenced transaction belongs to the same payment as the refund.
ALTER TABLE payments.payment_transaction
    ADD CONSTRAINT uk_transaction_payment_pair
        UNIQUE (payment_id, transaction_id);

ALTER TABLE payments.refund
    DROP CONSTRAINT fk_refund_transaction;

ALTER TABLE payments.refund
    ADD CONSTRAINT fk_refund_transaction_payment
        FOREIGN KEY (payment_id, transaction_id)
        REFERENCES payments.payment_transaction(
            payment_id,
            transaction_id
        );


-- Bookings must have a positive price, while a full discount may still make
-- final_amount zero under the existing final-amount constraint.
ALTER TABLE payments.payment
    DROP CONSTRAINT chk_payment_booking_amount;

ALTER TABLE payments.payment
    ADD CONSTRAINT chk_payment_booking_amount
        CHECK (booking_amount > 0);


-- New attempts enter the only non-terminal transaction state directly.
ALTER TABLE payments.payment_transaction
    ALTER COLUMN transaction_status SET DEFAULT 'PENDING';


-- COMPLETED is the canonical successful refund state from V2 onward.
ALTER TABLE payments.refund
    DROP CONSTRAINT chk_refund_status;

UPDATE payments.refund
SET refund_status = 'COMPLETED'
WHERE refund_status = 'SUCCESS';

ALTER TABLE payments.refund
    ADD CONSTRAINT chk_refund_status
        CHECK (
            refund_status IN (
                'REQUESTED',
                'PROCESSING',
                'COMPLETED',
                'FAILED',
                'REJECTED'
            )
        );


COMMENT ON COLUMN payments.payment_transaction.idempotency_key IS
'Client request key; globally unique for transaction attempts and also paired with payment_id.';

COMMENT ON COLUMN payments.refund.idempotency_key IS
'Client request key; globally unique for refunds and also paired with payment_id.';
