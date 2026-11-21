# Privacy Notes — eFootball Pro Companion

This document describes the current behavior of the app. It is not a substitute for a jurisdiction-specific legal privacy policy.

## Data handled on the device

- Network diagnostics open short TCP connections to the configured public test endpoints (currently Cloudflare 1.1.1.1 and Google 8.8.8.8). The app displays measured connection times and failed attempts.
- A game screenshot is captured only after the user taps the capture control and approves Android's MediaProjection prompt. The app captures one frame, previews it locally, and stops capture.
- Screenshots remain in the app's cache unless the user shares them or explicitly sends them for AI analysis. Cached screenshots may be removed by Android when storage is needed.
- The AI backend URL and app access token are stored in the app's private preferences. Use a unique token and do not share it.
- Voice input is push-to-talk. The app launches Android's speech-recognition interface; availability, language processing, and any speech-service data handling depend on the selected Android speech provider. The app does not run a continuous microphone listener.

## Data sent to the configured AI backend

- When the user sends a chat message, the message is sent over HTTPS to the backend URL configured by the user.
- When the user taps screenshot analysis, the selected screenshot is sent over HTTPS to that same backend.
- The backend authenticates requests with the app access token and sends the chat text or screenshot to the configured Google Gemini API using the server-side API key.
- AI provider usage may incur charges. Review the privacy and terms of the backend host and AI provider before use.

## Data not claimed by this app

- The app does not claim to measure eFootball/Konami server ping or true packet loss.
- The app does not continuously record gameplay or continuously listen for a wake word.
- The app does not promise that network diagnostics will eliminate lag or improve carrier signal.

## User controls

Users can avoid sending AI data by not configuring the backend or not using chat and screenshot analysis. Users can stop voice input by leaving the Android speech-recognition interface. Uninstalling the app removes its private local preferences and cache under normal Android app-data behavior.
