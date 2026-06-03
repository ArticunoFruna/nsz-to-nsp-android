# NSZ → NSP (Android)

Native Android app that converts compressed `.nsz` Switch backups back to their
original `.nsp` form **on-device**, with no PC required. Wraps the open-source
[`nsz`](https://github.com/nicoboss/nsz) Python library via Chaquopy.

Made by [ArticunoFruna](https://github.com/ArticunoFruna).

> ## Legal
> This app **does NOT bundle or distribute any keys, ROMs, or copyrighted
> material**. You must provide your own `prod.keys` dumped from your own
> Nintendo Switch. You are responsible for the legality of files you process.

## Features

- One-tap NSZ → NSP conversion using nsz under the hood
- Batch queue with persistent history (Room)
- Foreground-service worker — keeps converting with screen off
- Material 3 + Dynamic Color (Android 12+), dark/light/system theme
- 100% on-device, no network calls, no telemetry
- English + Spanish (auto-picks from system locale)

## Requirements

- Android **8.0 (API 26)** or higher
- Your own `prod.keys` (dump from your own console with
  [Lockpick_RCM](https://github.com/shchmue/Lockpick_RCM))

## Install

Grab the APK from the [Releases](../../releases) page (once published) and
sideload it. Or build from source — see below.

## Build from source

### Prerequisites

- Android Studio **Hedgehog (2023.1)** or newer
- JDK **17 or 21** (AGP 8.5 supports up to JDK 21)
- Android SDK with platform **34** and NDK
- ~10 GB free during first build (Chaquopy downloads and compiles Python deps)

Chaquopy is free for open-source builds — no license activation required for
local development or release.

### Build commands

```bash
# Clone
git clone https://github.com/ArticunoFruna/nsz-to-nsp-android.git
cd nsz-to-nsp-android

# Debug APK
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

# Release APK (unsigned)
./gradlew assembleRelease
```

The first build takes 15-25 minutes because Chaquopy fetches `nsz` + `zstandard`
from PyPI and compiles their native deps. Subsequent builds take <1 minute.

### Sign a release APK for sideload

```bash
keytool -genkey -v -keystore release.keystore -alias nsz -keyalg RSA -keysize 2048 -validity 10000
$ANDROID_HOME/build-tools/35.0.1/apksigner sign \
  --ks release.keystore \
  --out app-release.apk \
  app/build/outputs/apk/release/app-release-unsigned.apk
```

## APK size

~55 MB (debug) / ~45 MB (release). The bundle ships CPython 3.11 + `nsz` +
`zstandard` with native binaries for `arm64-v8a` and `x86_64`. If you only need
physical devices, drop `x86_64` from `abiFilters` in `app/build.gradle.kts` to
shave ~15 MB.

## Architecture

- **Kotlin + Jetpack Compose** (Material 3)
- **MVVM + Clean Architecture** (`domain` / `data` / `ui` layers)
- **Hilt** for DI, **Room** for history, **DataStore** for preferences
- **WorkManager** + ForegroundService for background conversions
- **Storage Access Framework** for all user-visible I/O
- **Chaquopy 16.x** (CPython 3.11) for the `nsz` runtime

```
app/src/main/
├── python/                # nsz bridge (converter.py, batch_helper.py)
└── java/com/nszconverter/
    ├── di/                # Hilt modules
    ├── data/              # Room, DataStore, repositories
    ├── domain/            # Models + use cases
    ├── ui/                # Compose screens + components + theme
    ├── worker/            # ConversionWorker (WorkManager)
    └── util/              # FileManager (SAF ↔ cache), formatters
```

## How to dump prod.keys

This app **does not provide them**. Dump yours:

1. You need RCM-capable Switch (unpatched units).
2. Use [Lockpick_RCM](https://github.com/shchmue/Lockpick_RCM) payload
   (Hekate / sx OS).
3. Output goes to `/switch/prod.keys` on the SD card.
4. Copy it to your phone and pick it during onboarding (or from Settings).

## Credits

- [nicoboss/nsz](https://github.com/nicoboss/nsz) — NSZ compression/decompression
  library (MIT)
- [Chaquopy](https://chaquo.com/chaquopy/) — Python runtime for Android

## License

MIT — see [LICENSE](LICENSE). The bundled `nsz` library retains its own MIT
license.
