# Food Mobo Chain — AgentMail HTTPS email setup

The staging application sends verification and password reset links through the dedicated `food-mobo-chain@agentmail.to` inbox. This address belongs to the project's AgentMail organization, so an owned custom domain is not required. Render Free blocks SMTP; the application calls AgentMail over HTTPS.

## Required Render configuration

Set these variables on **food-mobo-chain-staging** in Render's Environment page. Merge them with existing variables; do not replace the environment or alter MongoDB, JWT, Cloudinary, seed, or image settings.

| Name | Value |
| --- | --- |
| `AGENTMAIL_API_KEY` | Secret inbox-scoped key with `message_send` permission; store only in Render. |
| `AGENTMAIL_INBOX_ID` | `food-mobo-chain@agentmail.to` |
| `APP_PUBLIC_BASE_URL` | `https://food-mobo-chain-staging.onrender.com` |

Create the key in AgentMail Console → API Keys. Use an inbox-scoped, sending-only key if the Console supports that scope. Do not paste the key in chat or Git. The connected AgentMail plugin can send messages and create inboxes, but does not expose a key creation tool or reveal its OAuth credential. A plugin connection alone cannot authenticate the deployed application.

Do not set `MAIL_API_URL` or `MAIL_ALLOW_LOOPBACK_HTTP` on Render. They exist only to run the isolated CI fake API. The normal endpoint is pinned to `https://api.agentmail.to`.

## Verification

Run `./mvnw -B -ntp verify` and `bash scripts/ci_mongo_smoke.sh` with a disposable MongoDB replica set and fake AgentMail server. Only deploy after green CI. Use a user-authorized real mailbox for one live registration and password reset delivery check. An AgentMail API response confirms acceptance, not recipient delivery; check the message's delivery state and the recipient inbox. Never log or share raw account tokens.

The registration link expires after 24 hours and needs a CSRF-protected confirmation POST. Password reset expires after 30 minutes, works once, and revokes prior sessions. Unknown emails receive a generic response, and per-account requests are limited to one send every 90 seconds. Tokens are random and stored only as SHA-256 hashes in MongoDB. Existing accounts with no verification field retain access.

If the API key is absent, the application disables new registration and does not claim to send recovery links. Existing accounts and data stay intact.
