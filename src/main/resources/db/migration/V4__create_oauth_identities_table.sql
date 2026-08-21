create table oauth_identities (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    provider varchar(20) not null,
    external_id varchar(255) not null,
    created_at timestamptz not null default now(),
    unique (provider, external_id)
);
