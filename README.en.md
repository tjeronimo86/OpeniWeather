# Weather

[Deutsch](README.md) · **English**

An Android weather app with an animated sky. A private hobby project, non-commercial.
Kotlin and Jetpack Compose, Android 13 or newer.

[Changelog](changelog/CHANGELOG.en.md) · [Downloads](../../releases/latest)

<!-- SCREENSHOTS -->

## Features

- Sky rendered as a shader, matching the weather, time of day and moon phase
- Hourly strip, 10-day forecast with temperature bars and detail charts
- UV index, wind, precipitation, humidity, visibility, pressure, air quality, moon
- Map with rain radar, temperature and wind
- Multiple locations and home screen widgets
- Update check with a download link for new versions
- Languages: German, English, French, Spanish, Portuguese (Portugal), Polish

## Installation

The latest APK is under [Releases](../../releases/latest). Android asks once whether installs from this source are allowed.

## Building

- **Android Studio:** Open the folder and run.
- **Command line:** `./gradlew assembleRelease`, the APK ends up in `app/build/outputs/apk/release/`.
- **GitHub:** Every push to `main` builds an APK and publishes it under *Releases*.

Version numbers come from `changelog/CHANGELOG.md`. The build number is the GitHub run number.

## Data sources

| Source | Used for | Note |
| --- | --- | --- |
| [Open-Meteo](https://open-meteo.com) | Forecast, air quality, place search, temperature and wind grid | CC BY 4.0, free for non-commercial use only |
| [Bright Sky](https://brightsky.dev) | Warnings from the German Weather Service | Locations in Germany only |
| [RainViewer](https://www.rainviewer.com) | Rain radar for the last 2 hours | |
| [OpenStreetMap](https://www.openstreetmap.org/copyright) | Base map | © OpenStreetMap contributors, ODbL |

Third-party components and their licenses are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Notes

- This is an independent hobby project with no connection to makers of operating systems, devices or other weather apps.
- All information without warranty. The app does not replace official warnings.
- There is no support and no guarantee.
- The signing key in this repository is for this project only. Download the app from this page only.

## License

No open source license is granted for the project's own code: all rights reserved. Viewing it and building it privately is allowed. Third-party components keep their own licenses.
