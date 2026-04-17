<?php

declare(strict_types=1);

require_once __DIR__ . '/../../includes/db.php';

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store, no-cache, must-revalidate, max-age=0');
header('Pragma: no-cache');
header('Access-Control-Allow-Origin: *');
header('Access-Control-Allow-Headers: Content-Type, X-Player-Token');
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');

if (($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'OPTIONS') {
    http_response_code(204);
    exit;
}

const GTN_ROOMS = 'gtn_rooms';
const GTN_PLAYERS = 'gtn_players';
const GTN_MOVES = 'gtn_moves';
const GTN_DISCONNECT_SECONDS = 40;

function gtn_pdo(): PDO
{
    global $pdo;
    if (!$pdo instanceof PDO) {
        gtn_error('Database is unavailable right now.', 500);
    }
    return $pdo;
}

function gtn_has_column(PDO $pdo, string $table, string $column): bool
{
    static $cache = [];
    $cacheKey = $table . '.' . $column;
    if (array_key_exists($cacheKey, $cache)) {
        return $cache[$cacheKey];
    }

    try {
        $stmt = $pdo->prepare('SHOW COLUMNS FROM `' . $table . '` LIKE :column_name');
        $stmt->execute(['column_name' => $column]);
        $cache[$cacheKey] = $stmt->fetch(PDO::FETCH_ASSOC) !== false;
    } catch (Throwable $e) {
        $cache[$cacheKey] = false;
    }

    return $cache[$cacheKey];
}

function gtn_supports_last_seen(PDO $pdo): bool
{
    return gtn_has_column($pdo, GTN_PLAYERS, 'last_seen_at');
}

function gtn_supports_finish_reason(PDO $pdo): bool
{
    return gtn_has_column($pdo, GTN_ROOMS, 'finish_reason');
}

function gtn_json(array $data, int $statusCode = 200): never
{
    http_response_code($statusCode);
    echo json_encode($data, JSON_UNESCAPED_SLASHES);
    exit;
}

function gtn_error(string $message, int $statusCode = 400, array $extra = []): never
{
    gtn_json(array_merge(['ok' => false, 'error' => $message], $extra), $statusCode);
}

function gtn_method(string $expected): void
{
    $actual = strtoupper($_SERVER['REQUEST_METHOD'] ?? 'GET');
    if ($actual !== strtoupper($expected)) {
        gtn_error('Method not allowed', 405);
    }
}

function gtn_input(): array
{
    $raw = file_get_contents('php://input');
    if ($raw === false || trim($raw) === '') {
        return [];
    }

    $decoded = json_decode($raw, true);
    if (!is_array($decoded)) {
        gtn_error('Invalid JSON body');
    }

    return $decoded;
}

function gtn_field(array $data, string $key, string $default = ''): string
{
    $value = $data[$key] ?? $default;
    if (!is_string($value) && !is_numeric($value)) {
        return $default;
    }

    return trim((string) $value);
}

function gtn_room_code(string $value): string
{
    $value = strtoupper(trim($value));
    if (!preg_match('/^[A-Z0-9]{6}$/', $value)) {
        gtn_error('Invalid room code format.');
    }

    return $value;
}

function gtn_app_version(string $value): string
{
    $value = trim($value);
    if ($value === '') {
        gtn_error('Missing appVersion.');
    }

    if (strlen($value) > 20) {
        gtn_error('Invalid appVersion.');
    }

    if (!preg_match('/^[0-9]+(?:\.[0-9]+){1,2}$/', $value)) {
        gtn_error('Invalid appVersion format.');
    }

    return $value;
}

function gtn_app_version_from(array $data): string
{
    $fromBody = gtn_field($data, 'appVersion');
    if ($fromBody !== '') {
        return gtn_app_version($fromBody);
    }

    $fromQuery = trim((string) ($_GET['appVersion'] ?? ''));
    if ($fromQuery !== '') {
        return gtn_app_version($fromQuery);
    }

    $fromHeader = trim((string) ($_SERVER['HTTP_X_APP_VERSION'] ?? ''));
    if ($fromHeader !== '') {
        return gtn_app_version($fromHeader);
    }

    gtn_error('Missing appVersion.');
}

function gtn_require_version_match(array $room, string $appVersion): void
{
    $roomVersion = trim((string) ($room['app_version'] ?? ''));
    if ($roomVersion === '') {
        gtn_error('Room version metadata missing.', 409);
    }

    if ($roomVersion !== $appVersion) {
        gtn_error(
            'Version mismatch. Room is v' . $roomVersion . ' but your app is v' . $appVersion . '.',
            409,
            ['roomVersion' => $roomVersion, 'appVersion' => $appVersion]
        );
    }
}

function gtn_number(string $value): string
{
    $value = trim($value);
    if (!preg_match('/^[1-9]{4}$/', $value)) {
        gtn_error('Number must be 4 digits (1-9).');
    }

    if (count(array_unique(str_split($value))) !== 4) {
        gtn_error('Digits must be unique (no repeats).');
    }

    return $value;
}

function gtn_display_name(string $name, string $fallback): string
{
    $name = trim($name);
    if ($name === '') {
        $name = $fallback;
    }

    if (mb_strlen($name) > 60) {
        $name = mb_substr($name, 0, 60);
    }

    return $name;
}

function gtn_token(): string
{
    return bin2hex(random_bytes(32));
}

function gtn_random_room_code(): string
{
    $alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    $value = '';
    for ($i = 0; $i < 6; $i++) {
        $value .= $alphabet[random_int(0, strlen($alphabet) - 1)];
    }
    return $value;
}

function gtn_unique_room_code(PDO $pdo): string
{
    for ($i = 0; $i < 24; $i++) {
        $candidate = gtn_random_room_code();
        $stmt = $pdo->prepare('SELECT id FROM ' . GTN_ROOMS . ' WHERE room_code = :room_code LIMIT 1');
        $stmt->execute(['room_code' => $candidate]);
        if ($stmt->fetch() === false) {
            return $candidate;
        }
    }

    gtn_error('Failed to create room code. Retry.', 500);
}

function gtn_fetch_room(PDO $pdo, string $roomCode, bool $forUpdate = false): ?array
{
    $sql = 'SELECT * FROM ' . GTN_ROOMS . ' WHERE room_code = :room_code LIMIT 1';
    if ($forUpdate) {
        $sql .= ' FOR UPDATE';
    }

    $stmt = $pdo->prepare($sql);
    $stmt->execute(['room_code' => $roomCode]);
    $row = $stmt->fetch(PDO::FETCH_ASSOC);
    return $row === false ? null : $row;
}

function gtn_fetch_player(PDO $pdo, int $roomId, string $playerToken): ?array
{
    $stmt = $pdo->prepare(
        'SELECT * FROM ' . GTN_PLAYERS . ' WHERE room_id = :room_id AND player_token = :player_token LIMIT 1'
    );
    $stmt->execute([
        'room_id' => $roomId,
        'player_token' => $playerToken,
    ]);
    $row = $stmt->fetch(PDO::FETCH_ASSOC);
    return $row === false ? null : $row;
}

function gtn_fetch_player_by_id(PDO $pdo, int $roomId, int $playerId): ?array
{
    $stmt = $pdo->prepare(
        'SELECT * FROM ' . GTN_PLAYERS . ' WHERE room_id = :room_id AND id = :id LIMIT 1'
    );
    $stmt->execute([
        'room_id' => $roomId,
        'id' => $playerId,
    ]);
    $row = $stmt->fetch(PDO::FETCH_ASSOC);
    return $row === false ? null : $row;
}

function gtn_role(array $room, int $playerId): string
{
    if ((int) $room['host_player_id'] === $playerId) {
        return 'host';
    }
    if ((int) $room['guest_player_id'] === $playerId) {
        return 'guest';
    }
    gtn_error('Player is not in room.', 403);
}

function gtn_touch_player(PDO $pdo, int $playerId): void
{
    if (!gtn_supports_last_seen($pdo)) {
        return;
    }

    $stmt = $pdo->prepare(
        'UPDATE ' . GTN_PLAYERS . ' SET last_seen_at = NOW() WHERE id = :id'
    );
    $stmt->execute(['id' => $playerId]);
}

function gtn_timeout_check(PDO $pdo, array $room, array $player): array
{
    if (!gtn_supports_last_seen($pdo)) {
        return $room;
    }

    if (($room['status'] ?? '') === 'finished') {
        return $room;
    }

    $playerId = (int) $player['id'];
    $role = gtn_role($room, $playerId);
    $opponentId = $role === 'host' ? (int) $room['guest_player_id'] : (int) $room['host_player_id'];
    if ($opponentId <= 0) {
        return $room;
    }

    $opponent = gtn_fetch_player_by_id($pdo, (int) $room['id'], $opponentId);
    if ($opponent === null) {
        return $room;
    }

    $lastSeenRaw = $opponent['last_seen_at'] ?? null;
    $lastSeenTs = is_string($lastSeenRaw) ? strtotime($lastSeenRaw) : false;
    if ($lastSeenTs === false) {
        return $room;
    }

    if ((time() - $lastSeenTs) < GTN_DISCONNECT_SECONDS) {
        return $room;
    }

    $setSql = 'status = :status, winner_player_id = :winner_player_id, is_draw = 0, turn_player_id = NULL';
    $params = [
        'status' => 'finished',
        'winner_player_id' => $playerId,
        'id' => (int) $room['id'],
    ];
    if (gtn_supports_finish_reason($pdo)) {
        $setSql .= ', finish_reason = :finish_reason';
        $params['finish_reason'] = 'opponent_timeout';
    }

    $updateStmt = $pdo->prepare(
        'UPDATE ' . GTN_ROOMS . ' SET ' . $setSql . ' WHERE id = :id'
    );
    $updateStmt->execute($params);

    $freshRoom = gtn_fetch_room($pdo, (string) $room['room_code'], true);
    return $freshRoom ?? $room;
}

function gtn_score(string $secret, string $guess): array
{
    $s = str_split($secret);
    $g = str_split($guess);

    $position = 0;
    for ($i = 0; $i < 4; $i++) {
        if ($s[$i] === $g[$i]) {
            $position++;
        }
    }

    $matched = 0;
    foreach ($g as $digit) {
        if (in_array($digit, $s, true)) {
            $matched++;
        }
    }

    return [$matched, $position];
}

function gtn_outcome(array $room): array
{
    $hostSolved = $room['host_solved_on'] !== null ? (int) $room['host_solved_on'] : null;
    $guestSolved = $room['guest_solved_on'] !== null ? (int) $room['guest_solved_on'] : null;

    $hostAttempts = (int) $room['host_guess_count'];
    $guestAttempts = (int) $room['guest_guess_count'];

    if ($hostSolved !== null && $guestSolved !== null && $hostSolved === $guestSolved) {
        return ['status' => 'finished', 'is_draw' => 1, 'winner_player_id' => null];
    }

    if ($hostSolved !== null && $guestAttempts >= $hostSolved) {
        if ($guestSolved === null || $guestSolved > $hostSolved) {
            return [
                'status' => 'finished',
                'is_draw' => 0,
                'winner_player_id' => (int) $room['host_player_id'],
            ];
        }
    }

    if ($guestSolved !== null && $hostAttempts >= $guestSolved) {
        if ($hostSolved === null || $hostSolved > $guestSolved) {
            return [
                'status' => 'finished',
                'is_draw' => 0,
                'winner_player_id' => (int) $room['guest_player_id'],
            ];
        }
    }

    return ['status' => 'active', 'is_draw' => 0, 'winner_player_id' => null];
}

function gtn_winner_for(array $room, int $playerId): ?string
{
    if (($room['status'] ?? '') !== 'finished') {
        return null;
    }

    if ((int) ($room['is_draw'] ?? 0) === 1) {
        return 'draw';
    }

    return ((int) $room['winner_player_id'] === $playerId) ? 'you' : 'opponent';
}

function gtn_state(PDO $pdo, array $room, array $player): array
{
    $playerId = (int) $player['id'];
    $role = gtn_role($room, $playerId);

    $myAttempts = $role === 'host' ? (int) $room['host_guess_count'] : (int) $room['guest_guess_count'];
    $opponentAttempts = $role === 'host' ? (int) $room['guest_guess_count'] : (int) $room['host_guess_count'];

    $mySecretSubmitted = $role === 'host'
        ? ($room['host_secret_value'] !== null)
        : ($room['guest_secret_value'] !== null);

    $opponentSecretSubmitted = $role === 'host'
        ? ($room['guest_secret_value'] !== null)
        : ($room['host_secret_value'] !== null);

    $mySecretValue = $role === 'host'
        ? $room['host_secret_value']
        : $room['guest_secret_value'];

    $opponentSecretValue = null;
    if (($room['status'] ?? '') === 'finished') {
        $opponentSecretValue = $role === 'host'
            ? $room['guest_secret_value']
            : $room['host_secret_value'];
    }

    $movesStmt = $pdo->prepare(
        'SELECT m.turn_no, m.player_attempt_no, m.guess_value, m.match_count, m.position_count, m.score_code, m.is_correct, m.created_at, p.role ' .
        'FROM ' . GTN_MOVES . ' m ' .
        'JOIN ' . GTN_PLAYERS . ' p ON p.id = m.player_id ' .
        'WHERE m.room_id = :room_id ORDER BY m.turn_no ASC, m.id ASC LIMIT 300'
    );
    $movesStmt->execute(['room_id' => (int) $room['id']]);

    return [
        'roomCode' => $room['room_code'],
        'appVersion' => $room['app_version'] ?? null,
        'status' => $room['status'],
        'finishReason' => array_key_exists('finish_reason', $room) ? $room['finish_reason'] : null,
        'role' => $role,
        'yourTurn' => ((int) $room['turn_player_id'] === $playerId),
        'turnPlayerId' => $room['turn_player_id'] !== null ? (int) $room['turn_player_id'] : null,
        'myAttempts' => $myAttempts,
        'opponentAttempts' => $opponentAttempts,
        'mySecretSubmitted' => $mySecretSubmitted,
        'mySecretValue' => $mySecretValue,
        'opponentSecretSubmitted' => $opponentSecretSubmitted,
        'opponentSecretValue' => $opponentSecretValue,
        'winner' => gtn_winner_for($room, $playerId),
        'isDraw' => (int) ($room['is_draw'] ?? 0) === 1,
        'moves' => $movesStmt->fetchAll(PDO::FETCH_ASSOC),
        'updatedAt' => $room['updated_at'],
        'createdAt' => $room['created_at'],
    ];
}
