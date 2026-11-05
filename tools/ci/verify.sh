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
adb emu geo fix 8.68 50.11
adb logcat -c

# 1. App mit echten Daten
adb shell am start -n $PKG/.MainActivity
sleep 25
shot app-0-was-ist-neu
adb shell input keyevent KEYCODE_BACK
sleep 25
shot app-1-oben
adb shell am force-stop $PKG

# Wisch-Test: Wenn weitere Orte fehlen, zeigt das Log trotzdem die Bildrate
adb shell dumpsys gfxinfo $PKG > "$OUT/gfxinfo.txt"

# 2. Update-Ablauf gegen einen lokalen Test-Server
mkdir -p out/upd
cp app/build/outputs/apk/debug/app-debug.apk out/upd/app.apk
SIZE=$(stat -c %s out/upd/app.apk)
cat > out/upd/latest.json <<JSON
{"tag_name":"build-99999","name":"Wetter 9.9 (Build 99999)",
 "body":"- Testeintrag eins\n- Testeintrag zwei\n\n[Verlauf](x)",
 "assets":[{"name":"wetter-99999.apk","size":$SIZE,"browser_download_url":"http://10.0.2.2:8765/app.apk"}]}
JSON
(cd out/upd && nohup python3 -m http.server 8765 >/dev/null 2>&1 &)
sleep 2
adb shell appops set $PKG REQUEST_INSTALL_PACKAGES allow
adb shell dumpsys package $PKG | grep -E "lastUpdateTime|versionCode" > "$OUT/vorher.txt"
adb shell am start -n $PKG/.MainActivity
sleep 20
adb shell am broadcast -n $PKG/.DebugRenderReceiver -a $PKG.DEBUG_UPDATE --es api http://10.0.2.2:8765/latest.json
sleep 10
shot update-1-hinweis
adb shell am broadcast -n $PKG/.DebugRenderReceiver -a $PKG.DEBUG_INSTALL
sleep 3
shot update-2-laden
sleep 15
shot update-3-installer
adb shell uiautomator dump /sdcard/ui2.xml
adb pull /sdcard/ui2.xml "$OUT/ui2.xml"
python3 tools/ci/tap_add.py "$OUT/ui2.xml" | while read -r x y; do adb shell input tap "$x" "$y"; done
sleep 25
shot update-4-fertig
adb shell dumpsys package $PKG | grep -E "lastUpdateTime|versionCode" > "$OUT/nachher.txt"

# 3. Ortsliste mit Versionszeile
adb shell am start -n $PKG/.MainActivity
sleep 15
shot app-2-nach-update

adb logcat -d -s WetterDebug:* WetterMap:* AndroidRuntime:E System.err:W > "$OUT/log.txt"
adb logcat -d | grep -iE "wetter|Widgets|RemoteViews|TransactionTooLarge|exceeds|Fatal signal|has died" | tail -300 > "$OUT/log-all.txt"
pkill -f "http.server 8765" || true
(cd out && zip -qr verify.zip verify)
exit 0
