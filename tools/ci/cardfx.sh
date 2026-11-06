#!/bin/bash
# Kurzpruefung der Karteneffekte: Schnee, Frost, Hitze, Regen.
set -x
PKG=de.tonyapps.wetter
OUT=out/verify
mkdir -p "$OUT"
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell settings put global hide_error_dialogs 1
adb shell am start -n $PKG/.MainActivity
sleep 35
for case in "5 0 -2" "10 0 -6" "11 0 33" "11 4 33" "3 0 12"; do
  set -- $case
  adb shell am force-stop $PKG; sleep 3
  adb shell am start -n $PKG/.DebugShotActivity --ei scene "$1" --ei scroll "$2" --ei hi 0 --ef temp "$3"
  sleep 22
  for n in 1 2 3; do shot "fx-$1-$2-$n"; sleep 1; done
done
timeout 60 adb logcat -d -s AndroidRuntime:E > "$OUT/log.txt"
(cd out && zip -qr verify.zip verify)
exit 0
