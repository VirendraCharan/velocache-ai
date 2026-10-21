# VeloCache AI

A predictive caching middleware built with Spring Boot, Redis, and a Python/Scikit-Learn forecasting layer.

## The problem

When a website suddenly gets a burst of visitors, the first request for each popular item has to go all the way to the slow database. Normal caching only fills the cache *after* someone has already waited. During a real surge, those slow first requests pile up and can use up the database's connections.

VeloCache AI sits in front of the application and counts traffic per tenant in Redis. A small Python forecaster looks for a repeating busy period, and a scheduled job fills the cache shortly before the next one arrives, so the first visitors in a burst find their answer already waiting.

## Architecture

```
Client (sends a JWT)
   |
   v
[ TrafficTrackingInterceptor ] -- reads tenant_id from the verified JWT
   |        |
   |        +--> Redis  velocache:traffic:{tenant}:{endpoint}   (sliding window)
   v
[ RateLimitInterceptor ]  (per IP + endpoint)
   |
   v
[ DataController ] --hit--> Redis  velocache:cache:{tenant}:{endpoint}
   | miss
   v
[ OriginService ]  (simulated, 200 ms) --> fills the cache key (2 min shelf life)

== TENANT BOUNDARY: every Redis key carries {tenant} from the JWT ==

Background loops, each every 30 seconds:

[ TelemetryExportScheduler ] reads traffic keys --> forecaster/*-export.csv
   |
   v
[ Python forecaster loop ] --> forecaster/*-forecast.json
   |
   v
[ CacheWarmingScheduler ]  spike inside 10 min + confidence high + fresh file
   |
   v
fetch from OriginService --> write the same cache key before the rush
```

## Results: warming OFF vs ON

Same simulator, same settings, Redis emptied before each run. The only difference is the warming switch (`velocache.warming.enabled`).

| | Warming OFF | Warming ON |
|---|---|---|
| Data requests served | 417 | 417 |
| Cache misses (slow trips to the origin) | 3 | 2 |
| Hit rate | 99.3% | 99.5% |
| Requests slower than 100 ms | 3 | 2 |
| p99 latency | 57 ms | 57 ms |

**Setup:** one tenant, a 15-minute run, a 2-minute burst every 5 minutes, and data requests only during bursts. Cached items expire after 2 minutes, so every burst starts with an empty cache. The origin is a simulated 200 ms wait.

**What this shows:** the forecaster needs two bursts before it can see the rhythm, so the first two bursts were cold in both runs. In the ON run the warmer filled the cache before the third burst, and one slow first request disappeared. p99 did not change, because only 3 of 417 requests were slow, too few to reach the 99th percentile.

**What this does not show:** this is one run per setting, so it demonstrates the mechanism and is not a general speed-up claim.

## What is simulated and what is real

**Simulated.** There are no real users. A Python script (`simulator/simulator.py`) generates all the traffic: steady background requests, plus a repeating burst (2 minutes every 5 minutes) to imitate a surge. In the benchmark, only one tenant sent traffic, and data requests were made only during bursts. The "origin" behind the cache is a stand-in that waits 200 ms. It is not a real database.

**Real.** Everything between the script and the origin is real and running, measured over real HTTP on one laptop: the Spring Boot interceptors, JWT-based tenant isolation, the Redis sliding-window counters, the cache-aside read path, the Python forecaster, and the scheduled warmer. In earlier phases, with two tenants sending traffic, I checked that tenants stayed isolated in Redis, that the rate limiter kicked in where it should, and that traffic was still recorded when requests were refused.

**Fairness.** The benchmark's request rate was chosen to stay under the rate limit (100 requests per minute), so refused requests could not make the latency numbers look better. Cached items expire after 2 minutes so each burst starts cold; that is a setting I chose to make the effect visible, and it is stated here on purpose.

## Tech stack

| Tool | What it does here | Why this choice |
|---|---|---|
| Java 21 + Spring Boot 4.1.0 | The middleware server | Interceptors let one class check every request before any controller runs, which is what traffic tracking and rate limiting need |
| Redis | Sliding-window traffic counts and the cache | It lives in memory, so reads are fast. Sorted sets store each request with its timestamp as the score, so old entries can be trimmed from a moving window |
| MySQL | Permanent traffic logs for `POST /api/track` | Redis forgets old data on purpose, so history that must last goes in a database |
| JWT (jjwt library) | Carries the tenant ID inside a signed token | The server can verify who a request belongs to without a database lookup, so the tenant comes from a verified claim and never from client input |
| Micrometer + Actuator | Cache hit and miss counters | The counters can be read live at `/actuator/metrics` while the app runs |
| Python + pandas + scikit-learn | The forecaster | It detects the repeating burst rhythm, with a linear regression as a fallback when no rhythm is found. It runs as a separate small script, so a forecast failure cannot take the server down |
| Maven | Builds the project and manages libraries | It downloads and versions every library from `pom.xml` |


## How to run it

Docker Compose is planned but not built yet, so for now the setup is manual. Run everything below from the repo folder (the one containing `pom.xml`).

**1. Start Redis and MySQL.** Redis must answer `PONG` to `redis-cli ping`. MySQL needs a database named `velocache_db`.

**2. Create your settings file.** Copy `src/main/resources/application.properties.example` to `application.properties` (it is gitignored) and fill in the placeholders, including your own MySQL username and password and your Redis settings. `velocache.dev-token-secret` must match `DEV_SECRET` in `simulator/simulator.py`. To switch warming off, add `velocache.warming.enabled=true`. It is on by default.

**3. Start the app.** Run `MiddlewareApplication` from your IDE and wait for `Started MiddlewareApplication`.

**4. Install the Python libraries.**

```
pip install pandas numpy matplotlib scikit-learn requests
```

**5. Start the forecaster loop** in its own terminal:

```
python forecaster/run_forecaster_loop.py
```

**6. Run the simulator** in a second terminal. The word after the file name labels the run in `benchmark_results.txt`:

```
python simulator/simulator.py ON
```

The run takes 15 minutes. For a fair comparison, empty Redis (`redis-cli flushall`) and delete the old `forecaster/*-export.csv` and `forecaster/*-forecast.json` files before each run.

## Known limitations

- **Benchmark scope.** One run per setting, one tenant, 15 minutes, and the simulated origin is a 200 ms wait. It demonstrates the mechanism and is not a general performance claim.
- **The forecaster needs two bursts.** It measures the gap between bursts, so the first two bursts are always cold. It only finds bursts that last minutes, because it counts requests per whole minute.
- **It forecasts one rhythm, per endpoint as a whole.** Warming works on an endpoint's cache key, not on individual items inside it. Items are not tracked separately.
- **The forecast time uses today's date,** so a burst predicted across midnight would be off by a day. Minutes with no traffic are not filled in as zero.
- **Delay.** The export, the forecaster and the warmer each run every 30 seconds, so a forecast can take up to about 90 seconds to turn into a warm cache.
- **The forecaster is run by a separate script** (`forecaster/run_forecaster_loop.py`), not managed by the app. The warmer finds forecasts through the relative folder `forecaster/`, so the app must be started from the repo folder.
- **Scaling.** `redisTemplate.keys()` blocks Redis while it scans, and production would use `SCAN`. Each request is stored as its own Redis entry, which will not scale to high traffic. The 15-minute window keeps it bounded here.
- **The warmer also fills a cache key for `/api/track`,** which nothing reads. It is harmless but wasteful.
- **Not built yet:** asynchronous MySQL traffic logging, Docker Compose, a dashboard, and a public deployment. The rate limiter counts per IP and endpoint, not per tenant.