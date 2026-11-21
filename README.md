# eFootball Pro Companion

A lightweight Android companion for eFootball Mobile with public-network diagnostics, user-triggered screenshot analysis, and an Amharic-capable AI chat/voice interface.

## Implemented in the Android app

- Network diagnostics: repeated TCP connection-time measurements to Cloudflare (1.1.1.1:443) and Google (8.8.8.8:443), response variation, failed connection-attempt rate, traffic-light grading, and practical suggestions.
- Connection information: Wi-Fi/mobile-data state, Android internet validation, and metered-network status.
- One-shot screenshot capture: Android's explicit MediaProjection consent, visible foreground notification, a single captured frame, local preview, and optional sharing.
- AI screenshot analysis: user-triggered upload to the configured backend, with evidence-limited tactical analysis.
- AI chat coach: typed Amharic or English questions through the configured backend, with practical tactical guidance oriented toward the user's stated central-possession passing style.
- Push-to-talk voice coach: Android speech-recognition activity requests Amharic input and Android TextToSpeech reads the AI answer when the device has a compatible speech service.

## Important limitations

- TCP response time to public endpoints is **not** ICMP ping, true packet loss, eFootball/Konami server latency, or proof of server-side lag. The traffic light is only a comparative public-endpoint connection estimate.
- This app does not yet provide GearUP-style VPN routing. Actual alternate routing requires a secure VPN/relay service, server infrastructure, and a separately implemented Android VPN service.
- Voice is push-to-talk, not an always-listening “Hey Coach” wake word. Amharic speech recognition and TTS depend on services and language data installed on the phone.
- The app cannot guarantee zero lag or improve the carrier's radio signal.
- Screenshot analysis is sent only after the user explicitly taps the analysis button. No continuous gameplay recording is performed.

## Configure the AI backend

1. Create a Gemini API key in Google AI Studio.
2. Deploy this repository's `render.yaml` Blueprint on Render.
3. Set `GEMINI_API_KEY` and a long random `APP_ACCESS_TOKEN` in the Render service environment. Keep both private; do not commit them.
4. After deployment, verify `https://YOUR-SERVICE.onrender.com/health`.
5. In the app, open **CONFIGURE REAL AI BACKEND** and enter the service URL and the same app access token.
6. Use chat or capture a screenshot and tap **ANALYZE SCREENSHOT WITH AI**.

The backend keeps the Gemini key server-side, checks the app token, accepts JPEG/PNG/WebP screenshots up to 8 MB, and uses evidence-limited prompts. The Android app accepts HTTPS backend base URLs only and does not follow authenticated redirects. AI provider usage may incur charges.

## Build and verification

- Android CI workflow: `.github/workflows/android-apk.yml` (manual dispatch after implementation is ready).
- Backend test workflow: `.github/workflows/backend-tests.yml`.
- Backend unit tests: `cd backend && pip install -r requirements.txt -r requirements-dev.txt && pytest -q`.
- Android build: `gradle assembleDebug`.
- A successful CI build confirms compilation and APK packaging; it does **not** replace installation and real-device testing. Screen capture, speech services, backend connectivity, and performance must be verified on a physical Android device.
