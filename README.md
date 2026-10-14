# Mixi

A dead-simple Android app for downloading YouTube videos and audio, designed for a
non-technical user. Paste a link, tap one button, done.

## Why this exists

A family member watches religious content on YouTube and wants to keep it for offline
use. She is not comfortable with technology, and I do not live with her — so the app has
to work unattended and be fixable from a distance.

## Architecture

```
┌──────────────────────────────────────────────┐
│  MIXI APK ON THE PHONE                       │
│  ┌────────────────────────────────────────┐  │
│  │ NATIVE BAR: [logo]   🔄 UPDATE         │  │ ← always present
│  ├────────────────────────────────────────┤  │
│  │ WEBVIEW → UI served from Vercel        │  │ ← editable remotely
│  │ link field · VIDEO · AUDIO · progress  │  │
│  ├────────────────────────────────────────┤  │
│  │ NATIVE ENGINE: yt-dlp + ffmpeg         │  │ ← residential IP, no blocks
│  │ saves to Gallery / Music               │  │
│  └────────────────────────────────────────┘  │
└──────────────────────────────────────────────┘
```

**Why the engine cannot live on a server:** YouTube blocks datacenter IP ranges
(AWS, GCP, Azure) with `Sign in to confirm you're not a bot` *before* evaluating the
request. No library avoids this — the block is per-IP, not per-code. The phone's own
residential IP is the only reliable exit.

## Update model — three levels

| Level | What it fixes | User taps | Weight |
|---|---|---|---|
| 1. yt-dlp engine | YouTube extractor breakage | **0** (automatic, daily) | ~3 MB |
| 2. Web interface | Design, text, buttons | **0** (on open) | ~0 |
| 3. Full APK | Native changes (rare) | **3** (guided by phone) | ~100 MB |

## Repository layout

```
web/       → published to Vercel (the interface)
android/   → the APK (Kotlin + WebView + yt-dlp)
.github/   → GitHub Actions build pipeline
docs/      → design spec and implementation plan
```

## Documentation

- `docs/specs/2026-10-14-mixi-design.md` — approved design document
- `docs/plans/2026-10-14-mixi-implementation.md` — task-by-task implementation plan

## Build

The APK is built in the cloud by GitHub Actions — no local Android SDK required.
Trigger it by pushing to `main`, or run the workflow manually.

<!-- Mixi: app Android de descarga de video y audio, para uso personal. -->
