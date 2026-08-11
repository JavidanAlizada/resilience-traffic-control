# Configuration & Extensibility

The feature that turns four correct algorithms into something usable as a
framework rather than four classes a caller has to know about by name. This
doc covers Milestone 1's slice of it; the generalized cross-mechanism
registry and external config-file loading are Milestone 5 scope (ADR-008).

## Design patterns, and why each one earns its place here

Per explicit instruction to lean on named design patterns rather than a
flat procedural style, and cross-referencing
`../../../projects/repos/design-patterns` (the iluwatar/java-design-patterns
catalogue) for idiomatic shape — every pattern below solves a real, present
problem in this milestone, not a speculative future one:

### Strategy — `RateLimiter` and its four implementations

```java
public interface RateLimiter {
    default boolean tryAcquire() { return tryAcquire(1); }
    boolean tryAcquire(int permits);
}
```

`GcraRateLimiter`, `TokenBucketRateLimiter`, `FixedWindowRateLimiter`, and
`SlidingWindowCounterRateLimiter` are interchangeable strategies behind it.
Callers depend only on `RateLimiter` — never on a concrete class — which is
what makes "pick the algorithm from config" possible at all. `NanoClock`
(see `clock-abstraction.md`) is the same pattern at a smaller scale: one
production strategy (`SYSTEM`), one test strategy (`FakeClock`).

### Template Method — `AbstractWindowRateLimiter`

Fixed Window and Sliding Window Counter are both "count requests within a
rolling window, roll the window forward when time passes" — they differ
only in `estimatedCount(previousCount, currentCount, windowProgress)`. That
overlap is real (both need identical, easy-to-get-wrong window-rollover
logic), so factoring it into a base class with one abstract method is a
correctness win, not just a style choice: a bug in window-rollover logic
would otherwise need fixing in two places, and the two places could drift.

### Builder — `RateLimiterConfig.Builder`

```java
RateLimiterConfig.builder()
    .algorithm(RateLimiterAlgorithm.GCRA)
    .permitsPerSecond(100)
    .burstCapacity(20)
    .build(); // throws IllegalArgumentException here, not on first tryAcquire()
```

The validating `build()` step is the actual point: an invalid
`RateLimiterConfig` must fail loudly at construction, not silently produce
a limiter that misbehaves the first time it's called deep inside a request
path. This is also where a future config-file loader (Milestone 5) plugs
in — parse external values, call the same builder, get the same validation
for free.

### Factory Method — `RateLimiterFactory`

```java
public static RateLimiter create(RateLimiterConfig config) {
    return switch (config.algorithm()) {
        case GCRA -> new GcraRateLimiter(config);
        case TOKEN_BUCKET -> new TokenBucketRateLimiter(config);
        case FIXED_WINDOW -> new FixedWindowRateLimiter(config);
        case SLIDING_WINDOW_COUNTER -> new SlidingWindowCounterRateLimiter(config);
    };
}
```

This is the one place in the codebase that knows about all four concrete
classes. Everything else — callers, tests going through the public API —
depends only on `RateLimiterConfig` and `RateLimiter`. Concrete
implementation constructors are package-private specifically to keep this
factory as the single seam (see ADR-009).

### Decorator + Observer — `ObservableRateLimiter` + `RateLimiterListener`

```java
RateLimiter observed = new ObservableRateLimiter(delegate)
    .addListener(myMetricsListener);
```

`ObservableRateLimiter` wraps any `RateLimiter` and reports every
admit/reject to registered `RateLimiterListener`s — Decorator for the
wrapping-and-delegating structure, Observer for the listener-registration
protocol. Neither of the four algorithm classes has to know listeners
exist; this is exactly the seam ADR-010's metrics SPI was always going to
need, arriving a little earlier than planned because it's genuinely useful
on its own. Listeners live in a `CopyOnWriteArrayList` — registered once at
setup, read on every hot-path call, which is the textbook case for it.
`CountingRateLimiterListener` is the one concrete listener shipped here —
a thread-safe admitted/rejected tally — so the SPI isn't only ever
implemented by test doubles.

### Composite — `CompositeRateLimiter`

```java
RateLimiter perUserAndGlobal = RateLimiters.allOf(perUserLimiter, globalLimiter);
```

Tiered limiting ("100/sec per user AND 1000/sec globally") is an
AND-composition of several `RateLimiter`s behind one more. Composite is the
honest name for that — not Chain of Responsibility, which is about
delegating to whichever handler claims the request, a different contract
than "everyone must agree." The one real cost of this pattern here — a
later child's rejection doesn't undo an earlier child's already-spent
permit — is documented on the class itself rather than hidden, since
Milestone 1's `tryAcquire` has no reserve/commit step to make the
composition atomic with.

### Null Object — `NoOpRateLimiter`

```java
RateLimiter limiter = disabled ? RateLimiters.unlimited() : RateLimiters.gcra(rate, burst);
```

"Rate limiting is off in this environment" becomes a config choice instead
of `if (limiter != null)` scattered through every caller. The class itself
stays package-private — nothing outside this package should construct it
directly, only ask `RateLimiters` for it.

### Facade — `RateLimiters`

One small class fronting Factory Method, Builder, Composite, Null Object,
and the Decorator/Observer pair, so the common cases are a single static
call and the full `RateLimiterConfig.builder()` ceremony is still there for
anything a shortcut doesn't cover. This is what turns "four algorithms
behind an interface" into something that reads like a framework's front
door instead of an internal implementation detail a caller has to assemble
by hand.

## What's explicitly deferred to Milestone 5

- A `RateLimiterRegistry` — named instances, a shared default config with
  per-name overrides, the shape Resilience4j uses. Building it once,
  generalized across all four Phase 1 mechanisms, is lower-risk than
  building a Rate-Limiter-specific version now and re-deriving the general
  shape three more times.
- External config-file loading (YAML/`.properties`) mapped onto the same
  builders.
- The hot-reload decision (can a live instance's config change without
  recreating it) — ADR-008.

## What's explicitly NOT done here

No resilience/rate-limiting library dependency anywhere in this package —
Strategy/Builder/Factory Method are structural patterns from the JDK's own
vocabulary, not a shortcut around implementing GCRA/Token Bucket/window
counting ourselves. Using a design pattern to organize four
already-hand-rolled algorithms is a different thing entirely from using
Resilience4j to avoid writing them.
