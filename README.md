# Crochet Row Counter & PDF

Native Android crochet row counter and PDF pattern reader. Offline-first, no account, no server.

## Features
- Unlimited local crochet projects
- One-tap row counter, undo, target and progress
- Persist every change locally
- Import a PDF pattern with the Android document picker
- Remember imported PDF access, PDF page, zoom and reading guide position
- English, Spanish and Portuguese localization
- Local projects and PDFs; optional network access for consent and advertising

## Build
Requires JDK 17, Android SDK 36, and Gradle 8.11.1.

```bash
gradle assembleDebug
```

A GitHub Actions workflow builds a downloadable debug APK on pushes and pull requests.

## QA before publishing
Test opening multipage PDFs, document permission persistence, device rotation, process restart, app upgrade, project switching, undo, PDF zoom and the movable guide on a physical Android phone. A debug APK is for QA only; Play Store releases need a signed AAB and policy review.

## License
All rights reserved, LauraApps.

## Play release
Run Play release AAB manually. Default uses Google demo ads; enable live_ads only for production. Configure upload signing secrets as documented in the workflow. Configure AdMob privacy messages before release.
