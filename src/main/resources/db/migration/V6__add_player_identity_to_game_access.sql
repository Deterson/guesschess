alter table game_access
    add column white_player_type varchar(16),
    add column white_player_id uuid,
    add column black_player_type varchar(16),
    add column black_player_id uuid;

alter table game_access
    add constraint game_access_white_player_consistency
        check ((white_player_type is null) = (white_player_id is null)),
    add constraint game_access_black_player_consistency
        check ((black_player_type is null) = (black_player_id is null));
