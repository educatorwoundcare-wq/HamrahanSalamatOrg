# Firebase to Supabase Migration Guide

This document outlines the steps required to safely remove legacy Firebase dependencies from the project now that the Supabase infrastructure is in place.

## Step 1: Update `app/build.gradle.kts`

Remove or comment out the following Firebase and Google Services plugins:
```kotlin
// plugins {
//   ...
//   alias(libs.plugins.google.services)
//   alias(libs.plugins.firebase.crashlytics)
// }
```

Remove the `googleServices` block:
```kotlin
// googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }
```

Remove or comment out the following dependencies in the `dependencies` block:
```kotlin
// implementation(platform("com.google.firebase:firebase-bom:34.16.0"))
// implementation("com.google.firebase:firebase-analytics")
// implementation(libs.firebase.crashlytics)
// implementation(platform(libs.firebase.bom))
// implementation(libs.firebase.ai)
// implementation(libs.firebase.appcheck.recaptcha)
// implementation("com.google.firebase:firebase-messaging")
```

## Step 2: Remove `google-services.json`

Delete the `app/google-services.json` file from the project directory.

## Step 3: Deprecate Firebase Classes

Refactor the existing logic in `FCMService.kt` to use Supabase Realtime or an alternative push notification provider like OneSignal, or remove it entirely if background notifications are handled locally.

Similarly, references to Firebase in `CloudClient.kt` or `CrashHandler.kt` (if any are tied to Crashlytics) should be removed or swapped for alternative logging mechanisms.

## Step 4: Verify and Build

Run a clean build and ensure no compilation errors are thrown.

```bash
./gradlew clean assembleDebug
```
