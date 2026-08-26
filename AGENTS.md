# Repository Guidelines

## Project Overview
NyankoMode (`本喵模式`) is a single-module Android app that replaces configured trigger text with rule-defined output text in other apps. Users manage multi-trigger, multi-output rules, choose rotate or random output, preview mappings locally, and control excluded packages. The runtime replacement feature is implemented as an Android accessibility service.

The authoritative product and acceptance details are in `.trae/specs/implement-text-mapping-app/spec.md`; the task log and validation status are in `tasks.md` and `checklist.md` in the same directory.

## Architecture & Data Flow
- `MainActivity` enables edge-to-edge, installs the custom Material 3 theme, creates repositories from the shared `Context.appDataStore`, and passes them to `AppNavHost`.
- `AppNavHost` is the Compose state/navigation hub. It collects `RuleRepository.rules`, `SettingsRepository.totalEnabled`, and `SettingsRepository.excludedApps` with `collectAsState`, then wires screen callbacks to repository suspending mutations in a remembered coroutine scope.
- `data/` contains the serializable `MappingRule`/`OutputMode` model, the Preferences DataStore provider, and repositories. Rules are JSON-serialized into one DataStore string key; global enabled state and excluded package names use dedicated Preferences keys.
- `engine/MappingEngine.kt` is pure Kotlin and Android-free. Given full text, cursor position, rules, and optional rotation state, it selects the longest enabled trigger ending at the cursor and returns replacement bounds, output, and the resulting cursor position. `ROTATE` state is caller-owned and in-memory; `RANDOM` uses `kotlin.random.Random`.
- `accessibility/TextMappingService.kt` subscribes to the same DataStore flows as the UI. It filters `TYPE_VIEW_TEXT_CHANGED` events, the app package, excluded packages, disabled global state, and non-editable nodes; it writes replacements with `ACTION_SET_TEXT` and restores selection. A 250 ms self-write debounce prevents feedback loops.
- `ui/navigation/` defines `home`, `editor`, `trial`, and `settings` routes. Screens use hoisted state and callbacks; there is no ViewModel layer in the current production source. Shared visual primitives belong in `ui/components/` and theme tokens in `ui/theme/`.

## Key Directories
- `app/src/main/java/cc/ptoe/nyankomode/data/`: domain model, DataStore provider, rule/settings persistence.
- `app/src/main/java/cc/ptoe/nyankomode/engine/`: replacement logic and result model; keep this layer free of Android dependencies.
- `app/src/main/java/cc/ptoe/nyankomode/accessibility/`: global accessibility-service integration.
- `app/src/main/java/cc/ptoe/nyankomode/ui/`: Compose navigation, screens, shared components, and Material 3 Expressive theme.
- `app/src/main/res/`: manifest-facing strings, theme/color resources, launcher assets, and accessibility-service XML configuration.
- `app/src/test/java/cc/ptoe/nyankomode/`: host-side JVM unit tests for pure Kotlin behavior and serialization.
- `app/src/androidTest/java/cc/ptoe/nyankomode/`: device/emulator instrumentation tests.
- `gradle/`: version catalog and committed Gradle wrapper metadata.

## Development Commands
Use the committed Gradle wrapper. The repository task log directly documents these commands:

