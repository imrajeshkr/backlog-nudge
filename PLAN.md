# Build Plan — Backlog Nudge

Phased by dependency, not calendar time — each phase should be usable/dogfoodable on its own before moving to the next.

## Phase 0 — Core loop, no OS integration
Prove the AI capture + nudge-copy quality before touching any platform sensor.
- Data model + local storage (BacklogItem, NudgeEvent, UserPrefs).
- Claude API integration: freeform text → structured backlog item(s).
- Manual "I'm free for N minutes" trigger → nudge engine picks an item → Claude generates conversational nudge copy.
- Bare-bones UI (even a CLI or single-window Tauri shell) — just enough to add items and manually fire nudges.
- **Exit criteria**: capture parsing feels right on messy input; nudge copy feels conversational, not templated; you'd actually want to keep using it manually.

## Phase 1 — Desktop context-awareness (Windows + macOS)
Easiest, least-restricted sensing tier — first fully-automatic nudging.
- Tauri shell on Windows + macOS.
- Sensor adapters: Win32 foreground-window polling; macOS `NSWorkspace` active-app notifications.
- Wire sensor events → shared threshold/nudge logic from Phase 0.
- Snooze/dismiss actions feed back into per-item counters.
- **Exit criteria**: leaving YouTube/Netflix open on desktop for the threshold window reliably produces a relevant, well-timed nudge; snoozing visibly reduces repeat nudges for that item.

## Phase 2 — Android
- Android sensor adapter via `UsageStatsManager` polling (not `AccessibilityService`).
- Mobile UI (Tauri mobile, or reassess against native/RN if Tauri mobile friction is too high by this point — decide with real data, not upfront).
- Push notifications via FCM.
- Sync backlog + counters to hosted Postgres so desktop/Android share state.
- **Exit criteria**: same nudge quality/timing as desktop, now on the device where most "entertainment" time actually happens.

## Phase 3 — iOS (v1: manual-trigger + Shortcuts fallback)
- Ship the app with capture + manual-trigger nudging fully working (parity with Phase 0/1 minus auto-detection).
- Document/provide a Shortcuts "personal automation" recipe (app-opened → call local webhook/URL scheme) as an opt-in automatic trigger.
- **Exit criteria**: iOS user can get the same nudges as other platforms, just with one-time manual Shortcuts setup instead of silent auto-detection.

## Phase 4 — Adaptive learning + polish
- Per-item/per-time-of-day nudge-frequency decay based on snooze/dismiss history.
- "You've snoozed this 5 times — do it, reschedule, or drop it?" resurfacing flow.
- Voice capture via on-device STT (Apple Speech / Android `SpeechRecognizer`) instead of typed-only capture.
- Cross-device dedupe (don't nudge on both phone and laptop for the same item within the same window).

## Phase 5 — iOS v2 (stretch)
- Apply for Family Controls entitlement.
- Build the `DeviceActivity`/`ManagedSettings` Shield-extension flow for real auto-detection on iOS, accepting its known constraints (no data back to main app, unreliable threshold timing) — likely rendering a rotating backlog item directly inside the Shield UI rather than trying to round-trip through the main app.

## Phase 6 — Distribution (only if you decide to ship beyond yourself)
- Google Play Developer account + listing.
- Apple App Store listing (separate from the dev-only Family Controls entitlement work).
- Windows code signing if distributing outside your own machine.

---

Phase 0 is the only one worth starting immediately — say the word and I'll scaffold the Tauri project, data model, and the Claude API capture/nudge calls.
