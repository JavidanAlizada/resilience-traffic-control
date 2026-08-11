# resilience-traffic-control

Traffic-control and fault-tolerance primitives for the JVM, built from first
principles rather than wrapped around Resilience4j — the point isn't to
reinvent it, it's to make the algorithms (GCRA, token bucket, circuit
breaker state machines, backoff/jitter) and their concurrency/time-semantics
reasoning explicit, provable, and configurable.

Status: Milestone 1 (Rate Limiter) in progress. See
[CHANGELOG.md](CHANGELOG.md) for what's actually landed.

Scope: this repo builds four mechanisms to a genuinely configurable,
professional-framework bar — Rate Limiter, Timeout Management, Retry Engine,
Circuit Breaker — rather than eight shallowly. See
`../docs/instructions/task-02-resilience-traffic-control/01-resilience-traffic-control.md`
in the portfolio workspace for the full scope decision and the documented
(not built) Phase 2 (Backpressure, Failover, Bulkhead Isolation, Adaptive
Concurrency Control).

## Rate Limiter

`dev.trafficcontrol.ratelimiter` — one `RateLimiter` interface, four
interchangeable algorithms selected through `RateLimiterConfig`:

| Algorithm | Role |
|---|---|
| GCRA | Flagship. Single `AtomicLong` (theoretical arrival time), one CAS loop, no packing. |
| Token Bucket | Comparison baseline — packed `(tokens, lastRefillMillis)` state in one `AtomicLong`. |
| Sliding Window Counter | Approximate, O(1)-memory, weights the previous window's count by overlap. |
| Fixed Window | Naive baseline, kept to demonstrate its own boundary-burst flaw. |

```java
RateLimiter limiter = RateLimiters.gcra(100, 20); // 100/sec, burst of 20

if (limiter.tryAcquire()) {
    // handle the request
}
```

Or the full builder for anything the `RateLimiters` shortcuts don't cover:

```java
RateLimiter limiter = RateLimiterFactory.create(RateLimiterConfig.builder()
    .algorithm(RateLimiterAlgorithm.GCRA)
    .permitsPerSecond(100)
    .burstCapacity(20)
    .build());
```

Design patterns used deliberately, not decoratively — see
[docs/design/configuration-and-extensibility.md](docs/design/configuration-and-extensibility.md):
Strategy (`RateLimiter` + its four implementations, `NanoClock`), Template
Method (`AbstractWindowRateLimiter`), Builder (`RateLimiterConfig.Builder`),
Factory Method (`RateLimiterFactory`), Decorator + Observer
(`ObservableRateLimiter` + `RateLimiterListener`), Composite
(`CompositeRateLimiter`), Null Object (`NoOpRateLimiter`), and Facade
(`RateLimiters`).

## Build & test

Requires JDK 21.

```bash
./gradlew compileJava checkstyleMain spotbugsMain   # compile + static analysis
./gradlew test                                       # unit + concurrent tests
./gradlew jacocoTestReport                            # coverage report
```

CI runs the same pipeline on every push/PR to `main` (`.github/workflows/pr.yml`).

## Documentation

- `docs/architecture/` — system context and component structure
- `docs/adr/` — architecture decision records
- `docs/design/` — cross-cutting clock, state-machine, and configuration reasoning
- `docs/algorithms/` — per-mechanism algorithm write-ups and correctness proofs
- `docs/benchmarks/` — raw JMH results and analysis
- `docs/performance/` — capacity planning and performance characteristics
- `docs/security/` — security considerations
- `docs/operations/` — deployment, scaling, troubleshooting, recovery

## License

MIT — see [LICENSE](LICENSE).
