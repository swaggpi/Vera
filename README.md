# Vera (Android) — on-device media-literacy coach

Native Android app for the **UNESCO Youth Hackathon 2026** (track: AI & Media and Information Literacy).
A small **Gemma** model runs **on the device** (MediaPipe LLM Inference) and can pull **live web/RSS** to:

1. **Gamified briefing** — morning & evening news from your chosen sources, rewritten plainly by the
   on-device model, with quiz questions, streaks and XP.
2. **Fake-news training** — SIFT coaching + prebunking micro-games with spaced repetition. *(P2)*
3. **"Check what you heard"** — speak/type a claim; Vera researches it and coaches you through it. *(P3)*
Plus a **news-diet meter**, **deepfake drills**, and (long-term) a moderated **discourse layer**.

Privacy-first: the model runs locally, so **nothing leaves the device** — a natural fit for the user's
**Pixel 7a on GrapheneOS** (no Google Play Services required).

## Status
**P0 foundation + P1 briefing slice are built and verified** (compiles + unit tests pass). The app runs
against a deterministic `FakeLlmEngine`; real Gemma wiring is the next device-side step. See
[`progress.txt`](progress.txt) and [`feature-requirements.md`](feature-requirements.md).

## Architecture
- **`:core`** — pure Kotlin/JVM: models, `LlmEngine` interface + `FakeLlmEngine`, RSS/Atom parser,
  source catalog, briefing generator, gamification (streaks/XP + SM-2 spaced repetition), diet meter.
  All fast unit tests live here — the Ralph completion gate.
- **`:app`** — Compose (Material3) UI, Hilt DI, Room, DataStore, OkHttp, navigation. Real device/network
  impls (`MediaPipeLlmEngine` stub, `NewsRepositoryImpl`) live behind the `:core` interfaces.

## Build & test (Tier-1 — no device needed)
```bash
bash init.sh          # installs the Android SDK (first time), writes local.properties
bash run-tests.sh     # unit tests + assembleDebug  (the completion gate)
```
Toolchain: JDK 17+ (21 used here), Gradle wrapper 8.9, `compileSdk 35`, `minSdk 26`.

## Run on device (Tier-2 — Pixel 7a / GrapheneOS)
1. On the phone: Settings → About → tap build number ×7 → Developer options → **Wireless debugging** on.
2. `adb pair` / `adb connect <ip:port>`, accept the RSA prompt.
3. `./gradlew installDebug` (or `adb install app/build/outputs/apk/debug/app-debug.apk`).
4. **Gemma model** (for real AI): accept the Gemma license (Kaggle/HF), download e.g.
   `gemma2-2b-it-cpu-int4.task`, push it to the app files dir, then wire `MediaPipeLlmEngine`
   (see its KDoc) and bind it in `di/AppModule` instead of `FakeLlmEngine`.

## Notes
- Source catalog: `app/src/main/assets/sources_catalog.json`. Press-freedom tiers are approximate —
  refresh annually from RSF. Tagesschau's feed is non-commercial / 60 req-hr; stay source-agnostic for
  any public release.
- Sibling web prototype + proposal + deck live in the parent folder (`../`).
