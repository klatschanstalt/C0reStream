# Third-party notices

C0reStream does not bundle IPTV content or subscriptions. The project owner has not selected a project license; no `LICENSE` file is included. The components below remain under their own upstream licenses.

License names were read from the POM metadata cached by Gradle for the direct dependencies of version 1.3.0.

| Component group | License declared in dependency metadata |
| --- | --- |
| AndroidX (Activity, Compose, DataStore, Lifecycle, Navigation, Room, Test, WorkManager) | Apache License 2.0 |
| AndroidX Media3 (ExoPlayer, HLS, DASH, OkHttp data source, UI, MediaSession) | Apache License 2.0 |
| OkHttp and MockWebServer | Apache License 2.0 |
| kotlinx-coroutines (android, test) | Apache License 2.0 |
| JUnit 4 | Eclipse Public License 1.0 |
| org.json | Public Domain |
| kxml2 | Not declared in cached POM metadata; refer to the upstream project |

Build-time tooling (Android Gradle Plugin, Kotlin Compose plugin, KSP, Gradle wrapper, Android SDK, JDK) is not shipped in the APK and remains under the respective upstream licenses. Transitive dependencies are governed by their own upstream metadata in the published POM files.
