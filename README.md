# eFootball Pro Companion

An Android companion app for eFootball mobile, focused on lightweight network diagnostics and screenshot-based coaching.

## Current status

- Project initialized.
- Network diagnostics are intended to measure public diagnostic endpoints only; these measurements are not eFootball/Konami server ping.
- Screenshot coaching will require explicit Android screen-capture permission and a secure AI service. No silent screen recording.
- GearUP-style alternate routing requires relay/VPN infrastructure and is not provided by basic diagnostics.

## Build

Android app source and CI build workflow will be added in subsequent commits.


## One-shot screen capture

The app requests Android MediaProjection consent, starts a visible foreground notification, waits five seconds for the user to return to eFootball, captures exactly one frame, saves it in the app cache, and immediately stops projection. No continuous capture or background monitoring. Screenshot-based AI analysis is not yet connected to an AI backend.

## Real AI screenshot analysis

The Android app can send a user-selected one-shot screenshot to the companion AI backend and display the returned visual analysis. The backend uses Google's Gemini vision API. The Gemini API key is kept server-side and is never bundled in the APK.

### Deploy the backend

1. Create a private Gemini API key in Google AI Studio.
2. In Render, create a new Blueprint from this repository and let it read `render.yaml`.
3. Set the Render environment variables `GEMINI_API_KEY` and `APP_ACCESS_TOKEN`. Use a long random value for the app token. Never commit either secret.
4. Wait for the service to deploy. Check `https://YOUR-SERVICE.onrender.com/health`.
5. In the Android app, choose **CONFIGURE REAL AI BACKEND** and enter the Render service URL and the same `APP_ACCESS_TOKEN`.
6. Capture a screenshot, then tap **ANALYZE SCREENSHOT WITH AI**.

The analysis endpoint accepts only a user-triggered screenshot, checks the app token, and limits image uploads to 8 MB. The backend forwards the image to Gemini for analysis. A single static screenshot can sometimes reveal a formation, but it cannot reliably prove opponent playstyle or match habits; those require explicit tactics-screen evidence or multiple match moments. The AI prompt is designed to label uncertain findings instead of inventing them.

### Privacy and operating limits

Screenshots are transmitted to the backend and then to the configured AI provider only after the user taps the analysis button. Do not send screenshots containing private information. Provider usage may incur charges or be subject to provider limits. The app does not silently record gameplay.

