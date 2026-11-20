# VeloCache AI

A predictive caching middleware built with Spring Boot, Redis, MySQL and a Python/scikit-learn forecasting worker, with a React dashboard.

**Run the whole thing with one command (Docker required):** see [Run it in 3 steps](#run-it-in-3-steps).

## The problem

When a website suddenly gets a burst of visitors, the first request for each popular item has to go all the way to the slow database. Normal caching only fills the cache *after* someone has already waited. During a real surge, those slow first requests pile up and can use up the database's connections.

VeloCache AI sits in front of the application and counts traffic per tenant in Redis. A small Python forecaster looks for a repeating busy period, and a scheduled job fills the cache shortly before the next one arrives, so the first visitors in a burst find their answer already waiting.

## Architecture

```
Client (sends a JWT)
   |
   v
[ TrafficTrackingInterceptor ] -- verifies the JWT, takes tenant_id from it
   |        |                     (missing/invalid token -> 401)
   |        +--> Redis  velocache:traffic:{tenant}:{endpoint}   (sliding window, 15 min)
   v
[ RateLimitInterceptor ]  100 requests / 60 s per tenant + endpoint
   |                      (atomic Lua script, fails open if Redis is down)
   v
[ DataController ] --hit--> Redis  velocache:cache:{tenant}:{endpoint}
   | miss
   v
[ OriginService ]  (simulated, 200 ms) --> fills the cache key (2 min shelf life)

== TENANT BOUNDARY: every Redis key carries {tenant} from the verified JWT ==

Background loops, each every 30 seconds:

[ TelemetryExportScheduler ] reads traffic keys --> forecaster/*-export.csv
   |
   v
[ Python forecaster worker ] --> forecaster/*-forecast.json
   |
   v
[ CacheWarmingScheduler ]  only if: confidence "high" + fresh file
   |                       + spike less than 60 s away + endpoint /api/data
   v
fetch from OriginService --> write the same cache key before the rush

Two doors for the dashboard:
  /api/dashboard/**  staff door, needs the X-Ops-Key header, real tenants
  /api/public/**     no key, read-only, only an allow-list of demo tenants
```

The app and the forecaster share one folder (`forecaster/`): the app writes exports and reads forecasts, the worker does the opposite. There is no message queue.

## Run it in 3 steps

Requires Docker Desktop (Compose v2). Ports `8080` (API) and `5173` (dashboard) must be free.

1. **Clone** the repo and open a terminal in it.
2. **Copy `.env.example` to `.env`** and fill the three values:
   - `MYSQL_PASSWORD`: any password for the bundled MySQL.
   - `VELOCACHE_OPS_KEY`: the key you will type into the dashboard's Staff login.
   - `VELOCACHE_JWT_SECRET`: at least 32 characters. The app refuses to start with a shorter one.

   `.env` is gitignored. Never commit it.
3. **Start everything:**

   ```
   docker compose up --build -d
   ```

Open http://localhost:5173. The first build downloads dependencies and took about 6.5 minutes on the author's laptop; later builds with a warm Docker cache took under 30 seconds.

Useful commands: `docker compose ps`, `docker compose logs app --tail 25`, `docker compose down` (keeps the database), `docker compose down -v` (also wipes the database).

Notes:
- Redis and MySQL are not published to your machine; only the API and dashboard are.
- Containers run in UTC, so time labels on the dashboard are UTC.
- The dev profile is off in Docker, so `POST /api/dev/token` does not exist (404).

## What you will see

The dashboard opens straight into a **public demo view** (no key), with three demo tenants:

- **demo-a, demo-b**: use **Simulate traffic** to send a short burst of 40 requests through the real cache path, and **Kill Redis (simulated)** to make that tenant behave as if Redis were unreachable for 30 seconds. Both buttons have per-tenant cooldowns. The outage is a per-tenant flag in the app; it never touches the shared Redis connection.
- **demo-rhythm**: an automated tenant that receives a burst of 40 requests every 5 minutes, plus one quiet request per minute in between. This is the one that lets the real forecaster and warmer act. After the first two bursts (about 10 minutes after startup), you will see a forecast marker and warming events. Visitors cannot add bursts to it.

The simulate buttons do **not** produce a forecast by themselves: the forecaster needs to see a repeating rhythm.

**Staff login** (top right) asks for your ops key and switches to the real tenants (`tenant-a`, `tenant-b`) through the key-protected routes.

## What is simulated and what is real

**Simulated.** There are no real users. All traffic comes from scripts or built-in demo jobs. The "origin" behind the cache waits 200 ms; it is not a real database. The quiet one-request-per-minute background traffic for `demo-rhythm` is simulated too.

**Real.** Everything between the traffic and the origin runs for real, on one machine: the Spring Boot interceptors, JWT-based tenant scoping, the Redis sliding-window counters and rate limiter, the cache-aside path, the Python forecaster, the scheduled warmer and the dashboard.

## Measured results

These are single observations on one laptop, with simulated traffic and a simulated 200 ms origin. They show the mechanism works; they are not statistics.

**Three-run benchmark (20 Nov 2026).**

These include a three-run benchmark and earlier single observations on one laptop, with simulated traffic and a simulated 200 ms origin. They show the mechanism works; they are not statistical proof or production-scale evidence.

1. cache-disabled,
2. cache-aside,
3. predictive warming.

The traffic profile used 1 dedicated benchmark tenant per run, 150-request bursts, a 120-second burst duration, a 300-second spike interval, and one quiet request every 2 seconds. Each run had 60 seconds of warm-up and 60 seconds of cooldown. The cache was reset by deleting only the benchmark tenant's Redis keys before each run; no global `FLUSHDB` was used.

The laptop/environment recorded in the benchmark evidence was Windows 10 AMD64, 16 logical CPUs, 7083802624 bytes of Docker-visible memory, Python 3.11.9, Docker Server 29.7.2 and Docker Compose 5.5.1.

| Configuration | Runs | `/api/data` 200 responses | Median | P95 | P99 | Cache-hit ratio | Origin calls |
|---|---:|---:|---:|---:|---:|---:|---:|
| Cache disabled | 3 | 1014 | 221.878 ms | 239.211 ms | 243.289 ms | 0.0000% | 1014 |
| Cache-aside | 3 | 1262 | 21.396 ms | 37.416 ms | 45.436 ms | 99.2868% | 9 |
| Predictive warming | 3 | 1266 | 18.853 ms | 36.709 ms | 40.133 ms | 99.2891% | 14 |

There were 0 HTTP 429 responses, 0 transport errors and 0 other `/api/data` status errors in all three configurations. The cache-disabled run had no cache hits. The cache-aside and predictive-warming runs had nearly identical cache-hit ratios.

Across the three predictive-warming runs, 5 warming events were observed. These measurements do not establish statistical superiority or production-scale performance. They show the mechanism on one laptop with simulated traffic, a simulated 200 ms origin and a 900-second run for each repetition.

Raw benchmark files are in `simulator/results/slice-2-20261120234708/`:

- `benchmark-latest.json`
- `benchmark-runs.csv`
- `benchmark-summary.txt`
- one JSON file per run under `runs/`

**Predictive warming (Docker, clean start, automated `demo-rhythm` tenant, 19 Nov 2026).**
- After one burst, the forecast was `confidence: low`, `method: trend`, and the warmer did nothing.
- After two bursts it read `confidence: high`, `method: rhythm`, with the next spike on the 5-minute beat.
- The warmer logged `SPIKE SOON` and `WARMED` about 40 seconds before the third burst. Totals went from 78 hits and 2 misses to 118 hits and 2 misses, so the third burst had 40 hits and 0 misses, while each of the first two bursts had 39 hits and 1 miss. The visible effect is one avoided cold miss (about 200 ms) per burst.
- An earlier run outside Docker, on 18 Nov, showed the same pattern (39/1, then 78/2, then 118/2).

**Redis outage (Redis really stopped, one request, simulated 200 ms origin).** Before the fail-fast fix the request hung with no answer after 20 seconds. After the fix it returned `200` in about 0.22 s, and the app reconnected on its own when Redis came back (about 15 ms for cache hits).

**Simulated outage on `demo-a`.** During the outage burst: 0 hits and 40 misses (a normal burst is 39 hits and 1 miss). The tenant `demo-b` was unaffected.

**Tenant isolation.** 4 of 4 automated tests pass against a real Redis and MySQL: missing or invalid token gives 401; tenant B cannot read tenant A's cached value; tenant B cannot read tenant A's saved history; the rate limit applies per tenant (100 allowed, further requests get 429, another tenant still gets 200).

**Signing key.** With a configured `VELOCACHE_JWT_SECRET`, a token issued before `docker compose restart app` was still accepted afterwards.

**What has not been measured.** The full benchmark is a one-laptop observation with simulated traffic and a simulated origin. It does not measure a real database, real production users, 10K TPS, production-scale concurrency, multiple application instances or a statistically significant population of repeated hardware environments.

## How the forecaster decides

- **Rhythm detector** (`method: rhythm`, confidence `high`): finds peak minutes (at least 80% of the busiest minute), groups them into bursts, and needs at least two bursts. It predicts the next burst start by repeating the last gap. The busiest minute must also be at least 1.5 times the quietest one.
- **Trend fallback** (`method: trend`, confidence `low`): a linear regression over the last 15 minutes. It is only a guess, so it is labelled `low`, and the warmer ignores anything that is not `high`.
- Fewer than 5 minutes of history gives `low` with `insufficient_history`.

The forecast time is built from today's date, so a spike predicted across midnight would be wrong.

## Tests

Java tests use a locally running Redis and MySQL:

```
.\mvnw.cmd test -Dtest=TenantIsolationIntegrationTest
```

## Tech stack

| Tool | What it does here | Why this choice |
|---|---|---|
| Java 21 + Spring Boot 4.1 | The middleware server | Interceptors let one class check every request before any controller runs, which is what token checks, traffic tracking and rate limiting need |
| Redis | Sliding-window traffic counts, rate limiter, cache | In memory, so reads are fast. Sorted sets store each request with its timestamp as the score, so old entries can be trimmed from a moving window; a Lua script makes the rate limit check atomic |
| MySQL | Permanent traffic logs saved by `POST /api/track` | Redis forgets old data on purpose; history that must last goes in a database |
| JWT (jjwt) | Carries the tenant ID in a signed token | The tenant comes from a verified claim and never from client input |
| Micrometer + Actuator | Cache hit and miss counters per tenant | Readable live while the app runs |
| Python, pandas, scikit-learn | The forecaster worker | A separate process, so a forecast failure cannot take the server down |
| React + Vite + Recharts | Dashboard in `dashboard/` | One polling hook feeds every component, so the chart and cards show the same moment |
| Docker Compose | Runs app, forecaster, dashboard, Redis and MySQL together | One command from a fresh clone |

## Running without Docker (development)

1. Start Redis and MySQL (a database named `velocache_db`).
2. Copy `src/main/resources/application.properties.example` to `application.properties` (gitignored), fill in the placeholders and use long random values for `velocache.dev-token-secret` and `velocache.ops-key`.
3. Run `MiddlewareApplication` from your IDE with the `dev` profile from the repo folder (the app reads `forecaster/` by relative path).
4. Install the forecaster libraries from `forecaster/requirements.txt` and run `python forecaster/run_forecaster_loop.py` in its own terminal.
5. In `dashboard/`: `npm install`, then `npm run dev`, and open http://localhost:5173.

To use the dev token endpoint (simulator, benchmark runner) against the Docker stack, use the optional override and set `VELOCACHE_DEV_TOKEN_SECRET` in `.env`:

```
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d
```

## Security notes

- The staff routes (`/api/dashboard/**`) need the `X-Ops-Key` header, compared in constant time. If no key is configured, every request is rejected.
- The public routes (`/api/public/**`) are read-only except two rate-limited demo actions, and only work for an allow-list of demo tenants. Any other tenant gets a 404.
- CORS allows one origin (`velocache.cors.allowed-origin`, default `http://localhost:5173`).
- `POST /api/dev/token` only exists with the `dev` profile.
- Secrets live in `.env` or the gitignored `application.properties`, never in the repo.

## Known limitations

- **Simulated origin.** The origin is a 200 ms wait, and all traffic is simulated.
- **Benchmark scope.** The benchmark evidence is from one Windows laptop and three repetitions. It is not a production-scale or statistical claim.
- **The rhythm detector needs two bursts.** For the first minutes the trend fallback can make a weak guess; it is labelled `low` and ignored by the warmer, but the dashboard may still show its marker.
- **One rhythm per endpoint.** Warming works on an endpoint's cache key, not on items inside it.
- **Midnight.** The forecast time uses today's date. Minutes with no traffic are not filled in as zero.
- **Delay.** Export, forecaster and warmer each run every 30 seconds, so a forecast can take up to about 90 seconds to turn into a warm cache.
- **File hand-off.** The app and forecaster share a folder instead of a queue.
- **Scaling.** Each request is its own Redis entry (kept for a 15-minute window), which would not scale to high traffic.
- **Counters reset on restart.** Cache hit/miss counters are in memory.
- **UTC labels.** Containers run in UTC, so dashboard times are UTC.
- **Tenant IDs** must not contain `_` or `~` (forecast file names depend on them).
- **Not built:** automatic MySQL logging of every request (`POST /api/track` saves a row directly), CI, and any public deployment. This project is meant to run locally or in Docker.
