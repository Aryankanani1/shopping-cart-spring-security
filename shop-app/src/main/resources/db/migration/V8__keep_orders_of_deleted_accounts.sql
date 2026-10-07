-- Deleting an account used to delete its orders too (a JPA cascade). Orders are
-- now kept for the shop's records: deleting the user sets orders.user_id to null.
-- The app cancels and restocks any order still open before the delete.
--
-- The constraint name is the one Hibernate generated for V1.

alter table orders drop foreign key FK32ql8ubntj5uh44ph9659tiih;

alter table orders
   add constraint fk_orders_user
   foreign key (user_id)
   references users (id)
   on delete set null;
