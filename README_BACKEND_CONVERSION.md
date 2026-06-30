# VINK Multi Services LLC — Java Backend Conversion

## Status

This repo's original structure (top-level `src/` React frontend, `server/`
Node/Express+TypeScript backend, `supabase/`, `plans/`, etc.) has been kept
**unchanged**, as requested. A new `backend/` directory has been added
containing a Java/Spring Boot port of the backend, built module-by-module
to mirror `server/src/` 1:1 (same route paths, same JSON response shapes,
same in-memory seed data/business logic).

### Converted so far
- `vehicles` module — full Spring Boot port of `server/src/routes/vehicles.ts`
  (fleet, drivers, alerts, geofences, trips, analytics), including the
  in-memory seeded dataset (`server/src/data/vehicleStore.ts`) and the
  JWT `requireAuth`/`requireRole` middleware. Mounted at `/api/vehicles/**`,
  identical to the original.

### Not yet converted (still requires the original Node backend to run)
auth, kpis, subscribers, network, billing, fraud, provisioning, support,
interconnects, alerts, rides, healingDrivers, passengers, sos, bankAccounts,
bankCards, bankPayments, bankTreasury, bankCompliance, bankUsers,
marketplace, public, globalBanking, financialReports, levySystem, afc.

These are large modules (banking, fraud detection, MVNO/telecom, ride-hailing)
and will be ported in the same pattern as `vehicles`: TS types → Java model
classes, in-memory data stores → `@Component` stores, Express routes →
`@RestController`s, with identical paths/payloads/status codes.

## Architecture

- **Backend**: Java 21, Spring Boot 3.3.4, Maven (`backend/pom.xml`).
  - `model/` — POJOs mirroring `server/src/types/*.ts`
  - `store/` — in-memory seeded repositories mirroring `server/src/data/*.ts`
  - `controller/` — REST controllers mirroring `server/src/routes/*.ts`
  - `middleware/` — JWT auth filter + role guard mirroring `server/src/middleware/auth.ts`
  - `config/` — security, CORS, static frontend serving, error handling
- **Frontend**: unchanged React/Vite app in `src/`. The Spring Boot app is
  configured (`WebConfig`) to serve the built frontend (`npm run build`
  output copied into `backend/src/main/resources/static/`) with SPA
  fallback routing, so a single Java process can serve both frontend and
  API in production — same approach the original Express server's intent
  implied, just on the JVM instead of Node.

## Building

```bash
cd backend
mvn clean package
java -jar target/vink-backend.jar
```

A GitHub Actions workflow (`.github/workflows/backend-ci.yml`) builds and
tests the backend automatically on every push that touches `backend/**`.

Note: this conversion was produced in a sandboxed environment without
access to Maven Central, so the Maven build itself has not been executed
end-to-end here — review the CI run on this push for the first real build
confirmation.

## Environment variables

| Variable | Default | Purpose |
|---|---|---|
| `PORT` | 3001 | HTTP port (matches original Express default) |
| `JWT_SECRET` | dev secret | HMAC key for JWT verification |
| `ALLOWED_ORIGINS` | localhost:5173,4173 | CORS allow-list |

## Next steps

Continuing module-by-module: banking (accounts/cards/payments/treasury/
compliance/users) next, since it's the largest and most security-sensitive
domain, followed by MVNO/telecom (subscribers/network/billing/fraud/
provisioning) and ride-hailing (rides/drivers/passengers/sos).
