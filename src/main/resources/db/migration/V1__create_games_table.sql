create table games (
    id uuid primary key,
    status varchar(20) not null,
    result_winner varchar(10),
    result_cause varchar(40),
    side_to_move varchar(5) not null,
    state jsonb not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
