#!/bin/bash
# Laeuft im Emulator-Job: installiert die App, holt echte Daten, sammelt Screenshots und Logs.
set -x
PKG=de.tonyapps.wetter
OUT=out/verify
mkdir -p "$OUT"
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant $PKG android.permission.ACCESS_FINE_LOCATION
adb shell pm grant $PKG android.permission.ACCESS_COARSE_LOCATION
adb emu geo fix 8.85 50.07
adb logcat -c
adb shell am start -n $PKG/.MainActivity
sleep 40
shot app-1-oben
adb shell input swipe 500 1800 500 700 700
sleep 4
shot app-2-karten
adb shell input swipe 500 1800 500 500 700
sleep 4
shot app-3-kacheln
adb shell input swipe 500 1800 500 500 700
sleep 4
shot app-4-kacheln
# Karte: Symbol unten links, dann die drei Ebenen
adb shell input tap 110 2240
sleep 18
shot map-1-radar
adb shell input tap 950 300
sleep 10
shot map-2-temperatur
adb shell input tap 950 410
sleep 10
shot map-3-wind
adb shell input keyevent KEYCODE_BACK
sleep 3
adb shell am broadcast -n $PKG/.DebugRenderReceiver -a $PKG.DEBUG_RENDER
sleep 25
adb pull /sdcard/Android/data/$PKG/files/widgets "$OUT/widgets"

# Echtes Widget auf dem Startbildschirm: Pin-Dialog anfragen und bestaetigen
adb shell input keyevent KEYCODE_HOME
sleep 3
adb shell am broadcast -n $PKG/.DebugRenderReceiver -a $PKG.DEBUG_PIN --es style LARGE
sleep 5
shot pin-dialog
adb shell uiautomator dump /sdcard/ui.xml
adb pull /sdcard/ui.xml "$OUT/ui.xml"
python3 tools/ci/tap_add.py "$OUT/ui.xml" | while read -r x y; do adb shell input tap "$x" "$y"; done
sleep 12
shot home-widget

adb logcat -d -s WetterDebug:* WetterMap:* AndroidRuntime:E System.err:W > "$OUT/log.txt"
adb logcat -d | grep -iE "wetter|RemoteViews|TransactionTooLarge" | tail -200 > "$OUT/log-all.txt"
(cd out && zip -qr verify.zip verify)
exit 0
