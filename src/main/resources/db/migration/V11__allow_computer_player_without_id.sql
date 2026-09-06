-- Etape 15 : un ordinateur (PlayerRef.Computer) n'a pas d'identifiant de compte/anonyme,
-- seulement un type ("COMPUTER_EASY"/"COMPUTER_MEDIUM"/"COMPUTER_HARD") - la contrainte
-- posee en V6 exigeait que type et id soient tous les deux nuls ou tous les deux non-nuls,
-- ce qui rejetait ce cas (type non-null, id null). Assouplie pour l'autoriser explicitement.
alter table game_access
    drop constraint game_access_white_player_consistency,
    drop constraint game_access_black_player_consistency;

alter table game_access
    add constraint game_access_white_player_consistency
        check (
            (white_player_type is null and white_player_id is null)
            or (white_player_type like 'COMPUTER_%' and white_player_id is null)
            or (white_player_type is not null and white_player_id is not null)
        ),
    add constraint game_access_black_player_consistency
        check (
            (black_player_type is null and black_player_id is null)
            or (black_player_type like 'COMPUTER_%' and black_player_id is null)
            or (black_player_type is not null and black_player_id is not null)
        );
