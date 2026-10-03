# resilience-traffic-control

Traffic-control and fault-tolerance primitives for the JVM, built from first
principles rather than wrapped around Resilience4j. The point isn't to
reinvent it — it's to make the algorithms (GCRA, token bucket, a hand-rolled
timer wheel, exponential backoff and jitter, a circuit-breaker state machine
over a lock-free ring buffer) and their concurrency/time-semantics reasoning explicit,
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

## Circuit Breaker

`dev.trafficcontrol.circuitbreaker` stops calling a dependency once it's
clearly unhealthy, rejects calls cheaply while it recovers, then lets a
bounded number of trial calls through to find out whether it has.

```java
CircuitBreaker breaker = CircuitBreakers.countBased(100, 50, Duration.ofSeconds(30));
String result = breaker.execute(() -> callDependency()); // CallNotPermittedException while OPEN
```

| State | Calls | Leaves when |
|---|---|---|
| CLOSED | all go through, outcomes feed the sliding window | failure rate or slow-call rate reaches its threshold, once `minimumNumberOfCalls` are in the window → OPEN |
| OPEN | all rejected immediately | `waitDurationInOpenState` has passed; the next call moves it → HALF_OPEN |
| HALF_OPEN | exactly `permittedCallsInHalfOpenState` trial calls | all trial results are in: under both thresholds → CLOSED with an empty window, otherwise → OPEN. Also → OPEN if trials don't report back within `maxWaitDurationInHalfOpenState` |

**One immutable object per state, swapped by CAS.** Each state owns its
own data (the window, the time it opened, the trial counters) and the
breaker holds a single `AtomicReference` to the current one. A transition
replaces the whole object, so no state ever starts with counters left over
from the previous one. Many threads can cross the threshold at once, but
only one CAS succeeds, so a trip happens, and is reported, exactly once.

**Count-based window: a lock-free ring buffer.** A writer claims a slot
with `getAndIncrement`, swaps its outcome in with `getAndSet`, and adjusts
the running totals by the difference between what it wrote and what it
actually evicted. Every slot's history is one total order of swaps, so each
outcome is added once and subtracted once, and when writers go quiet the
totals equal a recount of the slots. A concurrent test checks exactly that.
While writers are in flight the three counters can be a call or two out of
step with each other. That's accepted rather than paid for with a CAS retry
loop over one packed word, because a trip decision is statistical anyway.

**Time-based window: per-second buckets, behind a lock.** Rolling a bucket
over to a new second means clearing it while other writers may still be
adding to it. Doing that lock-free needs either all three counts packed
into one long with the epoch (too few bits per count) or a fresh bucket
allocated on every call. A short `synchronized` section is the simpler
correct answer, and the asymmetry with the count-based window is
deliberate. Resilience4j locks both.

**Rate-based tripping only.** Tripping on N consecutive failures can't tell
1 failure in 3 calls from 1 in 3 million, and a single success resets it,
so a flapping dependency never trips. Failure rate and slow-call rate over
a window, gated by a minimum call count, are what production breakers
actually use. A slow call is simply one at or over
`slowCallDurationThreshold`, independent of whether it succeeded.

**Permits tie a result to the state that admitted the call.** A slow call
let in while CLOSED can finish after the breaker has moved to HALF_OPEN. If
it reported to whatever state was current, it would count as a trial call
it was never part of. Each call gets a single-use permit that reports back
to the state that issued it, and later reports on the same permit are
ignored. `execute`/`executeAsync` handle permits for you. An interrupted or
cancelled call releases its permit instead of counting as a failure, since
the caller gave up and that says nothing about the dependency.

**No timer thread.** OPEN → HALF_OPEN happens on the first call after the
wait, so tests drive it with a fake clock and nothing sleeps. The side
effect: an idle breaker keeps reporting OPEN past its wait until the next
call arrives.

**Listeners** (`CircuitBreakerConfig.builder().listener(...)`) are told
about every transition, synchronously, on the thread whose CAS made it, so
keep them fast. A listener that throws is ignored and can't fail the call
that triggered the transition. Two limitations: transitions made by
different threads in quick succession can be reported out of order (each
event's `from`/`to` says which is which), and an event doesn't say which
breaker it came from, since breakers have no names yet.

**With Retry, order matters.** `Retry(CircuitBreaker(call))` counts every
attempt in the breaker. Give the retry a predicate that excludes
`CallNotPermittedException`, otherwise it spends its remaining attempts on
rejections once the breaker opens. `CircuitBreaker(Retry(call))` counts a
whole retry sequence as one call, which hides flakiness from the breaker.
Both behaviors are pinned down by tests.

Runnable examples: `src/test/java/dev/trafficcontrol/circuitbreaker/CircuitBreakerDemo.java`
(an outage tripping and recovering, slow calls tripping with zero failures,
retry stopping at an open breaker, async rejection as a failed future).

## Design patterns

Each pattern is here because something in its milestone calls it, not for
idiomatic fit in the abstract:

- **Strategy**: `RateLimiter`, `TimeoutScheduler`, `BackoffStrategy`,
  the circuit breaker's sliding window, and `NanoClock`.
- **Template Method**: `AbstractWindowRateLimiter`, `AbstractExponentialBackoff`.
- **Decorator**: `RetryExecutor`, and the breaker's `execute`/`executeAsync`.
- **State**: the circuit breaker's CLOSED / OPEN / HALF_OPEN objects.
- **Observer**: circuit breaker transition listeners.
- **Builder**: every `*Config.Builder`.
- **Factory Method**: `RateLimiterFactory`, `TimeoutExecutorFactory`,
  `RetryExecutorFactory`, `CircuitBreakerFactory`.
- **Facade**: `RateLimiters`, `Retries`, `CircuitBreakers`.
- **Null Object**: `NoOpRateLimiter`.
- Not from the GoF book: **Balking** (an OPEN breaker refuses immediately
  instead of waiting), **Value Object** (`CircuitBreakerMetrics`,
  `StateTransitionEvent`, `Deadline`), and a **ring buffer** for the
  count-based window.

Considered and left out, for lack of a caller: Composite and an
Observer-style metrics SPI for the rate limiter (built, then reverted),
Command and Chain of Responsibility for retry, and Health Check, Null
Object and manual force-open/reset controls for the circuit breaker.

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
