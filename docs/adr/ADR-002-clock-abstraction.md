# ADR-002 — Clock Abstraction for Deterministic Testing

## Context

Every mechanism in this project (Rate Limiter now; Timeout, Retry, Circuit
Breaker later) makes decisions based on elapsed time — token refill
intervals, timeout budgets, backoff delays, HALF_OPEN windows. Time-based
logic is only as testable as time itself is controllable.

## Constraints

- Tests must exercise exact nanosecond boundaries (e.g. "denied 1ns before
  refill, admitted exactly at it") without relying on real elapsed wall
  time.
- Production code must use a monotonic time source — `System.nanoTime()`'s
  contract (arbitrary origin, immune to wall-clock adjustment) is what the
  refill/backoff/timeout math actually needs.
- The seam must be cheap on the hot admission path — every `tryAcquire`
  call reads the clock at least once.

## Options

**Option A — `Thread.sleep` in tests, `System.nanoTime()` directly in
production code.** No seam at all. Tests are slow, flaky under CI load,
and structurally unable to hit nanosecond-precision boundaries.

**Option B — A full `java.time.Clock`-based abstraction.** `Clock` is
wall-clock-shaped (`Instant`, millisecond resolution) — wrong tool for
nanosecond-precision monotonic elapsed-time math, and carries API surface
(time zones, epoch) this project never needs.

**Option C — A minimal, purpose-built `NanoClock` functional interface**
mirroring `System.nanoTime()`'s exact shape, with `SYSTEM` as the
production strategy and an injected `FakeClock` test double.

## Decision

Option C.

## Rationale

The interface needs to match what the algorithms actually consume:
monotonic nanoseconds from an arbitrary origin, nothing else. A
single-method functional interface makes `NanoClock.SYSTEM` (a method
reference) the zero-cost production default, and makes every algorithm
constructor take a `NanoClock` explicitly — so "did this class remember to
use the injected clock instead of calling `System.nanoTime()` directly" is
a one-line code-review check, not a subtle bug waiting to surface as test
flakiness.

## Trade-offs

- One more constructor parameter (`NanoClock clock`) on every algorithm
  class, always defaulted to `NanoClock.SYSTEM` at the `RateLimiterConfig`
  builder level so callers outside tests never have to think about it.
- The abstraction buys nothing for correctness in production — `SYSTEM` is
  just `System::nanoTime`. Its entire value is testability.

## Consequences

- Every time-based test in this project (`FakeClock`-driven) runs in
  microseconds of real wall-clock time and can assert exact nanosecond
  boundaries deterministically — see
  `GcraRateLimiterTest#deniesOneNanosecondBeforeRefillAndAdmitsExactlyAtIt`.
- Every later milestone (Timeout, Retry, Circuit Breaker) reuses this same
  seam rather than inventing its own — established once, here.

## Alternatives

Would reconsider Option B if this project ever needed wall-clock
comparisons across processes (it doesn't — everything here is single-JVM,
single-instance elapsed-time math). Would reconsider a heavier abstraction
if `NanoClock` ever needed more than one method — no evidence of that yet.
