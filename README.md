# NyankoMode

本喵模式 is an Android accessibility service that maps configured trigger text to replacement output in other apps.

## Features

- Replace keyword triggers while typing.
- Replace the latest line break with configured output.
- Apply a mapping when a recognizable send control is tapped.
- Support multiple triggers and outputs per rule.
- Rotate through outputs or choose one randomly.
- Replace the trigger, insert before it, or insert after it.
- Preview mappings locally without enabling the accessibility service.
- Exclude selected packages from processing.
- Intercept non-input-area taps, apply send rules when appropriate, and replay the original interaction.

The application itself is always excluded from text processing.

## Requirements

- Android Studio with a JDK supported by the project toolchain.
- Android SDK Platform 37.
- Android SDK Build Tools 37.0.0.

The project uses Gradle 9.7.1, Android Gradle Plugin 9.3.2, Kotlin 2.4.10, and Java 11 source compatibility. Use the committed Gradle wrapper.

## Build and test

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

On Windows:

```bat
gradlew.bat :app:testDebugUnitTest
gradlew.bat :app:assembleDebug
gradlew.bat :app:assembleRelease
```

Enable the service in **Settings → Accessibility → 本喵模式文本替换服务** before testing replacement in another application. Accessibility behavior depends on the target app's exposed accessibility nodes and cannot be fully verified by JVM tests alone.

## Signing

Debug and release variants use the same signing key. Signing material is intentionally not committed.

For local builds, create `signing.properties` in the repository root:

```properties
storeFile=nyankomode-upload.jks
storePassword=your-keystore-password
keyAlias=your-key-alias
keyPassword=your-key-password
```

The keystore path is resolved relative to the repository root. Both `signing.properties` and root-level `.jks` / `.keystore` files are ignored by Git.

Never publish the keystore or its passwords. Losing the release key prevents updates to an application already distributed under that key.

## GitHub Actions

`.github/workflows/android-build.yml` builds and uploads signed debug and release APKs. Configure these repository Actions secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

`ANDROID_KEYSTORE_BASE64` must contain the single-line Base64 encoding of the signing keystore. The workflow creates signing files only on the runner and removes them after the job, including failed jobs.

## License

This project is licensed under the [MIT License](LICENSE).
