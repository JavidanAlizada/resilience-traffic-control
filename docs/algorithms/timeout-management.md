# Timeout Management

## Why sync and async need different mechanisms

A synchronous call already has a thread blocked waiting for it. Bounding
that wait doesn't need a scheduler: `TimeoutExecutor.execute` submits the
`Callable` to a worker executor and calls `future.get(timeout, unit)` — the
JDK's own `FutureTask` parks the calling thread and wakes it on completion
or timeout. Building a custom timer for this would be redundant machinery
solving an already-solved problem.

An async call has no thread blocked waiting — nothing notices the deadline
passed unless something actively schedules a "fire this unless cancelled"
action. That's the real problem, and it's what the three `TimeoutScheduler`
implementations solve.

## The three schedulers

**`ScheduledExecutorServiceTimeoutScheduler`** wraps a JDK
`ScheduledThreadPoolExecutor` (DelayQueue/heap internally): O(log n) to
schedule or cancel, tight firing precision, one small thread pool services
every pending timeout. The obvious baseline.

**`VirtualThreadTimeoutScheduler`** starts one virtual thread per pending
timeout (`Thread.sleep(delay)`, then fire unless interrupted). Platform
threads made "a thread per timer" a bad idea past a few thousand pending
timeouts; virtual threads (KB-sized stacks, M:N onto carrier threads)
change that calculus. No JMH numbers back this up (dropped project-wide —
see the repo's CHANGELOG) so the scaling claim is structural, not measured.

**`HashedWheelTimeoutScheduler`** (flagship) is a hand-rolled timer wheel,
Netty-style: a ring of buckets, one background thread advancing a tick and
firing whatever's due. A timeout further out than one full rotation carries
a "rounds remaining" counter, decremented each pass. O(1) to schedule
(append to a bucket) instead of the heap's O(log n), at the cost of firing
precision bounded by one tick duration (default 10ms) — a real,
well-known trade-off for timer wheels, not a bug.

Cancellation on all three is lazy where it matters: a cancelled task is
marked and skipped when its bucket/queue entry is next visited, rather than
removed immediately. This avoids mutating a collection the background
thread might be mid-iteration over, and it means cancellation can race a
timeout that's already about to fire — same posture as `ScheduledFuture
.cancel()` on an already-running task. Best-effort, not a guarantee.

## Cooperative cancellation, and what actually happens to abandoned work

`Thread.interrupt()` is cooperative. A worker running CPU-bound work with
no blocking call and no `Thread.interrupted()` check keeps running to
completion even after `future.cancel(true)` — the caller gets a
`TimeoutException` and moves on, but the abandoned call is still consuming
a thread until it finishes on its own. A "timed out" dependency call can
still be running, still holding a connection or a lock, well after the
caller stopped waiting for it. This is a real production failure mode, not
a footnote.

## Deadline propagation

`Deadline.after(timeout, clock)` fixes an absolute expiry point using the
same `NanoClock` the rate limiter package already relies on. Pass one
`Deadline` through a call chain; each hop calls `.remaining()` at its own
point in time and gets a shrinking budget instead of a fresh fixed
duration. Pure arithmetic — no scheduling involved, fully testable with a
fake clock.
