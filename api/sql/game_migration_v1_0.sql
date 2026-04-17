-- Migration for strict same-version matchmaking (v1.0)
-- Run this on existing databases that already have gtn_rooms.

ALTER TABLE gtn_rooms
    ADD COLUMN IF NOT EXISTS app_version VARCHAR(20) NOT NULL DEFAULT '1.0' AFTER room_code;
