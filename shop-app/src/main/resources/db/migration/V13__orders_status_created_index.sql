-- ===========================================================================
-- V13 — an index for UnpaidOrderExpiryJob (docs/payment-design.md, section 6):
--
--   WHERE order_status = 'PENDING' AND created_at < ?  ORDER BY created_at
--
-- idx_orders_user_created leads with user_id, so without this the job would scan
-- every order, every minute.
-- ===========================================================================

create index idx_orders_status_created
   on orders (order_status, created_at);
