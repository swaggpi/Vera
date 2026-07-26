# Changelog

All notable changes to Vera are documented here. Format based on
[Keep a Changelog](https://keepachangelog.com/); versions follow [SemVer](https://semver.org/).

## [Unreleased]

### Changed
- **On-device engine moved from MediaPipe LLM Inference to LiteRT-LM.** Google put the MediaPipe
  LLM API into maintenance-only mode; LiteRT-LM is where the work now goes, and it is what loads
  `.litertlm` models. The engine tries the **GPU** backend first and falls back to CPU.
- **Default model is now Gemma 4 E2B** (~2.6 GB, ungated, 140+ languages) instead of
  Qwen2.5-1.5B — a better fit for the multilingual briefing. Gemma 4 E4B (~3.7 GB) is offered on
  phones with 12 GB+ of RAM; models a device cannot load are no longer shown.
- Downloading a model now evicts the previous one and its compile cache, instead of leaving
  gigabytes behind.
- Toolchain: Kotlin 2.3.21, KSP 2.3.10, AGP 8.13.2, Hilt 2.58, Room 2.8.4, Gradle 8.14.3 —
  required by LiteRT-LM, which ships Kotlin 2.3 metadata.
- The release APK stays under IzzyOnDroid's ~30 MB limit (23.4 MB) by compressing the 21 MB
  LiteRT-LM native library.

## [0.1.0] — 2026-07-25

First public release.

### Added
- **Gamified briefing** — morning/evening news from user-selected sources across the largest
  countries, summarised by an on-device model, with outlet ownership + political-leaning labels,
  streaks and XP.
- **One-tap on-device AI** — download a model from inside the app (choose **Fast** Qwen 0.5B ~550 MB
  or **Best** Qwen 1.5B ~1.6 GB). Runs entirely on the phone; nothing is uploaded. No Google Play
  Services required.
- **Key points + chat** — pull out a story's must-know facts and ask follow-up questions in a
  full-screen view, answered from the article on-device.
- **“Check what you heard”** — search multiple outlets, keep a diverse & relevant set, summarise
  each and flag likely bias.
- **Fake-news training** — daily prebunking game + SIFT Socratic coach.
- **News-diet meter** — source-diversity insights and echo-chamber nudges.

[0.1.0]: https://github.com/swaggpi/Vera/releases/tag/v0.1.0
