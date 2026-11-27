-- ===========================================================================
-- V19 — the checkout saga (docs/payment-design.md, section 13).
--
-- One row per order: how far its checkout got (booked, paid) and whether it was
-- undone. CheckoutSaga changes it in the same transaction as each step it
-- records, under the row's lock. A compensation owed after a commit (cancelling
-- the order after its last failed payment, a refund) is an outbox message (V18).
--
-- Orders are never deleted by the app; the cascade is for test data.
-- ===========================================================================

create table checkout_saga (
    id bigint not null auto_increment,
    order_id bigint not null,
    status enum ('AWAITING_PAYMENT','COMPENSATED','COMPENSATING','COMPLETED') not null,
    compensation_reason enum ('ORDER_CANCELLED','PAYMENT_ATTEMPTS_USED_UP','PAYMENT_DEADLINE_PASSED'),
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    primary key (id),
    constraint uk_checkout_saga_order unique (order_id),
    constraint fk_checkout_saga_order foreign key (order_id) references orders (id) on delete cascade
) engine=InnoDB;

-- Every order placed before this release gets the saga it would have had: unpaid
-- ones await payment, paid ones are completed, and cancelled ones are compensated,
-- or still compensating while their refund is owed. Why they were cancelled isn't
-- recorded anywhere, so they all count as cancelled by someone.
insert into checkout_saga (order_id, status, compensation_reason, created_at, updated_at)
select o.id,
       case
           when o.order_status = 'PENDING' then 'AWAITING_PAYMENT'
           when o.order_status <> 'CANCELLED' then 'COMPLETED'
           when exists (select 1 from payment p where p.order_id = o.id and p.status = 'REFUND_PENDING')
               then 'COMPENSATING'
           else 'COMPENSATED'
       end,
       case when o.order_status = 'CANCELLED' then 'ORDER_CANCELLED' end,
       o.created_at,
       utc_timestamp(6)
  from orders o;
