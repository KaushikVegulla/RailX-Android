# RailX

[![Android CI](https://github.com/KaushikVegulla/Railway-Reservation-System-/actions/workflows/android.yml/badge.svg)](https://github.com/KaushikVegulla/Railway-Reservation-System-/actions/workflows/android.yml)

RailX is a native Android railway journey demo built with Kotlin and Jetpack Compose. It supports train enquiries, Supabase accounts, a local booking flow, and Razorpay test checkout. It is an academic project and is not affiliated with IRCTC, CRIS, or Indian Railways. Demo bookings are not valid railway tickets.

## What changed in 1.9

- **Cognito removed completely**: the Amplify SDK, `CognitoAuth`, and `amplifyconfiguration.json` are gone, along with every Cognito flow. Supabase Auth is now the only cloud auth.
- **Registration uses Supabase**: the register flow creates the Supabase account first and confirms through the emailed confirmation link; if Supabase is unreachable it falls back to an on-device demo account with on-screen codes, as before.
- **Former Cognito accounts** keep working locally: their provider now reads as a legacy account and the on-device password hash signs them in.
- The optional Python backend now verifies **Supabase access tokens** (project JWKS) instead of Cognito JWTs for the Razorpay routes.
- Fixed a compile error from 1.8 (`SupabaseSession.fullName`) and hardened the Supabase auth repository so network failures return errors instead of crashing sign-in.

## What changed in 1.8

- **Supabase backend**: authentication and data are moving from Cognito + device-only storage to a Supabase project (Postgres with Row Level Security). See `supabase/README.md` for the schema and setup.
- **Supabase sign-in**: accounts tied to Supabase — and new email sign-ins — authenticate against Supabase and mirror into a local profile, so MPIN unlock and account-scoped bookings keep working. A failed or unreachable Supabase attempt falls through to the local flow.
- **Booking sync**: confirmed bookings, their passengers, and cancellations are mirrored to Supabase (best-effort) whenever a Supabase session exists.
- **Sign-out** now also ends the Supabase session.
- Added an MIT `LICENSE`.

## What changed in 1.7

- Refreshed Home, navigation, train results, and booking screens with consistent light/dark colors, readable cards, calendar selection, and accessible controls.
- Train sorting by departure, duration, and known fares; filters for departure window, class, and known availability. Controls that need missing provider data stay unavailable.
- Station suggestions wait briefly for typing to pause and cancel obsolete requests. Origin and destination suggestions stay separate.
- Availability loads progressively with a maximum of three concurrent class enquiries. A bounded 30-second cache avoids immediately repeating the same journey/class enquiry; station suggestions are cached for 24 hours.
- Booking history renders rows as needed. Passenger decoding happens off the UI thread.
- Booking history, last journey, and reused passengers belong to the signed-in account. Checkout captures the account that started payment and checks it again before saving.
- Startup waits for session readiness without an extra fixed delay. Unsupported service shortcuts and the nonfunctional return-journey toggle have been removed.

## Build and run

Use Android Studio with a full JDK 17 or newer, Android SDK platform 35, and the checked-in Gradle wrapper. Minimum Android version is 7.0 (API 24).

```sh
cp local.properties.example local.properties
```

Set `sdk.dir` to your SDK path. Set `railradar.api.key` for live enquiries. Without a working provider, train search can display clearly marked sample data.

Accounts use Supabase Auth (see the Supabase backend section below); the project URL and publishable key are public client configuration. Registration has a local demo fallback when Supabase is unreachable. MPIN, biometric unlock, and the local demo Aadhaar/VID check remain available; the latter is not UIDAI verification.

For backend-mediated Razorpay test payments, configure:

```properties
use.local.keys=false
backend.base.url=http://10.0.2.2:8088
```

Then follow [backend setup](backend/README.md). The emulator address above is for local development; deployed backends should use HTTPS. Remote payment calls require a Supabase access token.

The existing `use.local.keys=true` mode is for academic experimentation with test keys only. `local.properties` is gitignored, but secrets injected into `BuildConfig` can be extracted from the APK. Never package production payment secrets. This release does not replace the demo payment backend with a production booking service.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest  # running emulator/device required
```

## Supabase backend

Supabase is the RailX backend in progress: Auth for accounts, Postgres for synced bookings, passengers and favourite routes, and Edge Functions for the Razorpay steps that need a server secret. The app config defaults to the RailX project and can be overridden with `supabase.url` / `supabase.publishable.key` in `local.properties`. The publishable key is client configuration and is safe in the app because every table has Row Level Security policies scoped to the signed-in user. Never ship the secret / service_role key.

The live schema was applied on 17 Nov 2026, and the `create-order` / `verify-payment` Edge Functions were deployed the same day (ACTIVE, JWT-verified; they answer 401 without a signed-in user). Only the Razorpay test secrets remain to be set. Setup, auth settings and function deployment are documented in [supabase/README.md](supabase/README.md). Since 1.8 the sign-in screens use Supabase Auth for Supabase accounts and new email sign-ins, and bookings sync to Supabase when a session exists. Since 1.9 registration also goes through Supabase, Cognito is removed entirely, and the local demo flow remains the offline fallback.

## Existing data on upgrade

The Room v1 → v2 migration preserves booking rows and adds an account-owner column without destructive migration. Older rows have no reliable owner, so they remain on disk but are hidden from account history. Older unscoped saved-passenger/last-journey preferences are also retained but not shown. New records are scoped to the active account; signing out clears contact information from the session.

## Project layout

- `app/src/main/java/com/kaushik/railway/ui/`: Compose screens, shared controls, theme.
- `AppViewModel.kt`: session and journey state.
- `data/repository/`: cancellable enquiry orchestration, caching, account-scoped booking storage.
- `data/db/`: Room entities, owner-filtered queries, schema migration.
- `app/src/test/`: model, account, search filter, and repository unit tests.
- `app/src/androidTest/`: database migration and account-isolation tests.
- `app/src/main/java/com/kaushik/railway/data/Supabase*.kt`: Supabase Auth (GoTrue) and PostgREST clients over OkHttp, row models, token store, and the auth/sync repositories.
- `supabase/`: Postgres schema with Row Level Security, Edge Functions for Razorpay order creation and payment verification, and setup notes.
- `backend/`: optional Python Supabase-JWT/Razorpay test backend.

## Scope

One-tap rebook, reuse of passengers from the last booking, seat preferences, PNR/live-status enquiries, and text ticket sharing are supported. Multi-route favorites, a full passenger manager, PDF export, reminders, and live background notifications are future work. Local cancellation changes the demo record; it does not cancel an actual railway ticket or issue a refund.
