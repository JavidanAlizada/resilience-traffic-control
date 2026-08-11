# resilience-traffic-control

Traffic-control and fault-tolerance primitives for the JVM, built from first
principles rather than wrapped around Resilience4j. The point isn't to
reinvent it — it's to make the algorithms (GCRA, token bucket, a hand-rolled
timer wheel, exponential backoff and jitter, and soon circuit-breaker state
machines) and their concurrency/time-semantics reasoning explicit,
provable, and configurable, instead of hidden behind someone else's
library.

**Scope**: four mechanisms built to a genuinely configurable,
professional-framework bar — Rate Limiter, Timeout Management, Retry
Engine, Circuit Breaker — rather than eight built shallowly. No JMH
benchmarking is done in this project — a deliberate scope decision, not a
gap; correctness and design work come first. See [CHANGELOG.md](CHANGELOG.md)
for what's actually landed.

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

## Timeout Management

`dev.trafficcontrol.timeout` — bounds how long a call may take, for both a
synchronous call and a `CompletableFuture`, plus `Deadline` for propagating
a shrinking time budget through a call chain instead of handing each hop a
fresh timeout.

```java
try (TimeoutExecutor executor = TimeoutExecutorFactory.create(TimeoutConfig.builder().build())) {
    String result = executor.execute(Duration.ofMillis(200), () -> callDependency());
}
```

Three interchangeable `TimeoutScheduler`s back the async path: the JDK's
`ScheduledThreadPoolExecutor`, one virtual thread per pending timeout, and
a hand-rolled hashed wheel timer (the flagship — O(1) scheduling).

## Retry Engine

`dev.trafficcontrol.retry` — decorates a call (sync or async) with retry
behavior: how many attempts, how long to wait between them, and which
failures are even worth retrying.

```java
try (RetryExecutor executor = Retries.exponentialBackoff(3, Duration.ofMillis(100), Duration.ofSeconds(10))) {
    String result = executor.execute(() -> callFlakyDependency());
}
```

| Backoff strategy | Formula |
|---|---|
| Fixed Delay | constant |
| Exponential | `min(maxDelay, baseDelay * 2^(attempt-1))` |
| Full Jitter | `random(0, exponentialCap)` — widest spread, best against retry storms |
| Equal Jitter | `exponentialCap/2 + random(0, exponentialCap/2)` |
| Decorrelated Jitter | `min(maxDelay, random(baseDelay, previousDelay * 3))` (AWS's formula) |

The async path doesn't spawn a thread per pending retry — it reuses
`TimeoutScheduler` from Timeout Management (`scheduleTimeout` is exactly
the "run this later, cancellable" primitive a delayed retry needs; see
`RetryEngineDemo` — the demo output shows attempt 1 running on the calling
thread and later attempts running on the scheduler's background thread,
never blocking anything).

A retry that's exhausted throws/completes with `RetryExhaustedException`
carrying every prior attempt's failure via `getSuppressed()` — the JDK's
own "here's what led up to this" mechanism, not a bespoke accessor.

Runnable usage examples: `src/test/java/dev/trafficcontrol/retry/RetryEngineDemo.java`
(sync success-after-failures, exhaustion, a non-retryable predicate failing
fast, and non-blocking async retry) — same idea as the design-patterns
repo's per-pattern `App.java`, kept in test sources since a library
shouldn't ship a `main()` demo in its production JAR.

Design patterns are used deliberately, not decoratively: Strategy
(`RateLimiter`/`TimeoutScheduler`/`BackoffStrategy` and their
implementations, `NanoClock`), Template Method (`AbstractWindowRateLimiter`,
`AbstractExponentialBackoff`), Decorator (`RetryExecutor`), Builder
(`*Config.Builder`), Factory Method (`RateLimiterFactory`/
`TimeoutExecutorFactory`/`RetryExecutorFactory`), Null Object
(`NoOpRateLimiter`), and Facade (`RateLimiters`/`Retries`). Patterns
considered and rejected for Retry Engine, and why (Command, Chain of
Responsibility, a bespoke Observer/listener SPI): each needed a real
caller in this milestone before being included, not just idiomatic fit in
the abstract — see the design proposal doc for the reasoning.

## Build & test

Requires JDK 21.

```bash
./gradlew compileJava checkstyleMain spotbugsMain   # compile + static analysis
./gradlew test                                       # unit + concurrent tests
./gradlew jacocoTestReport                            # coverage report
```

## CI

`.github/workflows/pr.yml` runs on every push/PR to `main`: compile, unit +
concurrent tests, Checkstyle + SpotBugs, JaCoCo coverage, packaging
(`jar`/`sourcesJar`), and a GitHub dependency-vulnerability review.
`nightly.yml` is scheduled infrastructure for a full JMH benchmark matrix —
currently a no-op, since this project doesn't do JMH benchmarking; left
wired in case that changes.

## Containers

No Dockerfile here on purpose: this is a library (no `main()`, nothing that
runs on its own — it's a JAR meant to be a dependency of a service), so
there's nothing to containerize the way there would be for a deployable
process. Worth revisiting if an example/demo service module gets added
later.

## Contributing

Solo portfolio project, built with the discipline of a real internal
library. Before opening a PR: `./gradlew test checkstyleMain checkstyleTest
spotbugsMain spotbugsTest jacocoTestReport` should all pass locally — CI
runs the same set. One logical unit of work per commit.
