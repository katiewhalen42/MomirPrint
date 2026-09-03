# Momir Print — Agent Guide

## Big picture
- Single-module Android app in `app/` built with Kotlin + Jetpack Compose Material3.
- Launcher entry point is `app/src/main/java/com/example/momirprint/MainActivity.kt`; it currently renders `RandomScreen`, while `TestSearchScreen` is still present but commented out.
- Core domain is Magic: The Gathering card lookup + thermal printer output.

## Architecture and data flow
- Network layer: `ApiService.kt` wraps the Scryfall API with Retrofit + Gson.
  - Requests add `Accept: application/json` and `User-Agent: MomirPrint/0.1` in the shared OkHttp client.
  - `SearchViewModel` and `RandomViewModel` call the service from `viewModelScope` and store results in `mutableStateOf`.
- Model layer: `CardModels.kt` mirrors Scryfall response shapes (`MagicCard`, `CardFace`, `CardSearchResponse`) with default values so Compose previews and empty states work.
- Printing layer: `PrinterService.kt` handles Bluetooth ESC/POS connection state and `PrintFormatter.kt` turns cards into printer markup / QR-enabled text.
- Settings layer: `Settings.kt` uses DataStore preferences for `PrintMode`, QR-code toggle, and printer address; values are exposed as `Flow`s and updated via suspend setters.

## Project conventions
- Keep production code in `com.example.momirprint`; Compose theme stays under `ui/theme/`.
- Prefer small composables + ViewModel state over manual state hoisting in screens.
- Card printing currently assumes `MagicCard.layout == "normal"`; check `PrintFormatter.kt` before extending support for other layouts.
- Bluetooth permissions are declared in `AndroidManifest.xml`; printer features depend on them plus `INTERNET` for Scryfall.

## Build, test, and debug
- Use the Gradle wrapper from the repo root.
- Common commands:
  - `./gradlew assembleDebug`
  - `./gradlew test`
  - `./gradlew connectedDebugAndroidTest`
- Android build config is Kotlin/Compose with minSdk 28, target/compile SDK 37, Java 11, and Gradle configuration cache enabled (`gradle.properties`).

## External dependencies / integration points
- Scryfall API: `ApiService.kt`
- Bluetooth thermal printing: `com.github.DantSu:ESCPOS-ThermalPrinter-Android`
- Persistent preferences: `androidx.datastore:datastore-preferences`
- Barcode/QR support: `com.google.zxing:core`

## Files to inspect first when changing behavior
- `MainActivity.kt` for screen wiring and ViewModel usage
- `Settings.kt` and `PrintFormatter.kt` for print-mode / QR behavior
- `PrinterService.kt` for connection lifecycle and error handling
- `app/build.gradle.kts` and `settings.gradle.kts` for dependency/repository changes
