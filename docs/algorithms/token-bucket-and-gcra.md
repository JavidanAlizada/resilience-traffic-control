# Token Bucket and GCRA

Two algorithms, one admission contract: given a configured rate
(`permitsPerSecond`) and a burst allowance (`burstCapacity`), decide
whether the current request fits within it. Both are implemented because
the point of this milestone is to make the trade-off between them concrete,
not just argue it — see ADR-003 for the decision this doc backs.

## GCRA (`GcraRateLimiter`) — the flagship

### State

One field: `theoreticalArrivalTime` (TAT), an `AtomicLong` holding a
nanosecond timestamp — the point at which the limiter would be fully
"drained" if no further requests arrive. Two parameters derived once at
construction:

```
emissionIntervalNanos = round(1_000_000_000 / permitsPerSecond)
burstToleranceNanos   = (burstCapacity - 1) * emissionIntervalNanos
```

### Admission (CAS loop)

```
tryAcquire(permits):
  cost = permits * emissionIntervalNanos
  loop:
    now    = clock.nanoTime()
    oldTat = tat.get()
    base   = max(oldTat, now)
    if base - now > burstToleranceNanos:
      return false                          // rate exceeded, no CAS attempted
    newTat = base + cost
    if tat.compareAndSet(oldTat, newTat):
      return true                           // <-- linearization point
    // lost the race — retry with a fresh now/oldTat
```

Reading it as a token bucket: "tokens available right now" is
`(burstToleranceNanos - max(0, oldTat - now)) / emissionIntervalNanos`. When
`oldTat <= now` the bucket is fully drained (idle) and any request is
admitted immediately. GCRA never materializes that ratio — it compares two
timestamps — which is exactly why its state collapses to one field instead
of "tokens" and "last refill time" as two.

### Linearization point

The successful `compareAndSet`. Everything before it (reading `now`,
computing `base`/`newTat`) is local computation invisible to other threads;
only the CAS publishes the decision.

### Progress guarantee

Lock-free, not wait-free:

- **Lock-free**: on any contended round, at least one thread's CAS on `tat`
  succeeds, so the limiter as a whole always makes progress.
- **Not wait-free**: no per-thread bound — under adversarial scheduling one
  specific caller could keep losing the CAS race indefinitely.
- No thread ever blocks or holds a lock. A denied request doesn't retry at
  all — it's a real, correct "no" decided from a single read, not a stalled
  attempt.

### ABA-equivalent argument

Could a thread read `oldTat`, have it change and change back before the
CAS, and succeed spuriously? No: `newTat = base + cost` where `cost > 0`
for any real request, so every successful update strictly increases `tat`.
The field is monotonically increasing for the life of the limiter — it can
never return to a previously observed value. This is a different argument
than a reused/recycled node reference would need; here it's the *value*
itself, not object identity, that can't repeat.

### Clock-safety note

If `clock.nanoTime()` ever returned a value less than a previous call (not
possible for `System.nanoTime()` by contract, but possible for a misused
test double), `base = max(oldTat, now)` still behaves safely — `now` just
has no effect and the decision falls back to whatever `oldTat` already
encodes. See `docs/design/clock-abstraction.md`.

## Token Bucket (`TokenBucketRateLimiter`) — comparison baseline

A token bucket naturally needs two fields — token count and last-refill
time — which don't fit one CAS-able word without bit-packing. This class
packs them into a single `AtomicLong`:

```
high 32 bits: whole token count       (0..burstCapacity)
low  32 bits: last-refill time, milliseconds, truncated to int
```

### Wraparound-safe timestamp comparison

Truncating a growing timestamp to 32 bits means it wraps roughly every 24.8
days. Comparing two truncated values with plain signed `int` subtraction
(`nowMillis - oldMillis`) is correct across that wraparound as long as the
*true* elapsed time between two calls never exceeds ~24.8 days — the same
technique TCP uses for sequence numbers. For a limiter that's actually
being called, this is a large safety margin; a limiter untouched for over
24.8 days would undercount elapsed time on its next call. Documented here
rather than solved, since solving it (state renormalization, or a wider
field) isn't warranted without evidence it matters in practice.

### Refill and admission (CAS loop)

```
tryAcquire(permits):
  loop:
    oldState = state.get()
    (oldTokens, oldMillis) = unpack(oldState)

    nowMillis     = clock.nanoTime() / 1_000_000
    elapsedMillis = max(0, nowMillis - oldMillis)     // wraparound-safe int subtraction
    tokensAdded   = floor(elapsedMillis * permitsPerSecond / 1000)

    refilledTokens = min(burstCapacity, oldTokens + tokensAdded)
    refilledMillis = tokensAdded > 0 ? nowMillis : oldMillis

    admit    = refilledTokens >= permits
    newState = admit ? pack(refilledTokens - permits, refilledMillis)
                      : pack(refilledTokens, refilledMillis)

    if state.compareAndSet(oldState, newState):
      return admit
    // retry
```

### Precision trade-offs versus GCRA

Two real losses, both accepted and documented rather than engineered away:

1. **Millisecond, not nanosecond, refill precision** — chosen because
   packing tokens and a nanosecond timestamp into one 64-bit word doesn't
   leave enough bits for both at any useful range; GCRA needs no packing at
   all, so it keeps full nanosecond precision "for free."
2. **Up to one refill interval of time can be discarded on refill** — when
   `tokensAdded > 0`, the refill reference point resets to `now` instead of
   carrying the sub-token remainder forward, so a caller that refills
   slightly more often than exactly on interval boundaries loses a small,
   bounded amount of accumulated time each time. GCRA has no equivalent
   loss, because it never rounds time into discrete tokens in the first
   place.

This is the concrete version of the claim in ADR-003: GCRA isn't just
"simpler," it structurally avoids two precision trade-offs that any
single-word-packed token bucket has to accept.

### Progress guarantee and ABA

Same shape as GCRA: lock-free, not wait-free, true sharing on one memory
location. ABA doesn't apply to the *token count* component in the sense
that matters — even if the packed word returns to a value it held before
(e.g., tokens go 3 -> 2 -> 3 as tokens are consumed and refilled), any
observer's CAS is still deciding against the actual current state at the
moment of its attempt, which is all correctness requires; nothing is
inferred from the value's history, only from its current contents.
