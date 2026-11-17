# RailX on Supabase

Supabase is RailX's backend: Auth for accounts, Postgres for synced data,
and Edge Functions for the Razorpay steps that need a server secret.
The Android app talks to it with the project URL and the publishable key.
That key is safe in the app only because every table has Row Level
Security policies scoped to the signed-in user — see `schema.sql`.

- Project: `etaaufimazvjdmpladom`
- URL: https://etaaufimazvjdmpladom.supabase.co
- Never commit or ship the secret / service_role key. It bypasses RLS.

## Database

`schema.sql` creates `profiles`, `bookings`, `passengers` and
`favourite_routes`, enables RLS on all of them, adds owner-only policies,
and adds a trigger that creates a profile row on signup.

Status: **applied to the live project on 17 Nov 2026** and verified
(4 tables, RLS on, 16 policies, 4 triggers). The file is idempotent, so
it can be re-run in the dashboard SQL Editor if a fresh project is made.

## Auth settings (dashboard)

Verified live on 17 Nov 2026:

- Email sign-in enabled, sign-up open
- Email confirmation required (new users confirm before signing in)
- Google and phone sign-in disabled
  - Phone OTP stays off: OTP SMS to Indian numbers needs a TRAI DLT
    header, which RailX does not have yet
  - Google needs an OAuth client configured here before it is enabled

## Edge Functions (payments)

`functions/create-order` and `functions/verify-payment` hold the
Razorpay secret server-side. Both require a signed-in Supabase user.

Deploy with the Supabase CLI from the repo root:

```sh
supabase link --project-ref etaaufimazvjdmpladom
supabase secrets set RAZORPAY_KEY_ID=rzp_test_... RAZORPAY_KEY_SECRET=...
supabase functions deploy create-order verify-payment
```

Status: code is in the repo, **not deployed yet**. Deployment and the
Razorpay test secrets are the remaining manual step. Until then the app
keeps its existing demo payment modes.

## Free tier note

Free Supabase projects pause after about a week of inactivity and need a
visit to the dashboard (or an API call) to wake up. For a demo that is
acceptable; revisit before any real-user release.

## App wiring status

- `SupabaseAuth` / `SupabaseAuthRepository`: sign-up, sign-in, refresh,
  sign-out against Supabase Auth, with tokens in `SupabaseTokenStore`
  and the identity mirrored into `SessionStore`.
- `SupabaseDb` / `SupabaseSyncRepository`: bookings, passengers,
  favourite routes and profiles against PostgREST, failing soft when
  signed out or offline. Room remains the offline cache.
- Follow-up: `AppViewModel` still drives the Cognito + local demo flow.
  Switching its sign-in/registration to `SupabaseAuthRepository` is a
  separate change once PR #3 (demo account sign-in) has merged, so the
  two auth changes do not collide in the same files.
