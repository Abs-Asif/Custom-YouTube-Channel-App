# Durūs

Durūs is an open-source, lightweight, distraction-free Islamic video learning client for Android. It focuses on clean, ad-free video streaming alongside curated access to beneficial educational playlists.

## Features
- Notification and system media playback controls (Play/Pause, next/previous video, seek 5 seconds backward/forward)
- High-resolution video and playlist thumbnails
- YouTube-style video list layout inside playlists with full edge-to-edge thumbnails in portrait and 2-column grid in landscape
- Integrated compact video selector in player screen under channel name
- Dark theme and Rose app styling by default
- Audio-only mode with playback controls
- Watch progress tracking and history
- About page with app version and creator credit

## How to Build

Building Durūs is straightforward. Make sure you have JDK 17 and Android SDK installed.

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
