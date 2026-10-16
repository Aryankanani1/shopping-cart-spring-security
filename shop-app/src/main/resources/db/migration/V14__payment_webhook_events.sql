-- ===========================================================================
-- V14 — webhook events received from the payment provider (docs/payment-design.md,
-- section 7).
--
-- The unique event_id is what makes processing exactly-once: it is inserted in
-- the same transaction as the change the event makes, so a repeat of the event
-- (providers deliver at least once) finds it and changes nothing, and two
-- deliveries at the same moment collide on the key, only one committing.
--
-- payment_id is a plain column, not a foreign key: an event can name a payment
-- this shop doesn't know, and is recorded all the same.
-- ===========================================================================

create table payment_webhook_event (
    id bigint not null auto_increment,
    event_id varchar(64) not null,
    type varchar(64) not null,
    payment_id bigint,
    received_at datetime(6) not null,
    primary key (id)
) engine=InnoDB;

alter table payment_webhook_event
   add constraint uk_payment_webhook_event_event_id unique (event_id);
