#!/bin/bash
cd "$(dirname "$0")"

export JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home 2>/dev/null)

PKG="com.ai.voice"

echo "=== Cleaning build ==="
./gradlew clean -q


echo "=== Building homeDebug ==="
./gradlew assembleHomeDebug -q || exit 1

APK=$(find app/build/outputs/apk/home/debug -name "*.apk" | head -1)
echo "=== Installing $APK ==="

if ! adb install -r "$APK"; then
    echo "=== Install failed, trying uninstall first ==="
    adb uninstall $PKG 2>/dev/null
    adb install "$APK" || exit 1
fi

echo "=== Granting permissions ==="
adb shell pm grant $PKG android.permission.RECORD_AUDIO
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
adb shell appops set $PKG RECORD_AUDIO allow
adb shell appops set $PKG POST_NOTIFICATION allow

echo "=== Starting app ==="
adb shell am start -n $PKG/.ui.floating.FloatingLauncherActivity

sleep 2
PID=$(adb shell pidof $PKG)
echo "=== PID: $PID ==="

echo "=== Logcat (Ctrl+C to exit) ==="
adb logcat --pid=$PID
