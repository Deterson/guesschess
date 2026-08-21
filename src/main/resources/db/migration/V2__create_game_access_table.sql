create table game_access (
    game_id uuid primary key references games(id),
    white_token uuid not null unique,
    black_token uuid not null unique,
    created_at timestamptz not null default now()
);
