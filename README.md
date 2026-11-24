# Crochet Counter & PDF

Native Android crochet row counter and PDF pattern reader. Offline-first, no account, no server.

## Features
- Unlimited local crochet projects
- One-tap row counter, undo, target and progress
- Persist every change locally
- Import a PDF pattern with the Android document picker
- Remember imported PDF access, PDF page, zoom and reading guide position
- English, Spanish and Portuguese localization
- Privacy-friendly: no internet permission, tracking, analytics or ads

## Build
Requires JDK 17 and Android SDK 35.

```bash
./gradlew assembleDebug
```

A GitHub Actions workflow builds a downloadable debug APK on pushes and pull requests.

## QA before publishing
Test opening multipage PDFs, document permission persistence, device rotation, process restart, app upgrade, project switching, undo, PDF zoom and the movable guide on a physical Android phone. A debug APK is for QA only; Play Store releases need a signed AAB and policy review.

## License
All rights reserved, LauraApps.
