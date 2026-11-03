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
# Der Software-Shader ist hier langsam; Systemdialoge duerfen die Taps nicht blockieren
adb shell settings put global hide_error_dialogs 1
adb emu geo fix 8.85 50.07
adb logcat -c

# 1. App mit echten Daten
adb shell am start -n $PKG/.MainActivity
sleep 50
shot app-1-oben
adb shell am force-stop $PKG

# Wisch-Test: Wenn weitere Orte fehlen, zeigt das Log trotzdem die Bildrate
adb shell dumpsys gfxinfo $PKG > "$OUT/gfxinfo.txt"

# 2. Karte allein, damit der Emulator bedienbar bleibt
adb shell am start -n $PKG/.DebugMapActivity
sleep 25
shot map-1-radar
adb shell input tap 950 290
sleep 70
shot map-2-temperatur
adb shell input tap 950 400
sleep 15
shot map-3-wind

# 3. Widgets als PNG
adb shell am broadcast -n $PKG/.DebugRenderReceiver -a $PKG.DEBUG_RENDER
for n in $(seq 1 40); do
  adb logcat -d -s WetterDebug:* | grep -q "render done" && break
  sleep 5
done
adb pull /sdcard/Android/data/$PKG/files/widgets "$OUT/widgets"

# 4. Echtes Widget: Pin-Dialog bestaetigen, dann Startbildschirm
adb shell am broadcast -n $PKG/.DebugRenderReceiver -a $PKG.DEBUG_PIN --es style LARGE
sleep 6
shot pin-dialog
adb shell uiautomator dump /sdcard/ui.xml
adb pull /sdcard/ui.xml "$OUT/ui.xml"
python3 tools/ci/tap_add.py "$OUT/ui.xml" | while read -r x y; do adb shell input tap "$x" "$y"; done
sleep 8
adb shell input keyevent KEYCODE_HOME
sleep 75
for n in 1 2 3 4 5 6; do shot home-widget-$n; sleep 0.2; done
cp "$OUT/home-widget-1.png" "$OUT/home-widget.png"
# Gewitter als schwerster Fall fuer die Speichergrenze
adb shell am broadcast -n $PKG/.DebugRenderReceiver -a $PKG.DEBUG_SCENE --ei scene 4
sleep 60
for n in 1 2 3 4 5 6 7 8; do shot storm-$n; sleep 0.2; done
adb shell dumpsys appwidget > "$OUT/appwidget.txt"

adb logcat -d -s WetterDebug:* | grep -q "render done" && break
  sleep 5
done
adb pull /sdcard/Android/data/$PKG/files/widgets "$OUT/widgets"

# 4. Echtes Widget: Pin-Dialog bestaetigen, dann Startbildschirm
adb shell am broadcast -n $PKG/.DebugRenderReceiver -a $PKG.DEBUG_PIN --es style LARGE
sleep 6
shot pin-dialog
adb shell uiautomator dump /sdcard/ui.xml
adb pull /sdcard/ui.xml "$OUT/ui.xml"
python3 tools/ci/tap_add.py "$OUT/ui.xml" | while read -r x y; do adb shell input tap "$x" "$y"; done
sleep 8
adb shell input keyevent KEYCODE_HOME
sleep 75
for n in 1 2 3 4 5 6; do shot home-widget-$n; sleep 0.2; done
cp "$OUT/home-widget-1.png" "$OUT/home-widget.png"

adb logcat -d -s WetterDebug:* WetterMap:* AndroidRuntime:E System.err:W > "$OUT/log.txt"
adb logcat -d | grep -iE "wetter|Widgets|RemoteViews|TransactionTooLarge|exceeds|Fatal signal|has died" | tail -300 > "$OUT/log-all.txt"
(cd out && zip -qr verify.zip verify)
exit 0
