-- ===========================================================================
-- V5 — wishlist + in-app notifications.
--
-- wishlist_item: one row per (user, product) a customer has saved. It carries an
-- optional one-shot dated reminder (remind_at) and the price/stock alert state:
-- alert_price and was_in_stock are the baseline the scheduled scan compares the
-- live product against, so each price drop or restock is announced exactly once.
--
-- notification: the in-app inbox that reminders and alerts are written to.
-- product_name and the prices are snapshots, so the text still reads correctly
-- after the product is edited or deleted.
--
-- Foreign keys cascade in the database: deleting a user removes their wishlist
-- and notifications, and deleting a product removes it from every wishlist and
-- unlinks (but keeps) past notifications — so neither table can block a delete.
-- ===========================================================================

create table wishlist_item (
    alerts_enabled bit not null,
    was_in_stock bit not null,
    alert_price decimal(38,2) not null,
    price_when_added decimal(38,2) not null,
    created_at datetime(6) not null,
    remind_at datetime(6),
    id bigint not null,
    product_id bigint not null,
    user_id bigint not null,
    version bigint not null,
    primary key (id)
) engine=InnoDB;

create table wishlist_item_seq (
    next_val bigint
) engine=InnoDB;

insert into wishlist_item_seq ( next_val ) values ( 1 );

create table notification (
    new_price decimal(38,2),
    old_price decimal(38,2),
    created_at datetime(6) not null,
    read_at datetime(6),
    id bigint not null,
    product_id bigint,
    user_id bigint not null,
    type enum ('BACK_IN_STOCK','PRICE_DROP','WISHLIST_REMINDER') not null,
    product_name varchar(255) not null,
    primary key (id)
) engine=InnoDB;

create table notification_seq (
    next_val bigint
) engine=InnoDB;

insert into notification_seq ( next_val ) values ( 1 );

-- A product appears at most once per wishlist; also serves "is it saved?" lookups.
alter table wishlist_item
   add constraint uk_wishlist_user_product unique (user_id, product_id);

-- The reminder scan: WHERE remind_at <= now.
create index idx_wishlist_remind_at
   on wishlist_item (remind_at);

-- The inbox: WHERE user_id = ? ORDER BY created_at DESC.
create index idx_notification_user_created
   on notification (user_id, created_at);

alter table wishlist_item
   add constraint fk_wishlist_item_user
   foreign key (user_id)
   references users (id)
   on delete cascade;

alter table wishlist_item
   add constraint fk_wishlist_item_product
   foreign key (product_id)
   references product (id)
   on delete cascade;

alter table notification
   add constraint fk_notification_user
   foreign key (user_id)
   references users (id)
   on delete cascade;

alter table notification
   add constraint fk_notification_product
   foreign key (product_id)
   references product (id)
   on delete set null;
