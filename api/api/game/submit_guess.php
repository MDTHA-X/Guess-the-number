<?php

declare(strict_types=1);

require_once __DIR__ . '/common.php';

gtn_method('POST');

$pdo = gtn_pdo();
$input = gtn_input();

$roomCode = gtn_room_code(gtn_field($input, 'roomCode'));
$playerToken = gtn_field($input, 'playerToken');
$guess = gtn_number(gtn_field($input, 'guess'));
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

    if ($room['status'] !== 'active') {
        $pdo->rollBack();
        gtn_error('Game is not active.', 409);
    }

    gtn_require_version_match($room, $appVersion);

    $player = gtn_fetch_player($pdo, (int) $room['id'], $playerToken);
    if ($player === null) {
        $pdo->rollBack();
        gtn_error('Invalid player token.', 403);
    }

    $playerId = (int) $player['id'];
    $role = gtn_role($room, $playerId);

    if ((int) $room['turn_player_id'] !== $playerId) {
        $pdo->rollBack();
        gtn_error('It is not your turn.', 409);
    }

    $opponentId = $role === 'host' ? (int) $room['guest_player_id'] : (int) $room['host_player_id'];
    if ($opponentId <= 0) {
        $pdo->rollBack();
        gtn_error('Opponent is missing.', 409);
    }

    $secretToGuess = $role === 'host' ? $room['guest_secret_value'] : $room['host_secret_value'];
    if ($secretToGuess === null) {
        $pdo->rollBack();
        gtn_error('Opponent secret is not set yet.', 409);
    }

    [$matchCount, $positionCount] = gtn_score((string) $secretToGuess, $guess);
    $isCorrect = ($positionCount === 4);

    $hostGuessCount = (int) $room['host_guess_count'];
    $guestGuessCount = (int) $room['guest_guess_count'];

    $hostSolvedOn = $room['host_solved_on'] !== null ? (int) $room['host_solved_on'] : null;
    $guestSolvedOn = $room['guest_solved_on'] !== null ? (int) $room['guest_solved_on'] : null;

    if ($role === 'host') {
        $hostGuessCount++;
        $playerAttemptNo = $hostGuessCount;
        if ($isCorrect && $hostSolvedOn === null) {
            $hostSolvedOn = $playerAttemptNo;
        }
    } else {
        $guestGuessCount++;
        $playerAttemptNo = $guestGuessCount;
        if ($isCorrect && $guestSolvedOn === null) {
            $guestSolvedOn = $playerAttemptNo;
        }
    }

    $turnNo = $hostGuessCount + $guestGuessCount;

    $room['host_guess_count'] = $hostGuessCount;
    $room['guest_guess_count'] = $guestGuessCount;
    $room['host_solved_on'] = $hostSolvedOn;
    $room['guest_solved_on'] = $guestSolvedOn;

    $outcome = gtn_outcome($room);

    $newStatus = $outcome['status'];
    $newWinnerId = $outcome['winner_player_id'];
    $newIsDraw = $outcome['is_draw'];
    $newTurnPlayerId = $newStatus === 'active' ? $opponentId : null;

    $moveStmt = $pdo->prepare(
        'INSERT INTO ' . GTN_MOVES . ' (room_id, player_id, turn_no, player_attempt_no, guess_value, match_count, position_count, score_code, is_correct) ' .
        'VALUES (:room_id, :player_id, :turn_no, :player_attempt_no, :guess_value, :match_count, :position_count, :score_code, :is_correct)'
    );
    $moveStmt->execute([
        'room_id' => (int) $room['id'],
        'player_id' => $playerId,
        'turn_no' => $turnNo,
        'player_attempt_no' => $playerAttemptNo,
        'guess_value' => $guess,
        'match_count' => $matchCount,
        'position_count' => $positionCount,
        'score_code' => $matchCount . '-' . $positionCount,
        'is_correct' => $isCorrect ? 1 : 0,
    ]);

    $updateStmt = $pdo->prepare(
        'UPDATE ' . GTN_ROOMS . ' SET ' .
        'host_guess_count = :host_guess_count, ' .
        'guest_guess_count = :guest_guess_count, ' .
        'host_solved_on = :host_solved_on, ' .
        'guest_solved_on = :guest_solved_on, ' .
        'status = :status, ' .
        'winner_player_id = :winner_player_id, ' .
        'is_draw = :is_draw, ' .
        'turn_player_id = :turn_player_id ' .
        'WHERE id = :id'
    );
    $updateStmt->execute([
        'host_guess_count' => $hostGuessCount,
        'guest_guess_count' => $guestGuessCount,
        'host_solved_on' => $hostSolvedOn,
        'guest_solved_on' => $guestSolvedOn,
        'status' => $newStatus,
        'winner_player_id' => $newWinnerId,
        'is_draw' => $newIsDraw,
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
        'score' => $matchCount . '-' . $positionCount,
        'matchCount' => $matchCount,
        'positionCount' => $positionCount,
        'isCorrect' => $isCorrect,
        'state' => gtn_state($pdo, $freshRoom, $player),
    ]);
} catch (Throwable $e) {
    if ($pdo->inTransaction()) {
        $pdo->rollBack();
    }
    gtn_error('Failed to submit guess.', 500, ['detail' => $e->getMessage()]);
}
