# Vera — Feature Requirements (Ralph backlog)

On-device (Gemma via MediaPipe) media-literacy Android app for the UNESCO Youth Hackathon 2026.
Rule: a box is `[x]` only when its unit tests pass (`bash run-tests.sh`). Device-only checks are marked `(device)`.

## P0 — Foundation & harness
- [x] Gradle multi-module (`:app`, `:core`), version catalog, wrapper
- [x] `init.sh` installs Android SDK; `run-tests.sh` gate
- [x] Hilt DI, Compose Material3, navigation with bottom bar
- [x] Vera design system (palette/theme) ported from web prototype
- [x] Room DB (progress, read log) + DataStore settings
- [x] `LlmEngine` interface + `FakeLlmEngine`; `SpeechService`/`SearchProvider` interfaces — [ ] speech & search interfaces still TODO
- [x] `sources_catalog.json` (22 outlets across the largest countries)
- [ ] CI workflow (GitHub Actions: `run-tests.sh`) — Tier-1 lane
- [ ] `SpeechService` + `SearchProvider` interfaces + fakes in :core

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
- [ ] Port SIFT Socratic coach from web prototype (scenarios + generic)
- [ ] Inoculation micro-games (technique cards, feedback)
- [ ] Spaced repetition scheduling wired to a `TrainingRepository` (SM-2 logic + tests done)
- [ ] Training UI + progress

## P3 — "Check what you heard" research assistant
- [ ] `SearchProvider` impls (Wikipedia, Brave/Tavily) + fakes
- [ ] `ResearchRepository`: claim extraction → search → fetch → grounded synthesis with citations
- [ ] Voice input (`SpeechRecognizer`) + TTS output `(device)`
- [ ] Research UI with source cards + SIFT coaching

## P4 — Insights & extras
- [ ] News-diet / echo-chamber meter UI (DietMeter logic + tests done)
- [ ] Deepfake / AI-image spotting drills
- [ ] Share-back explainer cards (generate + share intent)

## P5 — Discourse layer (long-term, opt-in, networked)
- [ ] Backend (Supabase/Firebase free tier), accounts, threads per story
- [ ] AI pre-post coaching + source-reputation + reporting/moderation
- [ ] GDPR + minor-safety review

## Cross-cutting
- [ ] Accessibility pass (TalkBack, large fonts, reduced motion)
- [ ] i18n (strings externalized; multilingual model prompts)
- [ ] Press-freedom tiers refreshed annually from RSF (currently approximate)
