# SpotMark

SpotMark is a local-first Android app for saving the current location of personal items, adding notes and photos, and later finding the item again with direction, distance, and external map navigation.

![SpotMark preview](spotmark_icon.png)

## Features

- Save the current precise latitude and longitude as a named spot.
- Edit the item name and note for each saved spot.
- Attach photos from the system photo picker or camera.
- Find a saved spot with a compass-style direction indicator and live distance.
- Open an external map app for route navigation.
- Store all spot metadata locally with Room. Photos are copied into app-private storage.

## Tech Stack

- Kotlin
- Jetpack Compose + Material 3
- Room
- Google Play services Location
- Android Photo Picker and Activity Result APIs

## Build

Use Android Studio or run:

```powershell
$env:JAVA_HOME='D:\android studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:assembleDebug
```

Run unit tests:

```powershell
$env:JAVA_HOME='D:\android studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:testDebugUnitTest
```

## Gradle Sync Notes

If dependency downloads are slow or fail in Android Studio, configure Gradle to use your local VPN/proxy in your local Gradle settings, not in committed project files. For example, if your local proxy is `127.0.0.1:7890`, add this to a local `gradle.properties` such as `%USERPROFILE%\.gradle\gradle.properties`:

```properties
systemProp.http.proxyHost=127.0.0.1
systemProp.http.proxyPort=7890
systemProp.https.proxyHost=127.0.0.1
systemProp.https.proxyPort=7890
```

This repository also includes Maven mirror entries in `settings.gradle.kts` to improve dependency resolution in mainland China.

## License

MIT
