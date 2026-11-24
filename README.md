# Bunchiz — Restaurant Ordering & Kitchen App

**Bunchiz** is an Android restaurant ordering, live order tracking, and kitchen/menu management application built with **Kotlin**, **Jetpack Compose (Material 3)**, **Firebase Auth (Google Sign-In via Credential Manager)**, and **Cloud Firestore**.

## Features

- **Customer Menu & Cart**: Browse signature dishes (`Chicken Burger ৳180`, `Pizza ৳250`, `Coke ৳50`, plus staff-added dishes), filter by category, adjust quantities, and calculate live totals in `৳` (BDT).
- **Checkout & Customer Profile**: Save delivery details (`name`, `phone`, `address`) and place sequential orders starting at `#1001`.
- **Live Order Tracking**: Track orders in real time across 5 stages: `Pending` → `Confirmed` → `Preparing` → `Ready` → `Delivered`.
- **Staff & Kitchen Console**: Advance active orders through kitchen stages, add new dishes, update prices, and toggle dish availability.
- **Order Alerts**: Automatic customer notifications when an order is received and on every status update.

## Tech Stack

- **Language & UI**: Kotlin, Jetpack Compose, Material Design 3, Navigation Compose
- **Typography**: Bundled Google Fonts (`Fraunces`, `Plus Jakarta Sans`, `JetBrains Mono`)
- **Auth**: Firebase Authentication + Jetpack `CredentialManager` (`GetSignInWithGoogleOption`)
- **Database**: Cloud Firestore with hardened user-scoped security rules (`firestore.rules`)

## Download & Build from GitHub

1. **One-Click APK Download (Releases)**:
   Every push triggers the **Android Build** workflow (`.github/workflows/android-build.yml`), which builds `Bunchiz-debug.apk` and publishes it to the **Releases** page (`Bunchiz Android APK (Latest)`) as well as the **Actions** artifact (`bunchiz-debug-apk`).
2. **One-Click Source Download**:
   Click **Code → Download ZIP** on GitHub, or clone with:
   ```bash
   git clone <your-github-repo-url>
   ```
3. **Open in Android Studio**:
   - Open the downloaded project folder in Android Studio (AGP 9.1.1 / Gradle 9.3.1, JDK 21).
   - Decode the debug keystore before building locally if needed:
     ```bash
     base64 -d debug.keystore.base64 > debug.keystore
     ```
   - Build the debug APK:
     ```bash
     gradle :app:assembleDebug
     ```
