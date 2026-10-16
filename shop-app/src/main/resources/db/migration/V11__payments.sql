-- ===========================================================================
-- V11 — payments (docs/payment-design.md).
--
-- orders.order_status gets PAID: an order is now paid for before it is
-- fulfilled, and only a successful payment moves it from PENDING to PAID.
--
-- payment: one row per attempt to pay for an order, kept for good. A row is
-- written (PROCESSING) before the provider is called and settled (SUCCEEDED or
-- FAILED) after, so no provider call happens inside a transaction and an attempt
-- interrupted in between is still on record.
--
-- Orders are never deleted (deleting an account only unlinks them, V8), so the
-- foreign key to orders needs no on-delete rule.
-- ===========================================================================

alter table orders
   modify order_status enum ('CANCELLED','DELIVERED','PAID','PENDING','PROCESSING','SHIPPED');

create table payment (
    id bigint not null auto_increment,
    version bigint not null,
    order_id bigint not null,
    idempotency_key varchar(64) not null,
    payment_method varchar(64) not null,
    amount decimal(38,2) not null,
    currency varchar(3) not null,
    status enum ('FAILED','PROCESSING','SUCCEEDED') not null,
    provider_reference varchar(64),
    card_brand varchar(20),
    card_last4 varchar(4),
    failure_reason varchar(255),
    created_at datetime(6) not null,
    completed_at datetime(6),
    -- The order's id while the payment is in progress or succeeded, null once it
    -- failed. Not mapped by the entity: it exists for the unique key below.
    live_order_id bigint generated always as (case when status <> 'FAILED' then order_id end) stored,
    primary key (id),
    constraint chk_payment_amount_positive check (amount > 0)
) engine=InnoDB;

-- A repeated request (same order, same Idempotency-Key) finds the first attempt
-- instead of paying again; also serves every lookup of an order's payments.
alter table payment
   add constraint uk_payment_order_idempotency_key unique (order_id, idempotency_key);

-- At most one payment per order that hasn't failed: a second charge for an order
-- is impossible even if a code path skipped the order lock. MySQL has no partial
-- indexes, but a unique index ignores nulls, which live_order_id is for every
-- failed attempt.
alter table payment
   add constraint uk_payment_live_order unique (live_order_id);

alter table payment
   add constraint uk_payment_provider_reference unique (provider_reference);

alter table payment
   add constraint fk_payment_order
   foreign key (order_id)
   references orders (id);
