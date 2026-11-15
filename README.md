# GlacierLauncher

Android launcher (Kotlin, Jetpack Compose, Material 3, DataStore). iOS-style home with glass panels, dock, folders,
App Library, widgets (built-in + any app's widgets through AppWidgetHost), gestures and full Settings.
Min SDK 26, target/compile SDK 34. No analytics, no accounts, no server.

> **Status: written but NOT compiled or run by the author's tooling.** The environment that generated this
> had no Android SDK/Gradle/network. Expect to fix a few compile errors on the first build, and test on a device.

## Dependencies (why each one)
| Dependency | Why |
|---|---|
| Compose BOM 2024.06 (ui, foundation, material3, icons-core) | UI, pager, Material 3 base (custom glass system on top) |
| activity-compose, lifecycle-runtime/viewmodel-compose | Activity host, `collectAsStateWithLifecycle`, ViewModel |
| datastore-preferences | Local settings + layout (JSON). Replaces SharedPreferences |
| core-ktx | `Drawable.toBitmap`, `ContextCompat`, `NotificationManagerCompat` |

## Layout
`model/` data classes · `data/` DataStore, installed apps, layout codec/grid ops, weather ·
`widget/` AppWidgetHost wrapper · `media/` media-session reader · `system/` default-launcher + usage-access helpers ·
`ui/theme` glass + motion · `ui/home` home/dock/folders/menus · `ui/library` App Library + search ·
`ui/widgets` built-in widgets · `ui/settings` · `ui/onboarding`.

## Build
1. Install Android Studio (or command-line SDK) with **platform 34**, JDK 17.
2. `gradle wrapper --gradle-version 8.9` once (the wrapper JAR is not included), or open in Android Studio.
3. Debug APK: `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
4. Release APK: `./gradlew :app:assembleRelease`. Create git-ignored `keystore.properties`
   (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) to sign with your key; without it the release APK is signed
   with the debug key (fine for personal installs only).
5. Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk` or copy the APK to the phone.
6. Default launcher: press Home → choose GlacierLauncher → "Always", or Settings › System & Launcher › Set as Default Launcher
   (uses `RoleManager` ROLE_HOME on Android 10+, Home-app settings on 8/9).

## Termux
Reliable route: GitHub Actions (`.github/workflows/build.yml`) builds the APK; download it from the Actions tab.
Direct build in Termux is fragile because Google's `aapt2` is x86-64 only. `scripts/termux-build.sh` checks Java, Git, Gradle,
SDK platform 34 and a Termux-native `aapt2`, and only builds if all exist:
```bash
cd ~/GlacierLauncher && chmod +x scripts/termux-build.sh && ./scripts/termux-build.sh
# optional install over ADB (needs adb connected):
adb install -r "$(find app/build/outputs/apk/debug -name '*.apk' | head -n1)"
```
Missing pieces (install what the script reports): `pkg update && pkg install openjdk-17 git gradle aapt2`, plus an Android SDK
with `platforms/android-34` and `ANDROID_HOME` pointing to it. This path is untested.

## Git
```bash
cd ~/GlacierLauncher
git status
git config user.name "YOUR NAME" && git config user.email "YOUR EMAIL"
git commit --amend --reset-author --no-edit        # the bundled commit uses a placeholder author
git remote -v                                       # if origin already exists, don't overwrite it
git remote add origin REPOSITORY_URL                # only if the line above printed nothing
git branch -M main
git push -u origin main
```
Authenticate with SSH keys or `gh auth login` (GitHub CLI). Never paste tokens into files or chat.

## Features
**Implemented:** home grid (configurable columns/rows/pages/padding), multi-page pager, dock, folders (create by drop,
open/rename/dissolve, spring open), drag & drop across pages and dock, long-press menus, swipe up/down gestures,
double-tap/long-press actions, App Library (categories, A–Z with index, search, name/recent sort, hide apps), search panel,
8 built-in widgets × 3 sizes, third-party widgets (bind/configure/resize presets/remove), themes (system/light/dark),
accent colors, glass intensity/corner radius, icon style/size, reduce-motion/speed/bounce, haptics, onboarding,
default-launcher status + request, package install/uninstall refresh, reset.

**Limits (Android or design):**
- Real backdrop blur is only applied behind full-screen overlays (App Library/search/folder) via window blur on Android 12+
  when the system allows it; panels use translucency everywhere else, and everything falls back to a scrim.
- App-open/return animations use the system scale-up animation; exact iOS-style zoom isn't possible for third-party apps.
- Third-party widgets are rendered by their own apps; they can't be restyled. Dragging them works by long-pressing a spot that
  doesn't consume touches. Otherwise use Settings › Placed widgets. Widgets from the weather/calendar of other apps are not faked.
- Calendar widget shows dates only (no events, no calendar permission). Weather needs a city + internet (Open-Meteo), cached offline.
- Music widget needs Notification Access (opt-in; content never read). Recent-apps sort needs Usage Access (opt-in).
- Swipe left/right is fixed to page switching (not remappable). Static app shortcuts menus (LauncherApps) are not implemented.
- Custom manual ordering of the App Library is not implemented (name / recently used only).
- Resizing is by Small/Medium/Large presets, not by drag handles.
