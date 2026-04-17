<?php

declare(strict_types=1);

require_once __DIR__ . '/common.php';

gtn_method('POST');

$pdo = gtn_pdo();
$input = gtn_input();
$roomCode = gtn_room_code(gtn_field($input, 'roomCode'));
$displayName = gtn_display_name(gtn_field($input, 'displayName', ''), 'Player 2');
$appVersion = gtn_app_version_from($input);

try {
    $pdo->beginTransaction();

    $room = gtn_fetch_room($pdo, $roomCode, true);
    if ($room === null) {
        $pdo->rollBack();
        gtn_error('Room not found.', 404);
    }

    if ($room['status'] === 'finished') {
        $pdo->rollBack();
        gtn_error('Room is already finished.', 409);
    }

    gtn_require_version_match($room, $appVersion);

    if ($room['guest_player_id'] !== null) {
        $pdo->rollBack();
        gtn_error('Room already has 2 players.', 409);
    }

    $playerToken = gtn_token();

    $playerStmt = $pdo->prepare(
        'INSERT INTO ' . GTN_PLAYERS . ' (room_id, role, display_name, player_token) VALUES (:room_id, :role, :display_name, :player_token)'
    );
    $playerStmt->execute([
        'room_id' => (int) $room['id'],
        'role' => 'guest',
        'display_name' => $displayName,
        'player_token' => $playerToken,
    ]);

    $guestPlayerId = (int) $pdo->lastInsertId();

    $status = 'secret_phase';

    $updateStmt = $pdo->prepare(
        'UPDATE ' . GTN_ROOMS . ' SET guest_player_id = :guest_player_id, status = :status, turn_player_id = NULL WHERE id = :id'
    );
    $updateStmt->execute([
        'guest_player_id' => $guestPlayerId,
        'status' => $status,
        'id' => (int) $room['id'],
    ]);

    $pdo->commit();

    gtn_json([
        'ok' => true,
        'roomCode' => $roomCode,
        'playerToken' => $playerToken,
        'role' => 'guest',
        'displayName' => $displayName,
        'appVersion' => $room['app_version'],
        'status' => $status,
    ]);
} catch (Throwable $e) {
    if ($pdo->inTransaction()) {
        $pdo->rollBack();
    }
    gtn_error('Failed to join room.', 500, ['detail' => $e->getMessage()]);
}
