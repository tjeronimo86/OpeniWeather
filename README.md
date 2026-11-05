# Wetter

**Deutsch** · [English](README.en.md)

Eine Android-Wetter-App mit animiertem Himmel. Privates Hobbyprojekt, nicht kommerziell.
Kotlin und Jetpack Compose, ab Android 13.

[Update-Verlauf](changelog/CHANGELOG.md) · [Downloads](../../releases/latest)

<!-- SCREENSHOTS -->

## Funktionen

- Himmel als Shader, passend zu Wetterlage, Tageszeit und Mondphase
- Stundenleiste, 10-Tage-Vorhersage mit Temperaturbalken und Detailkurven
- UV-Index, Wind, Niederschlag, Luftfeuchte, Sichtweite, Luftdruck, Luftqualität, Mond
- Karte mit Regenradar, Temperatur und Wind
- Mehrere Orte und Widgets für den Startbildschirm
- Update-Suche mit Download der neuen Version
- Sprachen: Deutsch, Englisch, Französisch, Spanisch, Portugiesisch (Portugal), Polnisch

## Installation

Die neueste APK liegt unter [Releases](../../releases/latest). Android fragt beim ersten Mal, ob Installationen aus dieser Quelle erlaubt sind.

## Bauen

- **Android Studio:** Ordner öffnen und starten.
- **Kommandozeile:** `./gradlew assembleRelease`, die APK liegt dann unter `app/build/outputs/apk/release/`.
- **GitHub:** Jeder Push auf `main` baut eine APK und veröffentlicht sie unter *Releases*.

Die Versionsnummern im Verlauf stammen aus `changelog/CHANGELOG.md`. Die Build-Nummer ist die Nummer des GitHub-Laufs.

## Datenquellen

| Quelle | Wofür | Hinweis |
| --- | --- | --- |
| [Open-Meteo](https://open-meteo.com) | Vorhersage, Luftqualität, Ortssuche, Temperatur- und Windraster | CC BY 4.0, frei nur für nicht-kommerzielle Nutzung |
| [Bright Sky](https://brightsky.dev) | Warnungen des Deutschen Wetterdienstes | Nur für Orte in Deutschland |
| [RainViewer](https://www.rainviewer.com) | Regenradar der letzten 2 Stunden | |
| [OpenStreetMap](https://www.openstreetmap.org/copyright) | Grundkarte | © OpenStreetMap-Mitwirkende, ODbL |

Fremde Bestandteile und ihre Lizenzen stehen in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Hinweise

- Dieses Projekt ist ein unabhängiges Hobbyprojekt und steht in keiner Verbindung zu Herstellern von Betriebssystemen, Geräten oder anderen Wetter-Apps.
- Alle Angaben ohne Gewähr. Die App ersetzt keine amtlichen Warnungen.
- Es gibt keinen Support und keine Garantie.
- Der Signierschlüssel im Repo ist nur für dieses Projekt gedacht. Lade die App nur von dieser Seite.

## Lizenz

Für den eigenen Code ist keine freie Lizenz vergeben: Alle Rechte vorbehalten. Ansehen und privat bauen ist erlaubt. Für fremde Bestandteile gelten deren Lizenzen.
