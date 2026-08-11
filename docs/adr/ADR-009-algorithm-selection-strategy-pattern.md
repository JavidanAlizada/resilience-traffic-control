# ADR-009 — Algorithm Selection via Strategy Pattern + Config Enum

## Context

Rate Limiter ships four interchangeable admission algorithms (GCRA, Token
Bucket, Sliding Window Counter, Fixed Window), and every later Phase 1
mechanism (Circuit Breaker, Retry, Timeout) will similarly need to expose
tunable behavior without forcing callers to know concrete class names.

## Constraints

- Callers must be able to switch algorithms through configuration alone —
  no code change, no new import.
- Concrete algorithm classes should not leak into the public API surface —
  only `RateLimiter` (the interface) and `RateLimiterConfig` are meant to
  be public dependencies.
- Adding a fifth algorithm later must not require touching every call
  site, only the factory and the enum.

## Options

**Option A — One class per algorithm, caller picks the constructor
directly** (`new GcraRateLimiter(...)`, `new TokenBucketRateLimiter(...)`).
Simplest to write, but couples every caller to a concrete class, defeats
"pick the algorithm from config," and makes swapping algorithms a
recompile, not a config change.

**Option B — A single `RateLimiter` class with an internal `if`/`switch`
on an algorithm field, branching inside every method.** Avoids multiple
classes, but every method grows a branch per algorithm, and the four
algorithms' genuinely different state (one `AtomicLong` vs. an
`AtomicReference<WindowState>`) would have to coexist in one class,
unused for whichever branch isn't active.

**Option C — Strategy pattern: one `RateLimiter` interface, four
implementations, selected by a `RateLimiterFactory` reading
`RateLimiterConfig.algorithm()`.**

## Decision

Option C.

## Rationale

This is the textbook case the Strategy pattern exists for: several
interchangeable algorithms implementing one contract, selected at runtime
by configuration rather than compiled-in by the caller. `RateLimiterFactory`
is the single seam that knows about all four concrete classes (which are
package-private specifically to enforce this — nothing outside the
`ratelimiter` package can accidentally depend on `GcraRateLimiter` instead
of `RateLimiter`). Adding a fifth algorithm later means: implement
`RateLimiter`, add an enum value, add a factory branch — no existing code
changes.

## Trade-offs

- One extra layer of indirection (interface dispatch through the factory)
  versus directly instantiating a concrete class — not expected to be
  measurable given each algorithm's own per-call cost, but Experiment 7 in
  the project brief (`config-driven algorithm switch` overhead) exists
  specifically to check that assumption rather than wave it away.
- Four classes and an enum instead of one class with a branch — more files,
  but each file is independently testable and independently reasoned about
  (see the four separate algorithm test classes), which is the actual
  point.

## Consequences

- `RateLimiterConfig.Builder` and `RateLimiterFactory` are the pattern this
  project's later mechanisms (Circuit Breaker's failure-detection strategy,
  Retry's backoff strategy) should follow by default, absent a concrete
  reason not to.
- Concrete algorithm classes stay package-private; any change to make one
  public needs its own justification, not just convenience.

## Alternatives

Would reconsider Option B only if a future profiling result showed the
interface-dispatch indirection was a measurable hot-path cost at extreme
call rates — no such evidence exists, and Experiment 7 is the place that
evidence would show up first.
