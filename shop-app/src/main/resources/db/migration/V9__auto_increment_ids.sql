-- Ids now come from AUTO_INCREMENT (GenerationType.IDENTITY) instead of the
-- *_seq tables V1 created to emulate sequences, which MySQL doesn't have.
--
-- Hibernate fetched each new block of ids from those tables on a second pooled
-- connection while the request's transaction still held its first. With enough
-- simultaneous requests every connection was held by a transaction waiting on
-- another, the one that needed an id block couldn't get a connection, and the
-- whole pool stalled for the 30-second connection timeout. AUTO_INCREMENT
-- assigns the id in the INSERT itself, on the transaction's own connection.
--
-- MySQL refuses to change a column that a foreign key refers to, so checks are
-- off while the id columns change (the type stays bigint, so every key still
-- matches). Each table's AUTO_INCREMENT counter starts above its highest id.

set foreign_key_checks = 0;

alter table cart          modify id bigint not null auto_increment;
alter table cart_item     modify id bigint not null auto_increment;
alter table category      modify id bigint not null auto_increment;
alter table image         modify id bigint not null auto_increment;
alter table notification  modify id bigint not null auto_increment;
alter table orderitems    modify id bigint not null auto_increment;
alter table orders        modify id bigint not null auto_increment;
alter table product       modify id bigint not null auto_increment;
alter table refresh_tokens modify id bigint not null auto_increment;
alter table roles         modify id bigint not null auto_increment;
alter table users         modify id bigint not null auto_increment;
alter table wishlist_item modify id bigint not null auto_increment;

set foreign_key_checks = 1;

drop table cart_seq, cart_item_seq, category_seq, image_seq, notification_seq,
           order_item_seq, orders_seq, product_seq, refresh_tokens_seq, roles_seq,
           users_seq, wishlist_item_seq;
