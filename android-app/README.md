# Guess The Number Online (Android)

This Android app connects to your live API at:

- `https://notices.i-inaya.com/api/game/*`

## Features

- Offline solo mode with local save/resume
- Create room / join by room code
- Submit secret number (4 digits, 1-9, unique)
- Turn-based online guessing with polling
- Strict online version matchmaking (`1.0` only with `1.0`)
- Move history with `x-y` score format
- Fair winner logic handled server-side
- Sound effects for turns, success, errors, and game result
- Session restore using DataStore
- About Me page inside the app

## Build

```bash
cd android-app
JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home ./gradlew :app:assembleDebug
```

Generated debug APK:

- `android-app/app/build/outputs/apk/debug/app-debug.apk`

## Signed Release Build

### Option A: Android Studio (easiest)

1. Open `android-app` in Android Studio.
2. Go to `Build > Generate Signed Bundle / APK`.
3. Choose `Android App Bundle` (`.aab`) or `APK`.
4. Create/select your keystore and fill alias/password.
5. Build `release`.

### Option B: CLI (configured in this project)

1. Copy `keystore.properties.example` to `keystore.properties`.
2. Fill your real values:
   - `storeFile`
   - `storePassword`
   - `keyAlias`
   - `keyPassword`
3. Put the keystore file in the `android-app` folder (or use relative path in `storeFile`).
4. Build:

```bash
cd android-app
JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home ./gradlew :app:bundleRelease
```

Release outputs:

- AAB: `app/build/outputs/bundle/release/app-release.aab`
- APK: `app/build/outputs/apk/release/app-release.apk`

## API Base URL

Configured in:

- `app/src/main/java/com/iinaya/gtnonline/data/remote/NetworkModule.kt`

If your domain changes, update `BASE_URL` there.
