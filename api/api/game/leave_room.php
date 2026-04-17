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

    if ($room['status'] !== 'finished') {
        $playerId = (int) $player['id'];
        $role = gtn_role($room, $playerId);
        $winnerId = $role === 'host' ? (int) $room['guest_player_id'] : (int) $room['host_player_id'];

        $setSql = 'status = :status, winner_player_id = :winner_player_id, is_draw = 0, turn_player_id = NULL';
        $params = [
            'status' => 'finished',
            'winner_player_id' => $winnerId > 0 ? $winnerId : null,
            'id' => (int) $room['id'],
        ];
        if (gtn_supports_finish_reason($pdo)) {
            $setSql .= ', finish_reason = :finish_reason';
            $params['finish_reason'] = 'opponent_left';
        }

        $updateStmt = $pdo->prepare(
            'UPDATE ' . GTN_ROOMS . ' SET ' . $setSql . ' WHERE id = :id'
        );
        $updateStmt->execute($params);
    }

    $pdo->commit();

    gtn_json(['ok' => true]);
} catch (Throwable $e) {
    if ($pdo->inTransaction()) {
        $pdo->rollBack();
    }
    gtn_error('Failed to leave room.', 500, ['detail' => $e->getMessage()]);
}
