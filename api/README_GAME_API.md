# Guess The Number Online API (Single Subdomain + Single DB)

This package is designed for your existing hosting constraints:

- One subdomain only (`https://notices.i-inaya.com`)
- One existing MySQL database only
- Existing site already running

## Where files are placed

Game endpoints are isolated under:

- `api/api/game/`

So your existing pages stay untouched.

## Step 1: Create game tables (same database)

Run this SQL in phpMyAdmin:

- `api/sql/game_init.sql`
- If tables already exist, run: `api/sql/game_migration_v1_0.sql`
- For 40s inactivity auto-disconnect, also run: `api/sql/game_migration_disconnect_40s.sql`

It creates only prefixed tables:

- `gtn_rooms`
- `gtn_players`
- `gtn_moves`

No existing table is modified.

## Step 2: API URLs

Base: `https://notices.i-inaya.com/api/game/`

- `POST create_room.php`
- `POST join_room.php`
- `POST submit_secret.php`
- `POST submit_guess.php`
- `GET state.php`
- `POST rematch.php`
- `POST leave_room.php`
- `GET health.php`

## Step 3: Request/Response examples

### 1) Create room

`POST /api/game/create_room.php`

```json
{
  "displayName": "Player 1",
  "appVersion": "1.0"
}
```

Returns `roomCode` + `playerToken` + `appVersion`.

### 2) Join room

`POST /api/game/join_room.php`

```json
{
  "roomCode": "ABC123",
  "displayName": "Player 2",
  "appVersion": "1.0"
}
```

Returns `playerToken` for guest.

### 3) Submit secret (unique 4 digits, 1-9)

`POST /api/game/submit_secret.php`

```json
{
  "roomCode": "ABC123",
  "playerToken": "<token>",
  "secret": "5831",
  "appVersion": "1.0"
}
```

### 4) Submit guess

`POST /api/game/submit_guess.php`

```json
{
  "roomCode": "ABC123",
  "playerToken": "<token>",
  "guess": "1748",
  "appVersion": "1.0"
}
```

Returns score like `"2-1"`.

### 5) Poll state

`GET /api/game/state.php?roomCode=ABC123&playerToken=<token>&appVersion=1.0`

Poll every 1-2 seconds during active gameplay.

State response includes:

- `mySecretValue` (always your own secret)
- `opponentSecretValue` (revealed only when match status is `finished`)

### 6) Start rematch (after match finish)

`POST /api/game/rematch.php`

```json
{
  "roomCode": "ABC123",
  "playerToken": "<token>",
  "appVersion": "1.0"
}
```

Resets game to `secret_phase` with same players and same room code.

## Game rules enforced server-side

- Exactly 4 digits
- Digits `1-9`
- No repeated digits
- Turn-based enforcement
- Fair ending rule (opponent gets equal attempt count)
- Same-round solve => draw
- Strict version match: room players must use the exact same `appVersion`
- Auto-disconnect: if a player is inactive for 40+ seconds, the opponent wins by timeout

## Important security note

Your current `api/includes/config.php` contains plain credentials and OAuth secrets in code.
Move those into environment variables or at least rotate them if the repo is shared.
