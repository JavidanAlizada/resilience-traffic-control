# Clock Abstraction

Every mechanism in this project is time-driven, so this is the one seam
every later milestone (Timeout, Retry, Circuit Breaker) reuses without
re-litigating it.

## The interface

```java
@FunctionalInterface
public interface NanoClock {
    NanoClock SYSTEM = System::nanoTime;
    long nanoTime();
}
```

Deliberately minimal: one method, mirroring `System.nanoTime()`'s contract
(an arbitrary-origin, monotonic-per-JVM-run nanosecond count — not a wall
clock, not comparable across processes). `SYSTEM` is the production
strategy; every algorithm depends on an *injected* `NanoClock`, never on
`System.nanoTime()` directly.

## Why this exists

A rate limiter (or, later, a circuit breaker's HALF_OPEN window, or a
retry's backoff delay) is only testable at its real timing boundaries if
time itself is controllable. Without this seam, testing "does the limiter
refill after exactly one emission interval" would mean either:

- `Thread.sleep`-ing in tests — slow, flaky under CI load, and physically
  incapable of testing nanosecond-precision boundaries, or
- Not testing the boundary at all and hoping the arithmetic is right.

With `NanoClock` injected, `FakeClock` (test-only, see
`src/test/java/.../FakeClock.java`) advances time programmatically and
instantly, so tests like
`GcraRateLimiterTest#deniesOneNanosecondBeforeRefillAndAdmitsExactlyAtIt`
can assert exact nanosecond boundaries deterministically, in microseconds
of real wall-clock test time.

## Monotonic vs. wall clock

`NanoClock` is explicitly the monotonic seam (`System.nanoTime()`-shaped),
used for *elapsed-time* decisions — refill intervals, timeout budgets,
backoff delays. Nothing in this project's Rate Limiter milestone needs wall
clock (`System.currentTimeMillis()` / `Instant`) — there's nothing here
that gets compared across processes or logged as a human-readable
timestamp. `TokenBucketRateLimiter`'s packed representation derives
millisecond values *from* the monotonic clock (`nanoTime() / 1_000_000`),
not from wall-clock time — worth being precise about, since the two are
easy to conflate and have very different failure modes (wall clock can
jump on NTP correction; monotonic nanoTime cannot, by contract).

## What happens if the clock jumps or stalls

- **GCRA**: `base = max(oldTat, now)` — if `now` is stale/behind (a stalled
  clock, or a `now` read that raced with a concurrent update), the decision
  simply falls back to `oldTat`. No incorrect admission results from a
  clock that fails to advance; the limiter just behaves as if less time
  passed than actually did, which is the conservative direction to fail in
  for a mechanism whose job is to say "no."
- **Window limiters**: `rollWindow` treats `elapsed < 0` (an apparent
  backward jump) identically to "no time has passed" — no rollover, no
  crash, no incorrect window reset.
- **Token Bucket**: `elapsedMillis = max(0, nowMillis - oldMillis)` clamps
  the same way, plus the wraparound-safety argument in
  `docs/algorithms/token-bucket-and-gcra.md` for the packed-millis
  timestamp specifically.

All three fail the same direction: toward under-counting elapsed time
(fewer tokens/permits available), never toward over-admitting. That's a
deliberate property, not a coincidence — a rate limiter's job is to be
conservative under uncertainty about time.
