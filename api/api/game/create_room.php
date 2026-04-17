<?php

declare(strict_types=1);

require_once __DIR__ . '/common.php';

gtn_method('POST');

$pdo = gtn_pdo();
$input = gtn_input();
$displayName = gtn_display_name(gtn_field($input, 'displayName', ''), 'Player 1');
$appVersion = gtn_app_version_from($input);

try {
    $pdo->beginTransaction();

    $roomCode = gtn_unique_room_code($pdo);

    $roomStmt = $pdo->prepare(
        'INSERT INTO ' . GTN_ROOMS . ' (room_code, app_version, status) VALUES (:room_code, :app_version, :status)'
    );
    $roomStmt->execute([
        'room_code' => $roomCode,
        'app_version' => $appVersion,
        'status' => 'waiting',
    ]);

    $roomId = (int) $pdo->lastInsertId();
    $playerToken = gtn_token();

    $playerStmt = $pdo->prepare(
        'INSERT INTO ' . GTN_PLAYERS . ' (room_id, role, display_name, player_token) VALUES (:room_id, :role, :display_name, :player_token)'
    );
    $playerStmt->execute([
        'room_id' => $roomId,
        'role' => 'host',
        'display_name' => $displayName,
        'player_token' => $playerToken,
    ]);

    $hostPlayerId = (int) $pdo->lastInsertId();

    $updateStmt = $pdo->prepare(
        'UPDATE ' . GTN_ROOMS . ' SET host_player_id = :host_player_id WHERE id = :id'
    );
    $updateStmt->execute([
        'host_player_id' => $hostPlayerId,
        'id' => $roomId,
    ]);

    $pdo->commit();

    gtn_json([
        'ok' => true,
        'roomCode' => $roomCode,
        'playerToken' => $playerToken,
        'role' => 'host',
        'displayName' => $displayName,
        'appVersion' => $appVersion,
        'status' => 'waiting',
    ], 201);
} catch (Throwable $e) {
    if ($pdo->inTransaction()) {
        $pdo->rollBack();
    }
    gtn_error('Failed to create room.', 500, ['detail' => $e->getMessage()]);
}
