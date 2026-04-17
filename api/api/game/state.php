<?php

declare(strict_types=1);

require_once __DIR__ . '/common.php';

gtn_method('GET');

$pdo = gtn_pdo();

$roomCode = gtn_room_code((string)($_GET['roomCode'] ?? ''));
$playerToken = trim((string)($_GET['playerToken'] ?? ''));
$appVersion = gtn_app_version_from([]);

if ($playerToken === '') {
    $headerToken = $_SERVER['HTTP_X_PLAYER_TOKEN'] ?? '';
    $playerToken = trim((string) $headerToken);
}

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
    $room = gtn_timeout_check($pdo, $room, $player);

    $pdo->commit();

    gtn_json([
        'ok' => true,
        'state' => gtn_state($pdo, $room, $player),
    ]);
} catch (Throwable $e) {
    if ($pdo->inTransaction()) {
        $pdo->rollBack();
    }
    gtn_error('Failed to load state.', 500, ['detail' => $e->getMessage()]);
}
