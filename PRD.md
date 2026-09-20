# Backlog Nudge — Product Requirements Document

> **Status note (2026-09-20).** This document is the original plan and parts of it
> are now superseded by what actually shipped. Kept intact because the reasoning
> is still useful, but read these corrections first:
>
> - **No LLM, no network.** The Claude API integration described in §4.1, §6 and
>   §7 was built and then removed. The app makes zero network calls and the
>   `INTERNET` permission is gone. Voice capture uses Android's on-device
>   `SpeechRecognizer`; the transcript is saved as the item title with no
>   auto-tagging of time/energy/category.
> - **Native Android, not Tauri.** §6 proposes a Tauri 2.0 cross-platform shell.
>   What exists is a native Kotlin + Jetpack Compose Android app. iOS, macOS,
>   Windows and cross-device sync are not built.
> - **Visual direction.** The app follows the "Green Light" direction: Big
>   Shoulders Display for headings, Hanken Grotesk for UI, Spline Sans Mono for
>   durations, with green reserved for exactly two things — going and finishing.
>
> Shipped and working: `UsageStatsManager` detection via a foreground service,
> a continuous-session threshold (default 15 min), a daily-limit fast path
> (default 60 min, then ~30s re-nudge), and the nudge itself as a Bubbles
> notification with a full-screen-intent fallback.

## 1. Problem

Backlog items ("things I'll do when I have free time") die in notes apps because nothing reminds you of them *at the moment you actually have free time* — which is usually the moment you've opened YouTube/Netflix/Instagram instead. The app's job: capture backlog items conversationally, detect "you're currently in low-value idle/entertainment time," and nudge you toward a matching backlog item — without being annoying enough to get disabled in a week.

## 2. Goals / Non-goals

**Goals**
- Talk-to-capture: dump backlog items via voice or text, AI structures them (title, time estimate, energy level, category).
- Cross-platform presence: Android, iOS, macOS, Windows — same backlog, same nudge logic, synced.
- Context-aware nudging: detect "user has been in an entertainment/idle app for N minutes" and surface a matching backlog item as a conversational notification.
- Adaptive: nudges quiet down for items/times you keep dismissing; items you keep deferring eventually get flagged for a decision (do it, reschedule, or drop).

**Non-goals (v1)**
- Not a general task manager (no deadlines, subtasks, projects, collaborators).
- Not a screen-time blocker (no forced app-shielding/blocking behavior).
- Not multi-user/family accounts.
- Not App Store/Play Store distribution in early phases — personal-use builds first.

## 3. Core user story

> "review that PR, and also finally read the RAG paper" (spoken or typed)
→ AI splits into 2 backlog items, each auto-tagged with a rough time/energy estimate, confirmable in one tap.

> 15 minutes into a YouTube session on your phone →
> notification: "You've been on YouTube a while — got 15 min? The RAG paper's been sitting since Tuesday."
> → tap: Do it now / Snooze 1hr / Not today / Remove from backlog

## 4. Feature breakdown

### 4.1 Capture
- Voice or text input, freeform, one utterance can yield multiple items.
- LLM (Claude API) parses into: `title`, `estimated_minutes` (bucket: 5/15/30/60+), `energy` (low/medium/high), `category` (chore/learning/project/social/admin/other).
- If the utterance is ambiguous on time/energy, ask one short clarifying question instead of guessing — but default to a guess and let the user edit rather than blocking capture.
- Manual add/edit list view as a fallback to voice.

### 4.2 Context detection ("is the user idle/entertained right now")
Per-platform signal, normalized to one shared event shape:
```
{ platform, category: "entertainment"|"idle", confidence: "high"|"low", minutes_in_session, ts }
```
- **Windows / macOS**: foreground-app polling, high confidence, real-time. Easiest tier — build here first.
- **Android**: `UsageStatsManager` polling (Play-Store-safe) as primary; do NOT use `AccessibilityService` for this — Play Store review increasingly rejects non-accessibility uses of it, and it over-collects (reads on-screen content, including banking apps), which is a needless privacy liability for what is a low/medium-confidence timing signal anyway.
- **iOS**: no reliable "which app, right now" signal exists for third-party apps. Two realistic mechanisms:
  1. **Shortcuts personal automation** ("When \<app> is opened, run Shortcut") calling a URL scheme/local notification — no special entitlement, works today, but user has to set it up once per watched app and it's opt-in per automation.
  2. **Screen Time `DeviceActivity`/`ManagedSettings` (Family Controls entitlement)** — the "real" mechanism apps like One Sec/Opal use, via a `ShieldConfiguration` extension that intercepts opening a restricted app and shows a custom interstitial. Caveats confirmed in research: the DeviceActivity extension is sandboxed and **cannot pass data back to the main app** (only an opaque view), threshold timing is unreliable (observed drift, e.g. a 10-min threshold firing at ~5 min), and the entitlement itself requires a special request/approval from Apple even for personal/dev builds. Treat this as a v2+ investment, not a v1 blocker.
  - **v1 iOS plan**: ship with the Shortcuts-automation fallback only.

