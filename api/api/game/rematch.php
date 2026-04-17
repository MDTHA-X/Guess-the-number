<?php

declare(strict_types=1);

require_once __DIR__ . '/common.php';

gtn_method('POST');

$pdo = gtn_pdo();
$input = gtn_input();

$roomCode = gtn_room_code(gtn_field($input, 'roomCode'));
$playerToken = gtn_field($input, 'playerToken');
$appVersion = gtn_app_version_from($input);

if ($playerToken === '') {
    gtn_error('Missing playerToken.');
}

try {
    $pdo->beginTransaction();

    $room = gtn_fetch_room($pdo, $roomCode, true);
    if ($room === null) {
        $pdo->rollBack();
        gtn_error('Room not found.', 404);
    }

    gtn_require_version_match($room, $appVersion);

    $player = gtn_fetch_player($pdo, (int) $room['id'], $playerToken);
    if ($player === null) {
        $pdo->rollBack();
        gtn_error('Invalid player token.', 403);
    }

    gtn_touch_player($pdo, (int) $player['id']);

    if ($room['host_player_id'] === null || $room['guest_player_id'] === null) {
        $pdo->rollBack();
        gtn_error('Cannot rematch without 2 players.', 409);
    }

    if ($room['status'] !== 'finished') {
        $pdo->rollBack();
        gtn_error('Rematch is available only after match finishes.', 409);
    }

    $deleteMovesStmt = $pdo->prepare('DELETE FROM ' . GTN_MOVES . ' WHERE room_id = :room_id');
    $deleteMovesStmt->execute(['room_id' => (int) $room['id']]);

    $setSql =
        'status = :status, ' .
        'turn_player_id = NULL, ' .
        'host_secret_value = NULL, ' .
        'guest_secret_value = NULL, ' .
        'host_guess_count = 0, ' .
        'guest_guess_count = 0, ' .
        'host_solved_on = NULL, ' .
        'guest_solved_on = NULL, ' .
        'winner_player_id = NULL, ' .
        'is_draw = 0';

    $params = [
        'status' => 'secret_phase',
        'id' => (int) $room['id'],
    ];

    if (gtn_supports_finish_reason($pdo)) {
        $setSql .= ', finish_reason = NULL';
    }

    $resetRoomStmt = $pdo->prepare(
        'UPDATE ' . GTN_ROOMS . ' SET ' . $setSql . ' WHERE id = :id'
    );
    $resetRoomStmt->execute($params);

    $pdo->commit();

    $freshRoom = gtn_fetch_room($pdo, $roomCode, false);
    if ($freshRoom === null) {
        gtn_error('Room disappeared unexpectedly.', 500);
    }

    gtn_json([
        'ok' => true,
        'state' => gtn_state($pdo, $freshRoom, $player),
    ]);
} catch (Throwable $e) {
    if ($pdo->inTransaction()) {
        $pdo->rollBack();
    }
    gtn_error('Failed to start rematch.', 500, ['detail' => $e->getMessage()]);
}
