# Wetter

Private Android-App im Stil der iOS-Wetter-App. Nicht für den Vertrieb gedacht.
Kotlin und Jetpack Compose, ab Android 13 (AGSL-Shader).

[Update-Verlauf](changelog/CHANGELOG.md)

## Bauen

- **GitHub:** Bei jedem Push auf `main` baut die Action eine APK und legt sie unter *Releases* ab.
- **Android Studio:** Ordner öffnen und starten.
- **Kommandozeile:** `./gradlew assembleDebug`, die APK liegt dann unter `app/build/outputs/apk/debug/`.

Alle Builds tragen dieselbe Signatur (`app/wetter-debug.keystore`), neue APKs installieren sich also über alte.

## Bedienung

| Geste | Wirkung |
| --- | --- |
| Links/rechts wischen | Ort wechseln, der Himmel morpht mit |
| Lange auf den Ortsnamen drücken | Wetter-Vorschau: schaltet durch alle Himmels-Szenen |
| Nach unten ziehen | Aktualisieren |
| Tageszeile, Stundenleiste, Kachel antippen | Detailkurven |
| Auf der Kurve entlangfahren | Werte je Stunde |
| Ortsliste: nach links wischen | Ort löschen |

## Aufbau

| Pfad | Inhalt |
| --- | --- |
| `assets/shaders/sky.agsl` | Himmel als Shader |
| `sky/Sky.kt` | Wetterdaten → Shader-Parameter, Morphing |
| `data/` | API, Parser, deutsche Texte, Mondphase, Orte und Cache |
| `ui/Cards.kt` | Stundenleiste, 10 Tage mit Temperaturbalken, alle Kacheln |
| `ui/Theme.kt` | `GlassCard` mit dem Klebe-Verhalten beim Scrollen |
| `ui/DetailSheet.kt` | Detailblatt mit Kurven |
| `assets/map/map.html` | Karte: Radar, Temperatur, Wind |
| `tools/preview/` | rendert den Shader im Browser, zum Abstimmen ohne Handy |

## Datenquellen

| Quelle | Wofür | Hinweis |
| --- | --- | --- |
| [Open-Meteo](https://open-meteo.com) | Vorhersage, Luftqualität, Ortssuche, Temperatur-/Windraster | Ohne Schlüssel, frei für nicht-kommerzielle Nutzung, CC BY 4.0. Bündelt u. a. DWD ICON und ECMWF. |
| [Bright Sky](https://brightsky.dev) | Amtliche DWD-Warnungen | Nur für Orte in Deutschland |
| [RainViewer](https://www.rainviewer.com/api.html) | Regenradar | Letzte 2 Stunden, Zoom bis 7, Schema „Universal Blue“, keine Prognose mehr seit 2026 |
| OpenStreetMap | Grundkarte | Ohne Schlüssel. Die App meldet sich per eigenem User-Agent, sonst sperrt OSM die Kacheln. |

## Schrift

Inter (SIL Open Font License). SF Pro von Apple darf außerhalb von Apple-Plattformen nicht verwendet werden.
