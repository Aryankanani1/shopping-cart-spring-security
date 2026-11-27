-- ===========================================================================
-- V18 — the transactional outbox (docs/payment-design.md, section 12).
--
-- A message is written in the same transaction as the change that owes the work,
-- so the work is owed if and only if that change commits. OutboxRelay delivers it
-- to its handler after the commit, retrying with a backoff until the handler is
-- done; processed_at marks it delivered. A message names its record by id
-- (aggregate_id) instead of copying it: the handler reads the record as it is.
--
-- aggregate_id is a plain column, not a foreign key: messages of every type share
-- the table, each about a record of its own kind.
-- ===========================================================================

create table outbox_message (
    id bigint not null auto_increment,
    type varchar(64) not null,
    aggregate_id bigint not null,
    created_at datetime(6) not null,
    available_at datetime(6) not null,
    attempts int not null default 0,
    processed_at datetime(6),
    last_error varchar(255),
    primary key (id)
) engine=InnoDB;

-- The relay's query: messages not yet processed and due by now, oldest first.
-- Also the cleanup's: processed before a cutoff.
create index idx_outbox_message_due on outbox_message (processed_at, available_at);

-- Refunds owed before this release were retried by their own job (RefundRetryJob,
-- now removed), from their status. Each one still owed gets its outbox message, so
-- the relay sends it under the key it was first sent with.
insert into outbox_message (type, aggregate_id, created_at, available_at, attempts)
select 'payment.refund', id, requested_at, requested_at, 0
  from refund
 where status = 'PENDING';

-- That job's query was the only one this index served.
drop index idx_refund_status_requested on refund;
