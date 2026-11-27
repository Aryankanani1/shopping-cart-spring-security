-- ===========================================================================
-- V20 — refunds of charges made after an attempt failed (docs/payment-design.md,
-- section 14).
--
-- The provider can report charging an attempt the shop has recorded as failed,
-- and told the customer so. The attempt stays FAILED, late_charged_at records
-- when the charge was reported, and all of it is owed back as a LATE_CHARGE
-- refund (sent through the outbox, like every refund).
-- ===========================================================================

alter table payment add column late_charged_at datetime(6);

alter table refund modify kind enum ('AMOUNT','CANCELLATION','ITEMS','LATE_CHARGE') not null;
