#!/bin/bash
# Laeuft im Emulator-Job: prueft alle Sprachen und nimmt die Screenshots fuers README auf.
set -x
PKG=de.tonyapps.wetter
OUT=out/verify
mkdir -p "$OUT"
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
page() { adb shell am force-stop $PKG; sleep 3; adb shell am start -n $PKG/.DebugShotActivity --ei scene "$1" --ei scroll "$2" --ei hi "$3"; }

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell settings put global hide_error_dialogs 1
adb logcat -c

# Wetterdaten laden; ohne Standortfreigabe nimmt die App den Standardort
adb shell am start -n $PKG/.MainActivity
sleep 35
shot news-de
adb shell am force-stop $PKG

for lang in de en fr es pt-PT pl; do
  adb shell cmd locale set-app-locales $PKG --locales "$lang"
  sleep 2
  page 1 0 0; sleep 12; shot "lang-$lang-1"
  page 1 5 0; sleep 10; shot "lang-$lang-2"
  page 1 7 0; sleep 10; shot "lang-$lang-3"
  adb shell am force-stop $PKG; sleep 3; adb shell am start -n $PKG/.MainActivity; sleep 12; shot "lang-$lang-4"
  adb shell input keyevent KEYCODE_BACK; sleep 3
  adb shell am force-stop $PKG; sleep 3; adb shell am start -n $PKG/.DebugMapActivity; sleep 12; shot "lang-$lang-5"
  adb shell am broadcast -n $PKG/.DebugRenderReceiver -a $PKG.DEBUG_RENDER
  sleep 14
  adb shell am force-stop $PKG
done
adb pull /sdcard/Android/data/$PKG/files/widgets "$OUT/widgets"

(cd out && zip -qr verify.zip verify)

# Hohe Aufloesung fuers README
adb shell cmd locale set-app-locales $PKG --locales en
sleep 2
page 1 0 1; sleep 50; shot readme-en-day
page 3 2 1; sleep 50; shot readme-en-rain
page 7 0 1; sleep 50; shot readme-en-night
adb shell am start -S -n $PKG/.DebugMapActivity
sleep 20
adb shell input tap 950 290
sleep 30
shot readme-en-map
adb shell cmd locale set-app-locales $PKG --locales de
sleep 2
page 1 0 1; sleep 50; shot readme-de-day
page 3 2 1; sleep 50; shot readme-de-rain
page 7 0 1; sleep 50; shot readme-de-night
adb shell am start -S -n $PKG/.DebugMapActivity
sleep 20
adb shell input tap 950 290
sleep 30
shot readme-de-map

timeout 60 adb logcat -d -s 'WetterDebug:*' 'WetterMap:*' AndroidRuntime:E System.err:W > "$OUT/log.txt"
(cd out && zip -qr verify.zip verify)
exit 0
