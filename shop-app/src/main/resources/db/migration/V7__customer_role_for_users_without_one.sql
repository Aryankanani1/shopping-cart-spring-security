-- Accounts created through POST /users were saved with no role; only the dev
-- seeder gave its accounts one. New accounts now get ROLE_CUSTOMER, and this
-- gives it to the existing accounts that have no role, so every account has one.
--
-- On a fresh database this inserts nothing: DataInitializer creates the roles
-- when the app starts, after the migrations have run, and there are no users yet.

insert into user_roles (user_id, role_id)
select u.id, r.id
from users u
join roles r on r.name = 'ROLE_CUSTOMER'
where not exists (select 1 from user_roles ur where ur.user_id = u.id);
