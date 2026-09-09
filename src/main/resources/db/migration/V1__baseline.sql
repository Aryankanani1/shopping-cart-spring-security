-- ===========================================================================
-- V1 baseline — the full initial schema.
--
-- Generated from the JPA entities via Hibernate's MySQLDialect schema export so
-- it matches the mappings exactly (ddl-auto: validate must accept it verbatim).
-- Sequences are emulated as *_seq tables because MySQL has no native SEQUENCE;
-- this is what Hibernate itself produces for GenerationType.SEQUENCE on MySQL.
--
-- All later schema changes go in new V2__, V3__, ... files — never edit this one.
-- ===========================================================================

create table cart (
    total_amount decimal(38,2),
    id bigint not null,
    user_id bigint,
    version bigint not null,
    primary key (id)
) engine=InnoDB;

create table cart_item_seq (
    next_val bigint
) engine=InnoDB;

insert into cart_item_seq ( next_val ) values ( 1 );

create table cart_seq (
    next_val bigint
) engine=InnoDB;

insert into cart_seq ( next_val ) values ( 1 );

create table cart_item (
    quantity integer not null,
    total_price decimal(38,2),
    unit_price decimal(38,2),
    cart_id bigint,
    id bigint not null,
    product_id bigint,
    version bigint not null,
    primary key (id)
) engine=InnoDB;

create table category (
    id bigint not null,
    version bigint not null,
    name varchar(255) not null,
    primary key (id)
) engine=InnoDB;

create table category_seq (
    next_val bigint
) engine=InnoDB;

insert into category_seq ( next_val ) values ( 1 );

create table image (
    id bigint not null,
    product_id bigint,
    version bigint not null,
    file_name varchar(255),
    file_type varchar(255),
    url varchar(255),
    image longblob,
    primary key (id)
) engine=InnoDB;

create table image_seq (
    next_val bigint
) engine=InnoDB;

insert into image_seq ( next_val ) values ( 1 );

create table order_item_seq (
    next_val bigint
) engine=InnoDB;

insert into order_item_seq ( next_val ) values ( 1 );

create table orderitems (
    price decimal(38,2),
    quantity integer not null,
    id bigint not null,
    order_id bigint,
    product_id bigint,
    version bigint not null,
    primary key (id)
) engine=InnoDB;

create table orders (
    local_date date,
    total_amount decimal(38,2),
    created_at datetime(6) not null,
    id bigint not null,
    user_id bigint,
    version bigint not null,
    order_status enum ('CANCELLED','DELIVERED','PENDING','PROCESSING','SHIPPED'),
    primary key (id)
) engine=InnoDB;

create table orders_seq (
    next_val bigint
) engine=InnoDB;

insert into orders_seq ( next_val ) values ( 1 );

create table product (
    inventory integer,
    price decimal(38,2),
    category_id bigint,
    id bigint not null,
    version bigint not null,
    brand varchar(255),
    description varchar(255),
    name varchar(255),
    primary key (id)
) engine=InnoDB;

create table product_seq (
    next_val bigint
) engine=InnoDB;

insert into product_seq ( next_val ) values ( 1 );

create table refresh_tokens (
    revoked bit not null,
    created_at datetime(6) not null,
    expires_at datetime(6) not null,
    id bigint not null,
    user_id bigint not null,
    token_hash varchar(64) not null,
    primary key (id)
) engine=InnoDB;

create table refresh_tokens_seq (
    next_val bigint
) engine=InnoDB;

insert into refresh_tokens_seq ( next_val ) values ( 1 );

create table roles (
    id bigint not null,
    version bigint not null,
    name varchar(255),
    primary key (id)
) engine=InnoDB;

create table roles_seq (
    next_val bigint
) engine=InnoDB;

insert into roles_seq ( next_val ) values ( 1 );

create table user_roles (
    role_id bigint not null,
    user_id bigint not null
) engine=InnoDB;

create table users (
    id bigint not null,
    version bigint not null,
    email varchar(255),
    first_name varchar(255),
    last_name varchar(255),
    password varchar(255),
    primary key (id)
) engine=InnoDB;

create table users_seq (
    next_val bigint
) engine=InnoDB;

insert into users_seq ( next_val ) values ( 1 );

alter table cart
   add constraint UK9emlp6m95v5er2bcqkjsw48he unique (user_id);

create index idx_orders_user_created
   on orders (user_id, created_at, id);

create index idx_product_name
   on product (name);

create index idx_product_brand
   on product (brand);

create index idx_product_price
   on product (price);

alter table refresh_tokens
   add constraint idx_refresh_tokens_hash unique (token_hash);

alter table users
   add constraint UKq4gvg4dl2a3fpetfwspodde8e unique (email);

alter table cart
   add constraint FKg5uhi8vpsuy0lgloxk2h4w5o6
   foreign key (user_id)
   references users (id);

alter table cart_item
   add constraint FK1uobyhgl1wvgt1jpccia8xxs3
   foreign key (cart_id)
   references cart (id);

alter table cart_item
   add constraint FKjcyd5wv4igqnw413rgxbfu4nv
   foreign key (product_id)
   references product (id);

alter table image
   add constraint FKgpextbyee3uk9u6o2381m7ft1
   foreign key (product_id)
   references product (id);

alter table orderitems
   add constraint FKm3mp87f5ygbbfuqfdhc09y9a
   foreign key (order_id)
   references orders (id);

alter table orderitems
   add constraint FKatri80p9fodn2lpjxxpcv03hm
   foreign key (product_id)
   references product (id);

alter table orders
   add constraint FK32ql8ubntj5uh44ph9659tiih
   foreign key (user_id)
   references users (id);

alter table product
   add constraint FK1mtsbur82frn64de7balymq9s
   foreign key (category_id)
   references category (id);

alter table refresh_tokens
   add constraint FK1lih5y2npsf8u5o3vhdb9y0os
   foreign key (user_id)
   references users (id);

alter table user_roles
   add constraint FKh8ciramu9cc9q3qcqiv4ue8a6
   foreign key (role_id)
   references roles (id);

alter table user_roles
   add constraint FKhfh9dx7w3ubf1co1vdev94g3f
   foreign key (user_id)
   references users (id);
