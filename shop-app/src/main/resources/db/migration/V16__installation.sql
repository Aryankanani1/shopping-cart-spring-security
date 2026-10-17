-- ===========================================================================
-- V16 — a random id for this database (docs/payment-design.md, section 8).
--
-- MySQL generates it once, when this migration runs, so every database gets its
-- own: a local one wiped and created again, another developer's. The Stripe
-- adapter starts its idempotency keys with it and puts it in its metadata, because
-- Stripe keeps keys and sends webhooks per account, while payment ids (pay_7) are
-- unique only within one database.
-- ===========================================================================

create table installation (
    id char(36) not null,
    primary key (id)
) engine=InnoDB;

insert into installation (id) values (uuid());
