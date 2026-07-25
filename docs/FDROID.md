# Publishing Vera on F-Droid

## What F-Droid requires

To be included in the **main F-Droid repo**, an app must:

1. **Be 100% FOSS** — an OSI/FSF-approved licence, with **no proprietary libraries or tracking**. ✅ Vera is Apache-2.0, uses no Google Play Services and no analytics/trackers.
2. **Build from source in F-Droid's pipeline** — F-Droid compiles every app itself and **forbids prebuilt binaries** that it can't build. ⚠️ **This is the one blocker for Vera** (see below).
3. **No "anti-features" / non-free network dependencies** — services the app relies on must be free. ✅ Wikipedia + DuckDuckGo search are fine; the on-device model (Qwen2.5, Apache-2.0) is FOSS and its download is optional/user-initiated.
4. **Metadata + tagged releases** — a build recipe in the `fdroiddata` repo, `versionCode`/`versionName` in the build, and a git **tag** per release. ✅ Provided: fastlane metadata under `fastlane/metadata/`, versioning in `app/build.gradle.kts`.
5. **Reproducible builds** are encouraged (not mandatory).

## The one blocker: MediaPipe's prebuilt native library

Vera's on-device inference uses `com.google.mediapipe:tasks-genai`, whose AAR ships a **prebuilt** native library (`libllm_inference_engine_jni.so`). F-Droid's build-from-source policy doesn't allow shipping prebuilt binaries it can't reproduce, so the **main F-Droid repo would reject the app as-is.**

Three ways forward:

- **A. IzzyOnDroid (recommended, fast path).** [IzzyOnDroid](https://apt.izzysoft.de/fdroid/) is a widely-used, F-Droid-compatible repo that accepts a **FOSS-licensed app distributed as a signed GitHub release APK** without requiring build-from-source. Everything needed (Apache-2.0 licence, fastlane metadata, no anti-features, no Play Services) is already here — you'd just cut a signed release and open a request. This gets Vera into an F-Droid client immediately.
- **B. Main F-Droid with an exception.** Request that F-Droid whitelist the MediaPipe prebuilt (they occasionally allow well-known, verifiable binaries). Uncertain and slow.
- **C. Replace MediaPipe with a from-source engine.** Swap `MediaPipeLlmEngine` for a `llama.cpp`-based engine built inside F-Droid's pipeline (e.g. via a JNI module). Because everything model-facing is behind the `LlmEngine` interface, this is a contained change — but it's real work.

## Checklist to submit (IzzyOnDroid path)

- [x] FOSS licence (`LICENSE`, Apache-2.0)
- [x] No Google Play Services / trackers
- [x] Store metadata (`fastlane/metadata/android/en-US/…` — title, descriptions, screenshots)
- [x] `versionCode` / `versionName` in `app/build.gradle.kts`
- [ ] A **signed release APK** attached to a GitHub **tag** (e.g. `v0.1.0`) — set up an upload keystore and a release workflow
- [ ] Open an inclusion request at the IzzyOnDroid repo pointing at this GitHub repo's releases

## Notes

- Add a proper high-res app icon (512×512) at `fastlane/metadata/android/en-US/images/icon.png`.
- For the main F-Droid repo later, add an `fdroiddata` metadata file and confirm the build succeeds with `fdroid build`.
