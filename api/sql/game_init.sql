-- Guess The Number (Online 1v1) schema
-- Safe for a shared single database: uses gtn_* table prefix.

CREATE TABLE IF NOT EXISTS gtn_rooms (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    room_code CHAR(6) NOT NULL,
    app_version VARCHAR(20) NOT NULL DEFAULT '1.0',
    status ENUM('waiting', 'secret_phase', 'active', 'finished') NOT NULL DEFAULT 'waiting',
    host_player_id BIGINT UNSIGNED DEFAULT NULL,
    guest_player_id BIGINT UNSIGNED DEFAULT NULL,
    turn_player_id BIGINT UNSIGNED DEFAULT NULL,
    host_secret_value CHAR(4) DEFAULT NULL,
    guest_secret_value CHAR(4) DEFAULT NULL,
    host_guess_count INT UNSIGNED NOT NULL DEFAULT 0,
    guest_guess_count INT UNSIGNED NOT NULL DEFAULT 0,
    host_solved_on INT UNSIGNED DEFAULT NULL,
    guest_solved_on INT UNSIGNED DEFAULT NULL,
    winner_player_id BIGINT UNSIGNED DEFAULT NULL,
    is_draw TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_gtn_rooms_room_code (room_code),
    KEY idx_gtn_rooms_status (status),
    KEY idx_gtn_rooms_app_version (app_version),
    KEY idx_gtn_rooms_host_player_id (host_player_id),
    KEY idx_gtn_rooms_guest_player_id (guest_player_id),
    KEY idx_gtn_rooms_turn_player_id (turn_player_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS gtn_players (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    room_id BIGINT UNSIGNED NOT NULL,
    role ENUM('host', 'guest') NOT NULL,
    display_name VARCHAR(60) NOT NULL,
    player_token CHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_gtn_players_token (player_token),
    UNIQUE KEY uq_gtn_players_room_role (room_id, role),
    KEY idx_gtn_players_room_id (room_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS gtn_moves (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    room_id BIGINT UNSIGNED NOT NULL,
    player_id BIGINT UNSIGNED NOT NULL,
    turn_no INT UNSIGNED NOT NULL,
    player_attempt_no INT UNSIGNED NOT NULL,
    guess_value CHAR(4) NOT NULL,
    match_count TINYINT UNSIGNED NOT NULL,
    position_count TINYINT UNSIGNED NOT NULL,
    score_code CHAR(3) NOT NULL,
    is_correct TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_gtn_moves_room_turn (room_id, turn_no),
    KEY idx_gtn_moves_player_id (player_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