### 4.3 Nudge engine (the "AI-integrated" part)
- Don't fire on session-open; wait for a per-user threshold (default ~10–15 min continuous entertainment/idle signal).
- Pick a backlog item matching remaining likely free time (short session → short item) and current energy signal if known (e.g. late night → low-energy items only).
- LLM generates the nudge phrasing conversationally (not a templated push string), grounded only in the item's title/metadata — never in raw usage logs.
- Response actions: Do it now (opens focused view for that item), Snooze (30m/1h/today), Not today, Remove.
- Adaptive quieting: track snooze/dismiss counts per item and per time-of-day; reduce nudge frequency for repeatedly-snoozed items; after N snoozes, ask once whether to reschedule or drop it instead of continuing to nudge.

### 4.4 Sync
- Backlog + nudge-history sync across devices; the raw usage/context signal never leaves the device (local-first for sensing, sync only for backlog state and snooze counters).

### 4.5 Privacy stance (explicit, since this app watches app-usage patterns)
- All context-detection happens on-device; only structured backlog text and interaction outcomes (snoozed/done/dropped) sync to the backend.
- No raw screen-time/app-open logs stored or transmitted.
- Opt-in per platform sensor; the app is fully usable with manual "I'm free now" triggering if the user never grants usage-access permission.

## 5. Data model (sketch)

```
BacklogItem {
  id, title, notes,
  estimated_minutes: enum(5,15,30,60,120+),
  energy: enum(low, medium, high),
  category: enum(chore, learning, project, social, admin, other),
  status: enum(open, done, dropped),
  created_at, last_nudged_at, snooze_count, dismiss_count
}

NudgeEvent {
  id, item_id, platform,
  triggered_at, context_confidence,
  response: enum(did_it, snoozed, dismissed, removed)
}

UserPrefs {
  nudge_threshold_minutes, quiet_hours, per_platform_sensor_enabled
}
```

## 6. Tech direction

- **Single core, thin per-OS sensor adapters** (established in earlier discussion) — product/nudge logic lives once; only the "is the user idle/entertained" signal is platform-native.
- **Client shell**: Tauri 2.0 (Rust core + native webview) for a genuinely single codebase across Windows, macOS, Android, and iOS — Tauri 2.0 is the one framework in the current landscape whose mobile support extends the same core to iOS/Android, unlike Electron (desktop-only) or React Native (mobile-only). Tradeoff: Rust for the native layer, and per-OS WebView rendering quirks to test against (vs Electron's single bundled Chromium) — acceptable here since the UI surface is small (backlog list + nudge cards), not rendering-heavy.
- **Sensor adapters** (native, per OS): Win32 foreground-window polling; macOS `NSWorkspace` notifications; Android `UsageStatsManager`; iOS Shortcuts-automation webhook (v1) → optional DeviceActivity/Shield extension (v2+).
- **LLM**: Claude API for (a) parsing capture input into structured items, (b) generating nudge copy. Both are short, cheap calls — no need for a heavier agent framework.
- **Backend**: minimal sync service (backlog CRUD + snooze counters only) — a small hosted Postgres (e.g. Supabase/Neon free tier to start) is enough; no durable-workflow engine needed since there's no long-running server-side orchestration, all state machines (thresholds, quieting) run client-side per device.
- **Voice input**: prefer on-device speech-to-text (Apple Speech framework / Android `SpeechRecognizer`) over a cloud STT API, to keep raw audio off any server and cut cost.

## 7. Buy / subscribe / outsource checklist

| Item | Needed for | Cost | Notes |
|---|---|---|---|
| Apple Developer Program | iOS/macOS builds on a real device beyond 7-day free provisioning; any Family Controls entitlement request | $99/yr | Only needed once you move past local Xcode debug builds |
| Family Controls entitlement request | iOS v2+ Shield-based nudging | Free, but a manual Apple approval process | Apply only when starting Phase iOS-v2; has precedent (One Sec, Opal) but is reviewed case-by-case |
| Google Play Developer account | Only if/when publishing to Play Store | $25 one-time | Not needed for personal sideload builds |
| Claude API | Parsing + nudge copy generation | Pay-per-token, usage-based | Primary recurring cost, but per-call volume is tiny |
| Hosted Postgres (Supabase/Neon) | Cross-device backlog sync | Free tier sufficient at solo-user scale | Upgrade only if syncing for multiple users later |
| Push notification services (APNs, FCM) | Local/remote notifications | Free | Standard dev-account setup, no extra subscription |
| Code signing cert (Windows) | Only if distributing the Windows build beyond your own machine | ~$100+/yr if pursued | Skip for personal-only use; unsigned local builds are fine |

Nothing here needs to be outsourced to a third party/contractor — the whole thing is buildable solo with the above accounts/APIs.

## 8. Open risks

1. iOS is the weak link — no reliable automatic signal without the entitlement; v1 iOS experience will be manual-trigger + Shortcuts-automation only, not full parity with Android/desktop.
2. Tauri mobile (iOS/Android) is newer than its desktop story — expect more rough edges there than on Windows/macOS.
3. Play Store policy risk if `AccessibilityService` is ever used instead of `UsageStatsManager` — avoid it entirely per current review guidance.
4. Nudge fatigue is a product risk, not just technical — the adaptive-quieting logic (4.3) needs real dogfooding, not just a v1 ship-and-forget.
