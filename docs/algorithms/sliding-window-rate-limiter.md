# Fixed Window and Sliding Window Counter

Both algorithms answer "how many requests have happened in roughly the last
second" — they differ only in whether the previous window's count is
discarded outright or blended in. That single difference is the entire
Template Method step `AbstractWindowRateLimiter#estimatedCount` factors
out; see `docs/design/configuration-and-extensibility.md` for the pattern.

## Shared machinery (`AbstractWindowRateLimiter`)

State is an immutable `WindowState(windowStartNanos, previousCount,
currentCount)` record, swapped via `AtomicReference.compareAndSet`. The
window size is fixed at one second for this milestone (not yet
configurable — a known, deliberate limitation, not an oversight).

On each `tryAcquire`, the window is rolled forward if `now` has moved past
the current window's span:

- Advancing exactly one window: `previousCount` becomes the outgoing
  window's `currentCount`, `currentCount` resets to 0.
- Advancing more than one window (an idle gap): both `previousCount` and
  `currentCount` reset to 0 — the "previous" window is stale too.
- The clock appearing to go backward is treated the same as "no time has
  passed" (no rollover) — consistent with the clock-safety posture in
  `docs/algorithms/token-bucket-and-gcra.md`.

This allocates a new `WindowState` on every window rollover and every
admitted call — unlike GCRA/Token Bucket's zero-allocation CAS loops, an
explicit, accepted trade-off: these two algorithms aren't the packing
lesson, they're the window-boundary/blending lesson, so a small allocation
cost for a much simpler, clearly-correct implementation is the right call
here.

## Fixed Window (`FixedWindowRateLimiter`) — the flaw, kept on purpose

`estimatedCount` just returns `currentCount` — the previous window doesn't
exist as far as the admission decision is concerned.

**The boundary burst flaw**: at a configured limit of, say, 100
requests/second with one-second windows, 100 requests arriving at
`t=0.999s` (the tail of window 1) and another 100 at `t=1.001s` (the head
of window 2) are **all** admitted — 200 requests inside a 2ms span, against
a configured rate of 100/sec. This is not a concurrency bug; it happens
single-threaded, deterministically, every time a burst straddles a
boundary. It's reproduced directly in
`FixedWindowRateLimiterTest#boundaryBurstFlaw_admitsDoubleTheRateAcrossAWindowBoundary`,
which admits 4 requests in a 1-nanosecond simulated span against a
configured limit of 2/sec.

Kept in the codebase specifically so `SlidingWindowCounterRateLimiterTest`
can demonstrate the same scenario denied correctly — the flaw is the
argument for the next algorithm, not a bug to quietly fix in place.

## Sliding Window Counter (`SlidingWindowCounterRateLimiter`)

`estimatedCount` blends the previous window's count in, weighted by how
much of it still falls inside a one-window lookback from `now`:

```
overlapFraction = 1 - windowProgress        // windowProgress in [0, 1)
estimated       = currentCount + round(previousCount * overlapFraction)
```

Right at a window boundary (`windowProgress ≈ 0`), the previous window's
count still carries almost full weight, correctly denying the same burst
Fixed Window admits. As `windowProgress` grows toward 1, the previous
window's contribution decays linearly, until — by the next boundary — it
contributes nothing and the cycle repeats with a new "previous" window.

This is an **approximation**: it assumes requests were spread uniformly
across the previous window, which isn't always true (a burst concentrated
at the very start or end of a window is under- or over-weighted). It is not
an exact sliding log. It's the same approximation most production API
gateways actually ship, because it's O(1) memory regardless of request
volume, unlike a sliding log that stores every timestamp.

## Consistency note

Both algorithms' counts are exact and linearizable at the CAS that
publishes them — but the *admission decision* itself (`estimatedCount`
compared against the limit) is a snapshot read that can be stale by the
time it's acted on under concurrency, same as any optimistic-CAS retry
loop: a losing thread simply retries with a fresh read, so the final
published state is always consistent even though any single observation of
"how close to the limit are we" is only a point-in-time estimate.
