-- Pendule (etape 12) : la cadence, les temps restants et l'etat de la pendule active
-- vivent dans la colonne JSONB games.state, comme le reste de l'etat riche de la
-- partie. Seule cette colonne est denormalisee, pour que le scheduler de flag-fall
-- (GameClockScheduler) puisse trouver les parties dont le temps est ecoule par un
-- index plutot que de desincapsuler le JSONB de chaque partie ONGOING a chaque tick.
-- Null = pas de pendule active (correspondance, pendule pas encore demarree en
-- attendant le second joueur, ou partie terminee).
alter table games add column clock_deadline_at timestamptz;

create index games_clock_deadline_idx on games (clock_deadline_at) where clock_deadline_at is not null;
