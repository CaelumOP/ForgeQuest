# ForgeQuest Friends Worker

Cloudflare Worker + D1 backend for ForgeQuest Friends (`/v1/*`).

## Test & Typecheck

```sh
npm test
npx tsc --noEmit
```

## Deploy

1. Create the D1 database and paste the returned `database_id` into `wrangler.toml`:

```sh
npx wrangler d1 create forgequest
```

2. Apply D1 migrations:

```sh
npx wrangler d1 migrations apply forgequest --remote
```

3. Deploy the Worker:

```sh
npx wrangler deploy
```

4. (Optional) Configure Firebase Cloud Messaging v1 push notifications using a Google service-account JSON file (never commit `sa.json`):

```sh
npx wrangler secret put FCM_SA < sa.json
```
