-- ===========================================================================
-- V10 — longer product descriptions.
--
-- product.description was varchar(255), which a product description easily
-- outgrows; a longer one failed at the database and came back as a 409 "Data
-- conflict". It now holds up to 2000 characters (Product.DESCRIPTION_MAX), and
-- the request classes reject anything longer with a 400 before it gets here.
-- Existing values are unchanged; the column stays nullable.
-- ===========================================================================

alter table product
    modify column description varchar(2000);
