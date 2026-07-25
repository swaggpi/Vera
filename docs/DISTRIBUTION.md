# Distributing Vera (open-source, F-Droid-style)

Vera targets **[IzzyOnDroid](https://apt.izzysoft.de/fdroid/)** — a large, well-known repository that works inside the **F-Droid app** and other clients (Droid-ify, Neo Store). It's FOSS-only like F-Droid, but accepts an app as a **signed APK published on GitHub Releases**, so you avoid the main F-Droid repo's build-from-source rule (which MediaPipe's prebuilt native library would fail). This is the easiest open-source route for both you and your users.

**How users install it:** in the F-Droid app → Settings → Repositories → add IzzyOnDroid (many clients include it already) → search "Vera". One tap.

## What's already set up in this repo
- ✅ Apache-2.0 licence, no Google Play Services, no trackers
- ✅ Store metadata + screenshots (`fastlane/metadata/android/en-US/…`)
- ✅ Adaptive app icon
- ✅ Release **signing config** in `app/build.gradle.kts` (reads `keystore.properties` locally, or env vars in CI)
- ✅ **Release workflow** (`.github/workflows/release.yml`): tag a version → builds a **signed** APK → publishes a GitHub Release

## What you do once (≈10 minutes)

**1. Create an upload keystore** (keep this file + passwords safe — you need them for every future update):
```bash
keytool -genkey -v -keystore vera-upload.keystore -alias vera \
  -keyalg RSA -keysize 2048 -validity 10000
```

**2. Add four GitHub secrets** (repo → Settings → Secrets and variables → Actions):
| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 vera-upload.keystore` (the whole file, base64) |
| `KEYSTORE_PASSWORD` | the store password you chose |
| `KEY_ALIAS` | `vera` |
| `KEY_PASSWORD` | the key password you chose |

**3. Cut a release:**
```bash
git tag v0.1.0 && git push origin v0.1.0
```
The workflow builds and signs the APK and attaches it to a GitHub Release automatically.

**4. Request inclusion on IzzyOnDroid:** open an issue at their [request tracker](https://gitlab.com/IzzyOnDroid/repo) (or `RequestPackage`) pointing at `https://github.com/swaggpi/Vera`. Their bot then tracks your GitHub Releases and publishes each new tag.

## To build a signed APK locally (optional)
Create `keystore.properties` in the repo root (it's gitignored):
```properties
storeFile=/absolute/path/to/vera-upload.keystore
storePassword=...
keyAlias=vera
keyPassword=...
```
Then `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`.

## Notes
- **Google Play / Samsung** would reach more people but need a paid/registered account and (for new Play accounts) a 20-tester × 14-day closed test. The same signed build works there too if you go that route later.
- **Main F-Droid repo** (stricter, build-from-source) would require replacing MediaPipe with a from-source engine (e.g. llama.cpp) — a contained change since inference sits behind the `LlmEngine` interface. IzzyOnDroid first is the pragmatic order.
