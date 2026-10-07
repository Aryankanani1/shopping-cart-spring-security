-- Category names must be unique. The app looks categories up by name and
-- expects at most one match (CategoryRepository.findByName), but nothing stopped
-- a rename from creating a second category with a taken name, and after that
-- every lookup of that name failed.
--
-- Duplicates already in the database are merged first, or the constraint can't
-- be added: products move to the oldest category of each name (lowest id), and
-- the other copies are deleted. Names are compared with the column's collation,
-- the same way the constraint compares them (so "Books" and "books" are one).

update product p
    join category c on c.id = p.category_id
    join (select name, min(id) as keep_id from category group by name) k on k.name = c.name
set p.category_id = k.keep_id
where c.id <> k.keep_id;

delete c from category c
    join (select name, min(id) as keep_id from category group by name) k on k.name = c.name
where c.id <> k.keep_id;

alter table category
    add constraint uk_category_name unique (name);
