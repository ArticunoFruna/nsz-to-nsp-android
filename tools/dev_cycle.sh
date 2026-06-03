#!/usr/bin/env bash
# Ciclo de desarrollo automatizado: validate → build → install → monitor logcat.
#
# 1. Valida converter.py contra nsz instalado en el host (rapidísimo, <2s)
# 2. Si pasa, hace ./gradlew assembleDebug
# 3. Reinstala el APK vía adb
# 4. Lanza la app
# 5. Empieza a monitorear logcat filtrando por la app y errores Python
#
# Uso: ./tools/dev_cycle.sh [--no-monitor]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

export JAVA_HOME="${JAVA_HOME:-$HOME/.jdks/jdk-21.0.5+11}"
export PATH="$JAVA_HOME/bin:$PATH"
ADB="${ADB:-$HOME/Android/Sdk/platform-tools/adb}"

echo "==> [1/4] Host validation of converter.py"
if python3 tools/host_validate.py; then
    echo "    OK"
else
    echo "    FAILED — fix converter.py before continuing"
    exit 1
fi

echo "==> [2/4] gradlew assembleDebug"
./gradlew assembleDebug --no-daemon -q

echo "==> [3/4] adb install -r"
"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk | tail -1

echo "==> [4/4] launching app"
"$ADB" shell monkey -p com.nszconverter.debug -c android.intent.category.LAUNCHER 1 >/dev/null

if [[ "${1:-}" == "--no-monitor" ]]; then
    echo
    echo "Done. (logcat monitoring skipped)"
    exit 0
fi

echo
echo "==> Monitoring logcat — filter: Python errors + ConversionWorker + nsz"
echo "    (Ctrl+C to stop)"
echo

"$ADB" logcat -c
"$ADB" logcat \
    --pid="$("$ADB" shell pidof com.nszconverter.debug 2>/dev/null || echo 0)" \
    | grep -E --line-buffered "ConversionWorker|python|chaquopy|nsz|AndroidRuntime|FATAL|E/"
