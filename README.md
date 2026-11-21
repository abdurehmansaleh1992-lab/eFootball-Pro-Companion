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
