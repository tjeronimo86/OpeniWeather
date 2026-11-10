#!/bin/bash
# Prueft, dass ein Widget nach einem Wetterwechsel keine alten Bilder mehr zeigt.
set -x
PKG=de.tonyapps.wetter
OUT=out/verify
mkdir -p "$OUT"
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell settings put global hide_error_dialogs 1
adb shell am start -n $PKG/.MainActivity
sleep 35
adb shell am broadcast -n $PKG/.DebugRenderReceiver -a $PKG.DEBUG_PIN --es style MEDIUM
sleep 6
adb shell uiautomator dump /sdcard/ui.xml
adb pull /sdcard/ui.xml "$OUT/ui.xml"
python3 tools/ci/tap_add.py "$OUT/ui.xml" | while read -r x y; do adb shell input tap "$x" "$y"; done
sleep 8
adb shell input keyevent KEYCODE_HOME
# erst Regen, dann klarer Himmel
adb shell am broadcast -n $PKG/.DebugRenderReceiver -a $PKG.DEBUG_SCENE --ei scene 3
sleep 70
for n in 1 2 3; do shot "a-regen-$n"; sleep 1; done
adb shell am broadcast -n $PKG/.DebugRenderReceiver -a $PKG.DEBUG_SCENE --ei scene 0
sleep 70
# Schleife vermessen: Szene direkt vor der Aufnahme setzen, sonst ueberschreibt echtes Wetter sie
for k in 1 2; do
  adb shell am broadcast -n $PKG/.DebugRenderReceiver -a $PKG.DEBUG_SCENE --ei scene 2
  sleep 45
done
adb shell screenrecord --time-limit 60 --size 540x1200 --bit-rate 6000000 /sdcard/loop.mp4
sleep 2
adb pull /sdcard/loop.mp4 "$OUT/loop.mp4"
timeout 60 adb logcat -d -s AndroidRuntime:E Widgets:W > "$OUT/log.txt"
(cd out && zip -qr verify.zip verify)
exit 0
