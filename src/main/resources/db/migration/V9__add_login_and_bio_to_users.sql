-- login (etape 14) : pseudonyme unique et immuable. Nullable au niveau SQL pour ne
-- pas casser les comptes crees avant cette etape (pas de wipe de base) - un index
-- unique sur lower(login) autorise plusieurs NULL (semantique standard Postgres),
-- l'immuabilite et l'obligation d'en choisir un sont appliquees cote application
-- (AccountService), pas par une contrainte NOT NULL.
alter table users add column login varchar(20);
alter table users add column bio varchar(5000) not null default '';

create unique index users_login_unique_idx on users (lower(login));
