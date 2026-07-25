# Testing Vera on your Pixel 7a (GrapheneOS)

The debug APK is already built at `app/build/outputs/apk/debug/app-debug.apk`, and `adb` is already
installed on this machine at `$HOME/android-sdk/platform-tools/adb`. GrapheneOS needs **no Google Play
Services** for this app.

> Convenience: add adb to your PATH for this shell
> ```bash
> export PATH="$PATH:$HOME/android-sdk/platform-tools"
> ```

---

## 1. Enable wireless debugging on the phone
1. **Settings → About phone →** tap **Build number** 7× (enables Developer options).
2. **Settings → System → Developer options →** turn the top toggle **on**.
3. Scroll to **Wireless debugging** → turn it **on** (allow on the current Wi-Fi).
4. Keep both the phone and this computer on the **same Wi-Fi network**.

## 2. Pair + connect
On the phone: **Wireless debugging → Pair device with pairing code.** It shows an
**IP address & port** and a **6-digit code**.

```bash
adb pair 192.168.x.x:PORT        # enter the 6-digit code when prompted
```

Then connect using the IP:port shown on the **main** Wireless-debugging screen (a *different* port):

```bash
adb connect 192.168.x.x:PORT
adb devices                      # should list your device as "device"
```

*(USB alternative: on GrapheneOS set **Settings → Security → USB-C port** to allow data while unlocked,
plug in, then `adb devices` and accept the RSA prompt on the phone.)*

## 3. Install
```bash
cd vera-android
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
If GrapheneOS blocks it, confirm the install prompt **on the phone**. Open **Vera** from the launcher.

## 4. Grant permissions (needed for voice)
The mic is a runtime permission and the app doesn't yet prompt for it, so grant it manually once:
**Settings → Apps → Vera → Permissions → Microphone → Allow.** (Also allow Notifications if you want
briefing reminders later.)

---

## 5. What to test — a walkthrough

**Briefing tab**
- [ ] App opens on **Briefing** with news cards (uses your selected sources; falls back to bundled
      sample stories if a feed is blocked/offline).
- [ ] Tap the **tune icon** (top-right) → **Your sources**: toggle outlets on/off across countries;
      note the **ownership** + **press-freedom tier** labels. Go back — the briefing respects your picks.
- [ ] Answer a story's quiz question → correct/incorrect is revealed with an explanation.
- [ ] Tap **Mark briefing done** → the **streak** and **XP** in the header increase.
- [ ] Kill and reopen the app → streak/XP persisted (Room).

**Train tab**
- [ ] **Daily challenge**: pick the manipulation technique → feedback + streak; **Next challenge** cycles.
- [ ] **Coach me**: type e.g. *"a photo of a shark on a flooded road"* → Vera returns the 4-step SIFT
      walkthrough (no true/false verdict).

**Verify tab ("check what you heard")**
- [ ] Type e.g. *"the Great Wall of China is visible from space"* → **Check it** → a grounded coaching
      answer plus **Wikipedia source cards** (tap to open in browser). *(Needs internet.)*
- [ ] **Read-aloud** (speaker icon) and the **mic** button — see the voice caveat below.

**Insights tab**
- [ ] Before reading: an empty-state explainer. After completing a briefing or two: a **diversity %**,
      an **echo-chamber warning** if you only read one source/country, and by-country / by-ownership
      breakdowns.

## 6. Logs (for debugging)
```bash
adb logcat --pid=$(adb shell pidof -s app.vera)
```

---

## Honest caveats on this build

- **The AI is still the deterministic `FakeLlmEngine`.** Briefings/coach use canned + rule-based
  fallback text; research does a *real* Wikipedia search with grounded coaching. To get **real Gemma**
  answers, wire `MediaPipeLlmEngine` + side-load a `gemma2-2b-it-cpu-int4.task` model and swap the DI
  binding (see `MediaPipeLlmEngine` KDoc and `di/AppModule`). That's the next milestone.
- **Voice on GrapheneOS:** `SpeechRecognizer`/TTS need a speech engine, which a de-Googled phone may not
  ship. If so, `isAvailable()` is false and the mic button is disabled — **all features work fully by
  typing.** A FOSS on-device voice stack (e.g. whisper.cpp STT + a FOSS TTS) is on the roadmap so voice
  is truly Google-free too.
- **Live feeds:** some outlets block automated fetches or rate-limit; the app degrades to sample content
  rather than erroring.

## Rebuild after code changes
```bash
bash run-tests.sh                 # unit tests + assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
