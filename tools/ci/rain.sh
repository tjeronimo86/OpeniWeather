#!/bin/bash
# Kurzpruefung: Regen auf den Karten, mehrere Bilder kurz hintereinander.
set -x
PKG=de.tonyapps.wetter
OUT=out/verify
mkdir -p "$OUT"
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell settings put global hide_error_dialogs 1
adb shell am start -n $PKG/.MainActivity
sleep 35
for case in "3 0" "3 4" "4 2"; do
  set -- $case
  adb shell am force-stop $PKG; sleep 3
  adb shell am start -n $PKG/.DebugShotActivity --ei scene "$1" --ei scroll "$2" --ei hi 0
  sleep 20
  for n in 1 2 3 4 5 6; do shot "rain-$1-$2-$n"; done
done
timeout 60 adb logcat -d -s AndroidRuntime:E > "$OUT/log.txt"
(cd out && zip -qr verify.zip verify)
exit 0
