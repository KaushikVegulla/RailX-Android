# RailX backend (Supabase JWT + Razorpay test)

Account sign-up, email confirmation, and sign-in are Supabase Auth (project `etaaufimazvjdmpladom`).

`POST /register`, `/verify-email`, `/login`, and `/resend-code` return **410**. The Android app talks to Supabase directly.

Payment routes require `Authorization: Bearer <Supabase access token>` unless `SUPABASE_REQUIRE_JWT=0`. Tokens are verified against the project JWKS (`/auth/v1/.well-known/jwks.json`); projects on the legacy HS256 secret can set `SUPABASE_JWT_SECRET`.

| Env | Default |
|---|---|
| `SUPABASE_URL` | `https://etaaufimazvjdmpladom.supabase.co` |
| `SUPABASE_JWT_AUD` | `authenticated` |
| `SUPABASE_JWT_SECRET` | _(unset; legacy HS256 only)_ |
| `SUPABASE_REQUIRE_JWT` | `1` |

## Payments

### Create Order
`POST /create-order`  
Body: `{ "amount": 1670 }` (INR rupees)  
Creates a Razorpay order and returns `order_id`, `key_id`, `amount` (paise).

### Verify Payment
`POST /verify-payment`  
Body: `{ "razorpay_payment_id", "razorpay_order_id", "razorpay_signature" }`  
HMAC-SHA256 verifies the signature, then confirms the booking.

## Run

```bash
cd backend
python3 server.py
```

Default port **8088**. Android emulator uses `http://10.0.2.2:8088`.

Keys are loaded from `.env`. Never ship `KEY_SECRET` or `RESEND_API_KEY` in the Android app.
