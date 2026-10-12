# Phase 3: GitHub -> Render Docker + MongoDB Atlas + Cloudinary

Status: IMPLEMENTED IN SOURCE; live accounts and build remain unverified. Do not label the application deployed or fully tested until the GitHub workflow completes and a real staging run passes.

## Architecture

- GitHub repository hosts Java, Thymeleaf templates, Dockerfile, Render Blueprint and GitHub Actions. Never commit exported user records, SQL dumps, password hashes, Cloudinary API secret, JWT secret, or MongoDB URI.
- Render Free hosts **one** Dockerized Spring Boot 4 / Java 21 MVC application. Thymeleaf templates and bundled demo WebP images are shipped with it; Render's local disk is ephemeral.
- MongoDB Atlas Free stores application data and transaction-capable replica set state. Import the private Phase 2 export **out of band**, and run `migration/mongo_indexes.js` for uniqueness/query performance. Never automatically seed real users at startup.
- Cloudinary stores *new* seller food/cart images and admin category images. The server accepts at most 2MB PNG/JPEG/WebP and makes signed upload requests directly to Cloudinary. Mongo stores only the CDN URL. Original bundled images remain valid.
- Caffeine holds *only* categories, featured foods, approved public carts; bounded to 200 entries/cache and 3-minute TTL. Mutations evict after Mongo commits. Cache resets on Render sleep/restart. Auth, orders, payment state, reviews and carts are **not** cached.
- JWT: browser authentication is via a 30-minute **Secure + HttpOnly + SameSite=Lax** access cookie; API `/api/**` uses bearer header only. CSRF remains enforced for Thymeleaf forms and ignored for bearer-only `/api/**`. The filter reloads user status/role on every request. Logout removes browser cookie. Accounts are stored in MongoDB and passwords use BCrypt.

## 1. Set up Atlas free (M0)

1. Create an Atlas project and M0 cluster in the available free-tier region. Create a database user restricted to `food_mobo_chain` with read/write permission, not Atlas-admin permissions.
2. Choose `food_mobo_chain` as database name in the SRV URI. Escape any password URI reserved characters.
3. Atlas Network Access must allow the current Render service's outbound traffic. Use Render's actual outbound IP ranges if available; if you temporarily allow 0.0.0.0/0 for development, recognize that the cluster will be publicly reachable for attempted connections and depend on TLS + strong credential policies.
4. Privately import `FoodMoboChain_MongoDB_Phase2_Private_Export.zip` into Atlas (contains password hashes and personal data) using `mongoimport` or the documented Python tools. Configure collection indexes from `migration/mongo_indexes.js`.
5. Ensure the imported accounts are authorized and data reconciles before public deployment. Free Atlas has limits and no managed backups; schedule private offline `mongodump` exports.

## 2. Set up Cloudinary Free

1. Obtain `cloud_name`, API key, API secret from the Cloudinary Dashboard.
2. Only store the secret on Render; NEVER expose it in HTML/JS/GitHub. The backend signs requests with Cloudinary's documented SHA-1 signature procedure.
3. The seller can upload a small file from `/seller/menu/new`, `/seller/menu/{id}/edit`, and `/seller/food-cart`. JavaScript uploads to the protected Spring `/seller/uploads/images` endpoint before the seller clicks Save.
4. Admin can replace a category image from `/admin/categories`. Bundled static images still render without a Cloudinary account.
5. There is no full orphan-image cleanup flow yet: an image uploaded before an invalid/rejected form may remain in Cloudinary. Add deletion/cleanup before high-volume public use.

## 3. Configure the local environment

Generate a unique 32-byte JWT secret and Base64 encode it:

```bash
python -c 'import secrets,base64;print(base64.b64encode(secrets.token_bytes(32)).decode())'
```

Set `MONGODB_URI`, `JWT_SECRET_BASE64`, `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, and `CLOUDINARY_API_SECRET`. For local HTTP dev, set `SPRING_PROFILES_ACTIVE=dev` (turns off Secure attribute only for local HTTP). Run:

```bash
bash ./mvnw -B -ntp test
bash ./mvnw spring-boot:run
```

Test browser login (CSRF required), role routes, seller image uploads, cart, checkout, order processing, admin category image uploads and logout.

For command-line REST authentication:

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"buyer@foodmobo.local","password":"<demo password>"}'
```

Then call `GET /api/auth/me` with `Authorization: Bearer <accessToken>`. No API authentication cookie is set in this flow.

## 4. Push clean sources to GitHub

Repository: https://github.com/AzizulHakim00/spring-boot-Food-Mobo-Chain

```bash
git init
git add .
git status --short  # carefully review staged files, confirm no credentials or private data
git commit -m 'Add MongoDB persistence, JWT, Cloudinary, caching and Docker'
git branch -M main
git remote add origin https://github.com/AzizulHakim00/spring-boot-Food-Mobo-Chain.git
git push -u origin main
```

If the repository already has a history, **do not reinitialize/rewrite it**; create a feature branch and PR instead. GitHub Actions validates Java build, offline migration and Docker build. It is advisable to prevent Render from auto-deploying code until CI is green.

## 5. Deploy Render Blueprint

1. Sign up for Render. Choose New -> Blueprint, connect the GitHub repository and select `render.yaml`. Check it selected the `Free` web service; do not select a paid plan or add persistent disks.
2. Set environment secrets in the Render Dashboard (see `.env.example`). Don't paste them into Blueprint source.
3. Render builds the multi-stage Java 21 image and starts on `$PORT` (normally 10000). `/health` reports process liveness; Render probes `/ready`, which pings MongoDB Atlas and returns 503 if the database is unavailable. Configure Atlas networking/secrets before deploying.
4. Visit the `*.onrender.com` URL. HTTPS is provided. Check logout cookie clearing and CSRF-protected Thymeleaf forms.
5. Smoke-test buyer, seller, admin flows and uploaded image persistence after redeploy and after the service idles. Render Free sleeps after inactivity, and startup can take ~1 minute.

## Limitations & hardening backlog

- This phase is **not proof that the Phase 2 Java migration compiles or functions end-to-end**. CI/build and staging must pass before claiming readiness.
- Render Free has limited memory and idle spin-down. Caffeine is local memory, not Redis/shared state, and will reset on restart.
- JWT is short-lived; no persistent refresh-token rotation/revocation store is implemented. Password resets do not instantly invalidate an existing token (up to 30 min); strict immediate revocation needs an account `tokenVersion`/`passwordChangedAt` check or denylist.
- Atlas free offers no automated database backups. Prepare private export/restore steps.
- Cloudinary or Atlas may change free quotas; check both provider dashboards and avoid paid subscriptions. No 100% cost or uptime guarantee is possible.
- Online payments are still a demo flow, not real payment processing. Do not process actual card details.
- Add MongoDB integration tests, CSRF/JWT MockMvc tests, rate limiting on login/uploads and complete image cleanup before real production usage.
- No actual GitHub push, Atlas import, Cloudinary upload or Render deployment is performed by this source package.
