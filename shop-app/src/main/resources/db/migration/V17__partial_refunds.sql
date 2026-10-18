-- ===========================================================================
-- V17 — partial refunds (docs/payment-design.md, section 11).
--
-- A payment can now be refunded several times, in part: each refund is a row of
-- its own, with its own key with the provider, its status and its record. The one
-- refund a cancellation kept in the payment's own columns moves into a
-- CANCELLATION row, and keeps its key (refund_<payment id>): one still pending must
-- be repeated under the key it was first sent with, or the provider would refund
-- it twice. New refunds' keys start rfd_, so the two can't collide.
-- ===========================================================================

create table refund (
    id bigint not null auto_increment,
    version bigint not null,
    payment_id bigint not null,
    kind enum ('AMOUNT','CANCELLATION','ITEMS') not null,
    amount decimal(38,2) not null,
    currency varchar(3) not null,
    reason varchar(255),
    status enum ('PENDING','REFUNDED') not null,
    idempotency_key varchar(64) not null,
    request_key varchar(64),
    provider_reference varchar(64),
    requested_by varchar(255),
    requested_at datetime(6) not null,
    refunded_at datetime(6),
    primary key (id),
    constraint chk_refund_amount_positive check (amount > 0),
    constraint uk_refund_idempotency_key unique (idempotency_key),
    constraint uk_refund_provider_reference unique (provider_reference),
    -- The admin's Idempotency-Key: a repeated request gets the first refund.
    constraint uk_refund_payment_request_key unique (payment_id, request_key),
    -- Payments are never deleted by the app; the cascade is for test data.
    constraint fk_refund_payment foreign key (payment_id) references payment (id) on delete cascade
) engine=InnoDB;

-- The retry job's query: refunds still owed, oldest first.
create index idx_refund_status_requested on refund (status, requested_at);

-- The order items an ITEMS refund gave money back for.
create table refund_item (
    id bigint not null auto_increment,
    refund_id bigint not null,
    order_item_id bigint not null,
    quantity integer not null,
    amount decimal(38,2) not null,
    primary key (id),
    constraint chk_refund_item_quantity_positive check (quantity > 0),
    constraint fk_refund_item_refund foreign key (refund_id) references refund (id) on delete cascade,
    constraint fk_refund_item_order_item foreign key (order_item_id) references orderitems (id)
) engine=InnoDB;

-- How many of each item have been refunded, at most its quantity: a cancellation
-- restocks only the rest.
alter table orderitems
    add column refunded_quantity integer not null default 0;

-- The refunds so far, each a CANCELLATION row under its old key.
insert into refund (version, payment_id, kind, amount, currency, reason, status, idempotency_key,
                    provider_reference, requested_at, refunded_at)
select 0, id, 'CANCELLATION', amount, currency, 'Order cancelled',
       case when status = 'REFUNDED' then 'REFUNDED' else 'PENDING' end,
       concat('refund_', id), refund_reference,
       coalesce(refund_requested_at, completed_at, created_at), refunded_at
from payment
where status in ('REFUND_PENDING', 'REFUNDED');

-- What has been owed and refunded from each payment, added up. Kept on the payment
-- row because what refunds a payment locks that row first, and a locking read
-- returns it as committed now; refunds read after it may be older (Payment). The
-- check makes refunding more than was paid impossible, whatever the code does.
alter table payment
    add column refund_requested_amount decimal(38,2) not null default 0,
    add column refunded_amount decimal(38,2) not null default 0;

update payment set refund_requested_amount = amount where status in ('REFUND_PENDING', 'REFUNDED');
update payment set refunded_amount = amount where status = 'REFUNDED';

alter table payment
    add constraint chk_payment_refund_totals check (
        refunded_amount >= 0 and refunded_amount <= refund_requested_amount and refund_requested_amount <= amount);

alter table payment
    drop index idx_payment_status_refund_requested,
    drop index uk_payment_refund_reference,
    drop column refund_reference,
    drop column refund_requested_at,
    drop column refunded_at;
