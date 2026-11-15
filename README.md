# RailX

RailX is a native Android railway journey demo built with Kotlin and Jetpack Compose. It supports train enquiries, Cognito accounts, a local booking flow, and Razorpay test checkout. It is an academic project and is not affiliated with IRCTC, CRIS, or Indian Railways. Demo bookings are not valid railway tickets.

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

Accounts use the configured Amazon Cognito user pool in `ap-south-1`. Its pool/client IDs are public configuration. Registration has a local demo fallback when Cognito is unreachable. MPIN, biometric unlock, and the local demo Aadhaar/VID check remain available; the latter is not UIDAI verification.

For backend-mediated Razorpay test payments, configure:

```properties
use.local.keys=false
backend.base.url=http://10.0.2.2:8088
```

Then follow [backend setup](backend/README.md). The emulator address above is for local development; deployed backends should use HTTPS. Remote payment calls require a Cognito token.

The existing `use.local.keys=true` mode is for academic experimentation with test keys only. `local.properties` is gitignored, but secrets injected into `BuildConfig` can be extracted from the APK. Never package production payment secrets. This release does not replace the demo payment backend with a production booking service.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest  # running emulator/device required
```

## Existing data on upgrade

The Room v1 → v2 migration preserves booking rows and adds an account-owner column without destructive migration. Older rows have no reliable owner, so they remain on disk but are hidden from account history. Older unscoped saved-passenger/last-journey preferences are also retained but not shown. New records are scoped to the active account; signing out clears contact information from the session.

## Project layout

- `app/src/main/java/com/kaushik/railway/ui/`: Compose screens, shared controls, theme.
- `AppViewModel.kt`: session and journey state.
- `data/repository/`: cancellable enquiry orchestration, caching, account-scoped booking storage.
- `data/db/`: Room entities, owner-filtered queries, schema migration.
- `app/src/test/`: model, account, search filter, and repository unit tests.
- `app/src/androidTest/`: database migration and account-isolation tests.
- `backend/`: optional Python Cognito/Razorpay test backend.

## Scope

One-tap rebook, reuse of passengers from the last booking, seat preferences, PNR/live-status enquiries, and text ticket sharing are supported. Multi-route favorites, a full passenger manager, PDF export, reminders, and live background notifications are future work. Local cancellation changes the demo record; it does not cancel an actual railway ticket or issue a refund.
