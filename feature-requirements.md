# Vera — Feature Requirements (Ralph backlog)

On-device (Gemma via MediaPipe) media-literacy Android app for the UNESCO Youth Hackathon 2026.
Rule: a box is `[x]` only when its unit tests pass (`bash run-tests.sh`). Device-only checks are marked `(device)`.

## P0 — Foundation & harness
- [x] Gradle multi-module (`:app`, `:core`), version catalog, wrapper
- [x] `init.sh` installs Android SDK; `run-tests.sh` gate
- [x] Hilt DI, Compose Material3, navigation with bottom bar
- [x] Vera design system (palette/theme) ported from web prototype
- [x] Room DB (progress, read log) + DataStore settings
- [x] `LlmEngine` + `SpeechService` + `SearchProvider` interfaces + fakes in :core
- [x] `sources_catalog.json` (22 outlets across the largest countries)
- [ ] CI workflow (GitHub Actions: `run-tests.sh`) — Tier-1 lane

## P1 — Gamified briefing (MVP)
- [x] Source catalog parsing (`SourceCatalog`) + tests
- [x] RSS/Atom parser (`RssParser`) + tests
- [x] `BriefingGenerator`: summary/quiz JSON + graceful fallback + tests
- [x] Streak/XP logic (`Gamification`) + tests
- [x] Source picker UI (ownership + press-freedom tier chips)
- [x] Briefing UI: cards, interactive quiz, streak header
- [x] Offline `SampleData` fallback when feeds are blocked
- [ ] Wire real Gemma (`MediaPipeLlmEngine`) `(device)`
- [ ] WorkManager 2×/day briefing + notification scheduling
- [ ] Persist "read" articles to `read_log` for the diet meter
- [ ] Morning/evening slot content differentiation
- [ ] Instrumented UI test: complete a briefing → streak increments `(device)`

## P2 — Fake-news training
- [x] SIFT Socratic coach (LLM + scripted scenarios) + tests
- [x] Inoculation micro-games (technique cards, feedback, streak) + tests
- [x] Training UI (daily challenge + coach)
- [ ] Wire spaced repetition scheduling into training (SM-2 logic + tests done, not yet persisted)

## P3 — "Check what you heard" research assistant
- [x] `SearchProvider` + `WikipediaSearchProvider` (keyless) + `FakeSearchProvider` + tests
- [x] `ResearchRepository`: query → search → grounded, cited coaching (+ fallbacks) + tests
- [x] Voice input (`SpeechRecognizer`) + TTS output via `AndroidSpeechService` `(device)`
- [x] Research UI with source cards + read-aloud
- [ ] `BraveSearchProvider`/`TavilySearchProvider` (API key) for live news-claim checking

## P4 — Insights & extras
- [x] News-diet / echo-chamber meter UI, fed by `read_log` on briefing completion
- [ ] Deepfake / AI-image spotting drills
- [ ] Share-back explainer cards (generate + share intent)

## P5 — Discourse layer (long-term, opt-in, networked)
- [ ] Backend (Supabase/Firebase free tier), accounts, threads per story
- [ ] AI pre-post coaching + source-reputation + reporting/moderation
- [ ] GDPR + minor-safety review

## Cross-cutting
- [ ] Runtime RECORD_AUDIO permission request flow (currently must grant mic manually in Settings)
- [ ] De-Googled voice: bundle FOSS on-device STT (whisper.cpp) + TTS so voice works without Google
- [ ] Accessibility pass (TalkBack, large fonts, reduced motion)
- [ ] i18n (strings externalized; multilingual model prompts)
- [ ] Press-freedom tiers refreshed annually from RSF (currently approximate)
