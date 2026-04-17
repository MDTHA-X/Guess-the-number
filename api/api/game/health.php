<?php

declare(strict_types=1);

require_once __DIR__ . '/common.php';

$pdo = gtn_pdo();
$stmt = $pdo->query('SELECT 1');
$stmt->fetchColumn();

gtn_json([
    'ok' => true,
    'service' => 'guess-the-number-game-api',
    'time' => gmdate('c'),
]);
