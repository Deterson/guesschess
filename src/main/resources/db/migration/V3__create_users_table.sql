create table users (
    id uuid primary key,
    display_name varchar(255) not null,
    email varchar(320),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
