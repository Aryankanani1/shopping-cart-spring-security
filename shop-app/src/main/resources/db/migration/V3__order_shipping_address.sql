-- ===========================================================================
-- V3 — shipping address on orders.
--
-- Checkout now captures a delivery address, stored per order (a snapshot of
-- where that order shipped — deliberately not a foreign key to a mutable
-- address book). Columns are NULLABLE because orders placed before this
-- migration have none; the application enforces the required fields with bean
-- validation on new orders (see PlaceOrderRequest), not the schema.
-- ===========================================================================

alter table orders
    add column recipient_name varchar(255),
    add column address_line1  varchar(255),
    add column address_line2  varchar(255),
    add column city           varchar(255),
    add column state          varchar(255),
    add column postal_code    varchar(255),
    add column country        varchar(255);
