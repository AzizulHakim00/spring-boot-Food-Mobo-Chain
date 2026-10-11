# Instructor-only demonstration on existing staging data

This feature is intentionally separate from the public, read-only Demo Access button. It allows a specific instructor to interact with the *existing* staging database, using only ten pre-provisioned demo identities (one Admin, one Buyer and eight Sellers).

## Before enabling

- Create or verify a fresh MongoDB backup. Full-operation Admin, Seller and Buyer actions change real staging records.
- Confirm AgentMail is working on Render. Do not paste its API key or access tokens in a chat or repository.
- Set `APP_INSTRUCTOR_ACCESS_ENABLED=true` and `APP_INSTRUCTOR_EMAIL` to the instructor's email in **Render environment variables**. The code **fails closed** outside the exact staging base URL and MongoDB database.
- This is not a public passwordless login. Public Demo Access remains read-only unless a valid email invitation has been accepted.

## Send the invitation only when ready

1. Log in normally as the real application Admin.
2. In the Admin sidebar open **Instructor access**.
3. Click **Send invitation email**. The email is sent only to the configured address, never to a user-entered destination. Invitation tokens are random, stored hashed, expire after 15 minutes and are single-use.
4. The instructor opens the link in their own email and clicks **Start instructor session**. The preview GET never consumes a token.
5. The instructor is initially signed in as the existing demo Buyer account. The **Switch role** button opens Admin, Buyer and eight Seller choices; selecting one uses the app's normal Spring Security role checks and original dashboard.
6. The session expires after one hour or immediately on **End instructor session**, normal logout, or **Revoke all access** by Admin.

The instructor never sees a demo account password or gains a JWT bearer token. The session is authenticated server-side from a Secure HttpOnly SameSite=Lax cookie. On every MVC request, MongoDB verifies the session is still active; changes are audit-logged with HTTP method/path/status, without request bodies, passwords, tokens or query strings.

## Guardrails

- Demo account passwords cannot be changed from an instructor session. Demo Admin also cannot disable the ten shared demo identities, or invite more instructors.
- All other normal role operations use the **existing staging data**. That includes real mutations to menus, orders, food carts and other accounts. Keep backups and use the Admin revoke control as needed.
- The existing public Demo Access button stays read-only.
- The feature is off by default and only allowed if `APP_PUBLIC_BASE_URL=https://food-mobo-chain-staging.onrender.com` and the active MongoDB database is `food_mobo_chain`.
- This access system does not configure or enable a payment gateway. Existing staging payment behavior remains unchanged.
- Instructor email invitations are never sent automatically during deployments or tests. A signed-in Admin explicitly initiates them.

## Troubleshooting

If the Admin sidebar does not show **Instructor access**, verify the two Render settings and the correct staging database. If sending returns an email error, check AgentMail authorization on Render. If a link is invalid, send a new invitation after the cooldown. If a role stops working, verify the relevant seeded account is enabled and not awaiting verification.
