# ADR-006 — Timeout Enforcement Mechanism

## Context

The async path of `TimeoutExecutor` needs something to fire a "this call
timed out" action after a delay, unless the real call finished first. There
is no thread already blocked waiting the way there is on the sync path
(`Future.get(timeout)` covers that case without any custom scheduling —
see `docs/algorithms/timeout-management.md`), so this is a genuine
scheduling problem.

## Constraints

- Must support scheduling a delayed action and cancelling it before it
  fires, both from arbitrary caller threads.
- Needs to be viable at scale — a service protecting many concurrent calls
  could have thousands of pending timeouts at once.
- No JMH benchmarking for this project (dropped project-wide, see
  CHANGELOG.md) — the choice has to be defensible on algorithmic grounds,
  not measured ones.

## Options

**Option A — JDK `ScheduledThreadPoolExecutor`.** DelayQueue/heap
internally: O(log n) schedule and cancel, tight firing precision, minimal
code. The obvious default.

**Option B — One virtual thread per pending timeout.** `Thread.sleep`
plus interrupt-to-cancel. Historically a bad pattern with platform threads;
virtual threads' cost profile (KB-scale stacks, M:N onto carriers) makes it
worth having as a real option rather than dismissing it on old advice.

**Option C — Hand-rolled hashed wheel timer.** O(1) schedule (append to a
bucket), firing precision bounded by one tick duration, single background
thread regardless of how many timeouts are pending.

## Decision

Implement all three as interchangeable `TimeoutScheduler` strategies,
selected via `TimeoutConfig.algorithm`; the hashed wheel is the default
(flagship).

## Rationale

None of the three is strictly better — each is the right choice for a
different volume/precision trade-off, so building all three and making the
choice a config value (same Strategy-behind-config shape as
`RateLimiterConfig`) is more honest than picking one and hiding the
trade-off. The hashed wheel defaults because O(1) scheduling matters most
exactly when it matters most — under the highest number of concurrently
in-flight calls — and losing sub-tick timing precision is an acceptable
trade for a *timeout*, where "fired a few milliseconds late" is harmless in
a way it wouldn't be for, say, a real-time scheduler.

## Trade-offs

- Hashed wheel: bounded imprecision (±1 tick), a background thread to
  manage, more implementation surface than the other two.
- `ScheduledThreadPoolExecutor`: O(log n) instead of O(1) — likely
  irrelevant until pending-timeout counts get large, but unverified
  without benchmarks.
- Virtual-thread-per-timeout: one thread object per pending timeout is
  still more memory than a bucket entry, even at virtual-thread cost; the
  scaling claim versus platform threads is structural, not measured.

## Consequences

- `TimeoutScheduler` stays a small, closeable interface so a fourth
  implementation (or a benchmark-driven default change) doesn't require
  touching `TimeoutExecutor`.
- If this project's JMH-dropped stance is ever reversed, comparing these
  three at scale is the first thing worth measuring — the qualitative
  argument above should get real numbers before being trusted further.

## Alternatives

Would reconsider the default if a real workload showed hashed-wheel
imprecision (±1 tick, 10ms by default) causing observable problems, or if
pending-timeout volume in practice never got high enough for O(1) vs.
O(log n) to matter — at which point `ScheduledThreadPoolExecutor`'s
simplicity would be the better default.
