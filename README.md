# MadrasaTube

MadrasaTube is an open-source, lightweight, distraction-free Islamic video client for Android. It focuses on clean, ad-free video streaming and audio playback alongside curated access to beneficial educational channels.

## Features
- Search and stream YouTube videos
- Quick access to curated educational channels:
  - **AMAU** (Al Madrasatu Al Umariyyah)
  - **AMAR** (Al Madrasatu Al Rashidiyyah)
  - **Arabic 101**
  - **ilman Nafiya**
- Audio-only mode with full playback controls (Play/Pause, seek slider)
- Pull-to-refresh on search and channel pages
- Custom playlists, bookmarking, and watch history
- Dynamic app theming (System, Light, Dark)

## How to Build

Building MadrasaTube is straightforward. Make sure you have JDK 17 and Android SDK installed.

1. Clone this repository.
2. Open the project in Android Studio or terminal.
3. Run the following command to build the debug APK:
   ```bash
   ./gradlew assembleDebug
   ```
4. To install directly to a connected device:
   ```bash
   ./gradlew installDebug
   ```

## License

This project is open-source and available under the MIT License.
