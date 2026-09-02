create table account_settings (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    setting_key varchar(50) not null,
    setting_value varchar(255) not null,
    updated_at timestamptz not null default now(),
    unique (user_id, setting_key)
);
