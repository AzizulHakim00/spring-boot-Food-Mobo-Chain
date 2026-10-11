# Instructor-only demonstration on existing staging data

This feature is separate from public read-only Demo Access. A genuine Admin may send a time-limited private invitation to a chosen recipient inside the app. Once verified, the recipient can use the ten existing demo identities on the *existing* staging database (one Admin, one Buyer and eight Sellers).

## Before enabling

- Create or verify a fresh MongoDB backup. Full-operation Admin, Seller and Buyer actions change real staging records.
- Confirm AgentMail is working on Render. Do not paste its API key or access tokens in a chat or repository.
- Enable `APP_INSTRUCTOR_ACCESS_ENABLED=true` once for the staging service. **No recipient email is configured in Render.** The real Admin types the address into the website and confirms their current password before sending. The feature **fails closed** outside the exact staging base URL and MongoDB database.
- This is not a public passwordless login. Public Demo Access remains read-only unless a valid email invitation has been accepted.

## Send the invitation only when ready

1. Log in normally as the real application Admin.
2. In the Admin sidebar open **Instructor access**.
3. Enter the recipient's email in **Recipient email**, then enter your own current Admin password in **Confirm Admin password**. Click **Send invitation**. The server reauthenticates the real Admin and sends the invitation to that email. Tokens are random, hashed, expire after 15 minutes and can be used once.
4. The instructor opens the link in their own email and clicks **Start instructor session**. The preview GET never consumes a token.
5. The instructor is initially signed in as the existing demo Buyer account. The **Switch role** button opens Admin, Buyer and eight Seller choices; selecting one uses the app's normal Spring Security role checks and original dashboard.
6. The session expires after one hour or immediately on **End instructor session**, normal logout, or **Revoke all access** by Admin.

The invited recipient never sees a demo account password or gains a JWT bearer token. The session is authenticated server-side from a Secure HttpOnly SameSite=Lax cookie. On every MVC request, MongoDB verifies the session is still active; changes are audit-logged with HTTP method/path/status, without request bodies, passwords, tokens or query strings.

## Guardrails

- Demo account passwords cannot be changed from an instructor session. Demo Admin also cannot disable the ten shared demo identities, or invite more instructors.
- All other normal role operations use the **existing staging data**. That includes real mutations to menus, orders, food carts and other accounts. Keep backups and use the Admin revoke control as needed.
- The existing public Demo Access button stays read-only.
- The feature is off by default and only allowed if `APP_PUBLIC_BASE_URL=https://food-mobo-chain-staging.onrender.com` and the active MongoDB database is `food_mobo_chain`.
- This access system does not configure or enable a payment gateway. Existing staging payment behavior remains unchanged.
- Instructor email invitations are never sent automatically during deployments or tests. A signed-in and reauthenticated Admin explicitly initiates them. Only one pending invitation is active at a time; a 5-minute send cooldown applies.

## Troubleshooting

If the Admin sidebar does not show **Instructor access**, verify that the single feature flag is enabled and the app uses the expected staging database. If sending fails, check that the recipient address is valid, your Admin password is correct, and AgentMail can send. If a link is invalid, send a new invitation after the cooldown. If a role stops working, verify the relevant seeded account is enabled and not awaiting verification.
