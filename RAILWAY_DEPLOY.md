# Deploying to Railway (2 services)

This repo deploys as **two separate Railway services** from the same GitHub repo:

| Service  | Root Directory | Dockerfile             | Config file          |
|----------|-----------------|-------------------------|------------------------|
| backend  | `server`        | `server/Dockerfile`     | `server/railway.json`  |
| frontend | `/` (repo root) | `Dockerfile.frontend`   | `railway.json`         |

The `backend/` folder (Java/Spring Boot) is a work-in-progress port and is
**not** used by either service above — see `README_BACKEND_CONVERSION.md`.

## 1. Backend service

1. In Railway: **New Service → Deploy from GitHub repo** → select this repo.
2. Settings → **Root Directory**: `server`
3. Settings → Build → Railway will auto-detect `server/railway.json` /
   `server/Dockerfile`. If it doesn't, set **Dockerfile Path** to `Dockerfile`.
4. Variables tab → add the vars from `server/.env.example`
   (at minimum set `ALLOWED_ORIGINS` once you know the frontend URL).
5. Deploy. Note the generated public URL, e.g. `https://vink-backend.up.railway.app`.
6. Generate a domain under Settings → Networking if one wasn't auto-assigned.

## 2. Frontend service

1. **New Service → Deploy from GitHub repo** → same repo again.
2. Settings → **Root Directory**: leave as `/` (repo root).
3. Settings → Build → **Dockerfile Path**: `Dockerfile.frontend`.
4. Variables tab → add (using the backend URL from step 1.5):
   - `VITE_API_URL=https://vink-backend.up.railway.app`
   - `VITE_WS_URL=wss://vink-backend.up.railway.app/ws`

   These are build-time variables — Vite bakes them into the static bundle,
   so they must be set **before** the first deploy (or you must redeploy
   after changing them).
5. Deploy. Note the generated frontend URL.

## 3. Close the loop

Go back to the **backend** service → Variables → set:
```
ALLOWED_ORIGINS=https://your-frontend.up.railway.app
```
Redeploy the backend so CORS allows requests from the live frontend.

## Notes

- The backend exposes a WebSocket at `/ws` (live KPI/vehicle/fraud feed) and
  a REST API under `/api/*`. Health check: `GET /health`.
- `VITE_API_URL` / `VITE_WS_URL` default to `localhost:3001` if unset, which
  only works for local dev — production frontend builds must set them.
- Some frontend features (Applications API, banking demo flows) call a
  Supabase Edge Function directly via `utils/supabase/info.tsx` and are
  unrelated to the Node backend above — no extra setup needed for those
  unless you intend to wire up Supabase too.
