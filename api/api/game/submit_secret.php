<?php

declare(strict_types=1);

require_once __DIR__ . '/common.php';

gtn_method('POST');

$pdo = gtn_pdo();
$input = gtn_input();

$roomCode = gtn_room_code(gtn_field($input, 'roomCode'));
$playerToken = gtn_field($input, 'playerToken');
$secret = gtn_number(gtn_field($input, 'secret'));
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

    if ($room['status'] === 'finished') {
        $pdo->rollBack();
        gtn_error('Room already finished.', 409);
    }

    gtn_require_version_match($room, $appVersion);

    $player = gtn_fetch_player($pdo, (int) $room['id'], $playerToken);
    if ($player === null) {
        $pdo->rollBack();
        gtn_error('Invalid player token.', 403);
    }

    gtn_touch_player($pdo, (int) $player['id']);
    $room = gtn_timeout_check($pdo, $room, $player);
    if (($room['status'] ?? '') === 'finished') {
        $pdo->commit();
        gtn_json([
            'ok' => true,
            'status' => $room['status'],
            'gameStarted' => false,
            'state' => gtn_state($pdo, $room, $player),
        ]);
    }

    $playerId = (int) $player['id'];
    $role = gtn_role($room, $playerId);

    if ($role === 'host' && $room['host_secret_value'] !== null) {
        $pdo->rollBack();
        gtn_error('Host secret already submitted.', 409);
    }

    if ($role === 'guest' && $room['guest_secret_value'] !== null) {
        $pdo->rollBack();
        gtn_error('Guest secret already submitted.', 409);
    }

    if ($role === 'host') {
        $room['host_secret_value'] = $secret;
    } else {
        $room['guest_secret_value'] = $secret;
    }

    $bothPlayersPresent = $room['host_player_id'] !== null && $room['guest_player_id'] !== null;
    $bothSecretsReady = $room['host_secret_value'] !== null && $room['guest_secret_value'] !== null;

    $newStatus = $room['status'];
    $newTurnPlayerId = null;

    if (!$bothPlayersPresent) {
        $newStatus = 'waiting';
    } elseif ($bothSecretsReady) {
        $newStatus = 'active';
        $newTurnPlayerId = (int) $room['host_player_id'];
    } else {
        $newStatus = 'secret_phase';
    }

    $updateStmt = $pdo->prepare(
        'UPDATE ' . GTN_ROOMS . ' SET host_secret_value = :host_secret_value, guest_secret_value = :guest_secret_value, status = :status, turn_player_id = :turn_player_id WHERE id = :id'
    );
    $updateStmt->execute([
        'host_secret_value' => $room['host_secret_value'],
        'guest_secret_value' => $room['guest_secret_value'],
        'status' => $newStatus,
        'turn_player_id' => $newTurnPlayerId,
        'id' => (int) $room['id'],
    ]);

    $pdo->commit();

    $freshRoom = gtn_fetch_room($pdo, $roomCode, false);
    if ($freshRoom === null) {
        gtn_error('Room disappeared unexpectedly.', 500);
    }

    gtn_json([
        'ok' => true,
        'status' => $freshRoom['status'],
        'gameStarted' => $freshRoom['status'] === 'active',
        'state' => gtn_state($pdo, $freshRoom, $player),
    ]);
} catch (Throwable $e) {
    if ($pdo->inTransaction()) {
        $pdo->rollBack();
    }
    gtn_error('Failed to submit secret.', 500, ['detail' => $e->getMessage()]);
}
