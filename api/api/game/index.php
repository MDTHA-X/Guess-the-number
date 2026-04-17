<?php

declare(strict_types=1);

require_once __DIR__ . '/common.php';

gtn_json([
    'ok' => true,
    'service' => 'Guess The Number API',
    'endpoints' => [
        'GET health.php',
        'POST create_room.php',
        'POST join_room.php',
        'POST submit_secret.php',
        'POST submit_guess.php',
        'GET state.php?roomCode=...&playerToken=...&appVersion=1.0',
        'POST rematch.php',
        'POST leave_room.php',
    ],
]);
