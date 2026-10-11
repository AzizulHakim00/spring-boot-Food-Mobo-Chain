# Food Mobo Chain — Resend HTTPS email setup

**Status:** The application-side integration and tests are present on the staging feature branch. Actual outbound email remains unavailable until your Resend account and a verified sending domain are connected and the two secrets are configured securely on Render.

## Why Resend instead of Gmail SMTP?

The staging web service is on Render Free. Render blocks outbound TCP ports 25, 465 and 587, so Gmail SMTP/app-password approaches won't deliver reliably from this service. The application sends plain-text messages through the Resend HTTPS API instead.

## Required one-time account and domain setup

1. Open https://resend.com and sign in or register (this is an account-owner action).
2. In **Domains**, add a domain or subdomain you own, such as `mail.yourdomain.com`. Copy the TXT/CNAME records Resend supplies into that domain's DNS management. Wait until its domain status is **Verified**. If you have no domain, you must obtain one before sending to arbitrary users; Resend's `resend.dev` test sender is restricted and is not a public-production sender.
3. Create a **sending-only** Resend API key. Do not paste it into chat or commit it to Git.
4. Open **Render > food-mobo-chain-staging > Environment** and add the following keys without deleting existing environment values:

| Name | Value |
|---|---|
| `RESEND_API_KEY` | Secret key created in Resend (store only on Render) |
| `MAIL_FROM` | `Food Mobo Chain <no-reply@your-verified-domain>` |
| `APP_PUBLIC_BASE_URL` | `https://food-mobo-chain-staging.onrender.com` |

**Do not set** `MAIL_ALLOW_LOOPBACK_HTTP` or `MAIL_API_URL` in Render: their defaults enforce the official HTTPS API host; overrides are only for the isolated CI mock.

5. Deploy/redeploy the updated staging commit after GitHub CI succeeds and allow Spring Boot to start. Do not change `MONGODB_URI`, `JWT_SECRET_BASE64`, Cloudinary secrets, or food data.
6. Check **Resend > Emails** for Accepted/Delivered, and use a mailbox you own for the browser tests below. Provider **Accepted** is not proof that the recipient inbox received the message.

## Buyer acceptance flow

1. Create a **new** buyer account using a real inbox that has never been registered. The success message instructs you to check email; the account has `emailVerified=false`.
2. Confirm an attempt to sign in before verification fails.
3. Open the verification email's URL. This GET only displays a **Verify my email** button; it does not activate the account automatically (avoids scanner prefetch).
4. Press the confirmation button. You should be redirected to login with an activation message. Sign in succeeds; the same token is no longer valid.
5. In login, open **Resend verification email** to check the generic, privacy-preserving resend form. Requests less than 90 seconds apart do not issue a second email.

## Seller acceptance flow

Create a new seller account from `/seller/register`. They must verify their email before login, **and** their food cart still requires separate admin approval to open publicly. Verification never grants admin approval.

## Password reset acceptance flow

1. Choose **Forgot password?** from login and enter the email address.
2. Check the inbox for an email containing `/reset-password?token=...`. It expires after 30 minutes. Unknown emails receive the same generic public response.
3. Open the link and choose a strong new password. The link becomes invalid immediately and older JWTs are revoked by `sessionVersion`.
4. Test that old credentials fail and the new credentials work.
5. Reset requests sent less than 90 seconds apart are rate-limited per account.

## Security and operational notes

- All new account verification and password reset tokens have 256 bits of randomness. Only SHA-256 hashes are stored in MongoDB; emails alone contain the raw links.
- New users have `emailVerified=false`. Existing historical accounts with a missing/null field remain compatible and may still log in.
- CSRF protection is required for the final confirmation POST; a GET link preview never changes a user record.
- Resend credentials are not logged or exposed in responses. Delivery failures show generic user messages and do not bypass email verification.
- `emailVerificationTokens` stores one active token per user by deterministic MongoDB `_id`; migration indexes include unique hashes and TTL cleanup. Do not run bulk migrations on Atlas without a backup. Expiry is checked by application code independently of TTL.
- CI only contacts a loopback fake API with disposable demo credentials. Never use that test endpoint in production.
- Existing synthetic addresses like `@foodmobo.local` cannot receive external email. Use a real mailbox for acceptance.
- If Resend is not yet configured, login and existing order/cart features remain available, but new registrations and reset requests cannot send email and registration is disabled in the UI.

## Verification

Automated tests: `./mvnw -B -ntp verify`, `bash scripts/ci_mongo_smoke.sh` (disposable replica set and fake provider). The live deployment smoke still checks public pages and images only; deliverability requires a real configured Resend domain.

No real email will be sent unless the verified sender credentials are added to Render.
