-- ===========================================================================
-- V15 — each user holds each role once.
--
-- user_roles had no key, and User.roles was mapped as a bag, which doesn't notice
-- a role the user already has: AdminBootstrap added ROLE_ADMIN to the admin again
-- at every start. User.roles is now a Set; this primary key makes the database
-- refuse a duplicate too.
--
-- Two copies of a row can't be told apart, so a temporary id numbers the rows
-- first, and every copy but the first is deleted.
-- ===========================================================================

alter table user_roles
    add column tmp_id bigint not null auto_increment primary key;

delete later_copy
  from user_roles later_copy
  join user_roles first_copy
    on first_copy.user_id = later_copy.user_id
   and first_copy.role_id = later_copy.role_id
   and first_copy.tmp_id < later_copy.tmp_id;

alter table user_roles
    drop primary key,
    drop column tmp_id,
    add primary key (user_id, role_id);
