-- ===========================================================================
-- V12 — refunds (docs/payment-design.md, section 4).
--
-- Cancelling a paid order refunds it. The cancellation's transaction moves the
-- payment to REFUND_PENDING (an outbox row: the record that money is owed back
-- commits with the cancellation, or not at all); the provider is called after
-- the commit, and REFUNDED is recorded once it has refunded. A refund still
-- pending later is retried by RefundRetryJob.
--
-- The charge's own columns never change; a refund adds its reference and times.
-- live_order_id (V11) stays the order's id for a refunded payment, which is
-- harmless: a cancelled order can't be paid again anyway.
-- ===========================================================================

alter table payment
   modify status enum ('FAILED','PROCESSING','REFUNDED','REFUND_PENDING','SUCCEEDED') not null,
   add column refund_reference varchar(64),
   add column refund_requested_at datetime(6),
   add column refunded_at datetime(6);

-- The provider's id for the refund; one refund per payment.
alter table payment
   add constraint uk_payment_refund_reference unique (refund_reference);

-- RefundRetryJob: WHERE status = 'REFUND_PENDING' AND refund_requested_at < ?
create index idx_payment_status_refund_requested
   on payment (status, refund_requested_at);
