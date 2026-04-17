-- Migration for auto-disconnect timeout support (40s inactivity)
-- Run this after game_init/game_migration_v1_0 on existing databases.

ALTER TABLE gtn_players
    ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER player_token;

ALTER TABLE gtn_rooms
    ADD COLUMN IF NOT EXISTS finish_reason ENUM('guess', 'opponent_left', 'opponent_timeout') DEFAULT NULL AFTER is_draw;