```sh
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

On Windows, use the equivalent wrapper entry point:

```bat
gradlew.bat :app:testDebugUnitTest
gradlew.bat :app:assembleDebug
```

The Android application plugin provides these standard tasks, but they are not custom repository scripts:

```sh
./gradlew :app:lint                  # inferred standard lint task
./gradlew :app:installDebug          # inferred install task for a connected device
./gradlew :app:connectedDebugAndroidTest  # inferred device/emulator tests
```

There are no checked-in helper scripts, CI workflows, or custom run configurations. Run the app through Android Studio or install the debug APK on a device/emulator; the accessibility-service workflow requires manually enabling the service in system settings and testing text entry in another app.

## Code Conventions & Common Patterns
- Kotlin uses the official code style (`kotlin.code.style=official`). Preserve existing Kotlin/Compose formatting and package layout.
- Use `MappingRule` as an immutable data model and update values with `copy`. Keep `OutputMode` values as `ROTATE` and `RANDOM` unless the persisted serialization contract is deliberately migrated.
- Keep engine behavior deterministic where possible: pass a seeded `Random` in tests and pass a mutable rotation map explicitly when testing rotation. Preserve cursor-relative matching and longest-trigger precedence.
- Keep repository writes suspendable and expose reactive state through `Flow`. Follow existing `dataStore.edit` plus JSON encode/decode patterns. Corrupt or absent rule JSON currently falls back to an empty list through `runCatching`; preserve this behavior unless the error contract changes.
- In Compose, collect repository flows at the navigation boundary, hoist screen state, and pass event callbacks downward. Launch persistence mutations from the existing coroutine scope rather than adding a new state-management framework.
- Reuse `ui/components` primitives and `ui/theme` tokens for expressive shapes, gradient containers, typography, and colors. Do not introduce one-off styling when an existing local component or token applies.
- `SegmentedColumn` owns the rounded container background; `SegmentedColumnItem` uses a transparent `ListItem`, and its `HorizontalDivider` must span the full container width without a one-sided inset.
- Changes to the accessibility service must preserve event filtering, excluded-package handling, app self-exclusion, editable-node checks, `ACTION_SET_TEXT`/selection ordering, and self-write debounce behavior.
- Add Android manifest/service behavior through `AndroidManifest.xml` and `res/xml/accessibility_service_config.xml`; keep user-facing labels in `res/values/strings.xml`.

## Important Files
- `app/src/main/java/cc/ptoe/nyankomode/MainActivity.kt`: launcher and dependency assembly.
- `app/src/main/java/cc/ptoe/nyankomode/ui/navigation/AppNavHost.kt`: routes, collected state, and repository callback wiring.
- `app/src/main/java/cc/ptoe/nyankomode/engine/MappingEngine.kt`: pure mapping contract and replacement selection.
- `app/src/main/java/cc/ptoe/nyankomode/accessibility/TextMappingService.kt`: system event integration and text mutation.
- `app/src/main/java/cc/ptoe/nyankomode/data/MappingRule.kt`: serialized domain contract.
- `app/src/main/java/cc/ptoe/nyankomode/data/RuleRepository.kt`: persisted rule CRUD.
- `app/src/main/java/cc/ptoe/nyankomode/data/SettingsRepository.kt`: global enable and excluded-app persistence.
- `app/src/main/AndroidManifest.xml`: launcher activity and exported accessibility service declaration.
- `app/build.gradle.kts`: Android SDK levels, Compose enablement, Java 11 compatibility, dependencies, and build types.
- `gradle/libs.versions.toml`: centralized dependency/plugin versions.
- `gradle/wrapper/gradle-wrapper.properties`: pinned Gradle 9.5.0 distribution.
- `.trae/specs/implement-text-mapping-app/`: product requirements, task dependencies, checklist, and known validation gap.

## Runtime/Tooling Preferences
- This is an Android Gradle project, not a Node/Bun project. Use Gradle wrapper commands and Android Studio/SDK tooling.
- The wrapper is pinned to Gradle 9.5.0. Build configuration uses AGP 9.3.2, Kotlin 2.2.10, Compose BOM 2026.05.01, Material 3 `1.5.0-alpha26`, Navigation Compose 2.9.0, DataStore 1.1.7, and kotlinx-serialization 1.8.1.
- Module identity is namespace/application ID `cc.ptoe.nyankomode`; `minSdk` is 24, `compileSdk` and `targetSdk` are 37, and Java source/target compatibility is 11.
- Repositories are centralized in `settings.gradle.kts` with `FAIL_ON_PROJECT_REPOS`; do not add module-local repositories. Dependency versions belong in `gradle/libs.versions.toml`.
- `local.properties` is machine-specific Android SDK configuration and is ignored. Do not commit it or generated `build/`, `.gradle/`, or IDE cache artifacts.
- `gradle.properties` enables Gradle configuration cache and official Kotlin style. No explicit Gradle JVM/toolchain is configured in build scripts; use a locally supported JDK for the pinned Gradle/Android plugin stack.
- Release optimization is currently disabled and `app/src/main/keepRules/rules.keep` contains only the template comments. Treat shrinking/keep-rule changes as deliberate build behavior changes.

## Testing & QA
- Use JUnit 4 host-side tests under `app/src/test/java` for pure engine and serialization contracts. Existing tests cover multiple triggers, cursor-relative matching, longest-match precedence, rotate/random output selection, invalid rules, JSON round trips, and default-field decoding.
- Use `./gradlew :app:testDebugUnitTest` for the documented JVM test command. There is no Jacoco/coverage configuration, mocking framework, Robolectric setup, screenshot-test setup, or custom test orchestration.
- `app/src/androidTest/java` uses AndroidX JUnit with `AndroidJUnitRunner`; the current instrumented test is only a package-name smoke test. Device/emulator execution is via the standard `connectedDebugAndroidTest` task.
- For accessibility changes, supplement automated tests with manual device/emulator validation: enable the service, enter a trigger in another app, verify replacement and cursor placement, exercise rotate/random modes, verify excluded/self packages and the global switch, and confirm persisted rules after process restart. The repository checklist records this manual flow as still pending.
- After implementation, run the narrowest affected unit tests, then `:app:assembleDebug`. Do not treat a successful host build as proof that system accessibility behavior works.
