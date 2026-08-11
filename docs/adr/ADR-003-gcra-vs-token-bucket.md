# ADR-003 — GCRA vs. Token Bucket as the Flagship Rate Limiter Algorithm

## Context

A rate limiter's admission state needs to be updated atomically under
concurrent callers. Classic token bucket state is naturally two fields
(token count, last-refill timestamp), which don't fit one CAS-able word
without either bit-packing or CAS-ing a reference to an allocated
immutable snapshot. GCRA is mathematically equivalent to token bucket but
represents all of its state as a single monotonic timestamp.

## Constraints

- Milestone 1's concurrency-primitive decision (ADR — see doc 02, Decision
  H) rules out `VarHandle`; both candidates use `AtomicLong`.
- The flagship implementation should be the one that best demonstrates a
  clean single-CAS design, per this project's "no lock, no allocation on
  the hot path" bar for its primary algorithms.
- Both need to exist regardless of which is "flagship," so the comparison
  is backed by real code and real trade-offs, not just prose (see
  `docs/algorithms/token-bucket-and-gcra.md`).

## Options

**Option A — Token Bucket as flagship, packed into one `AtomicLong`.**
Familiar, widely known name. Requires bit-packing tokens and a timestamp
into 64 bits, which forces either reduced (millisecond) time precision or
a narrower token-count range, plus a wraparound-safety argument for the
truncated timestamp component.

**Option B — GCRA as flagship, Token Bucket as comparison baseline.**
GCRA needs no packing: its one field *is* a full 64-bit nanosecond
timestamp, so it keeps full precision and needs no wraparound handling at
all. Token Bucket is still implemented, specifically so the packing
trade-off is demonstrated on real code rather than asserted.

**Option C — Snapshot-swap Token Bucket** (`AtomicReference` to an
immutable `{tokens, timestamp}` object, CAS-swapped). Avoids packing
entirely, at the cost of an allocation on every contended attempt —
directly measurable, unappealing on a hot admission path, and not pursued
further absent a benchmark showing the packed version's precision
trade-offs matter more than an allocation would.

## Decision

Option B — GCRA is the flagship; Token Bucket (packed, Option A's
approach) is implemented as the explicit comparison baseline.

## Rationale

GCRA structurally avoids two costs Token Bucket's packed representation
has to accept (see `docs/algorithms/token-bucket-and-gcra.md` for the full
argument): millisecond-only precision, and up to one refill interval of
"leaked" time on every refill. Since GCRA needs no packing at all, it has
neither cost — not because it's more cleverly engineered, but because its
minimal-state design sidesteps the problem the packing exists to solve.
Building Token Bucket anyway (rather than skipping it) is what makes this
an evidence-backed decision instead of an assumption.

## Trade-offs

- GCRA is less immediately recognizable by name than "token bucket" to an
  engineer skimming the API — mitigated by `RateLimiterAlgorithm.GCRA`
  being one of four explicit, documented enum values, not the only option.
- Token Bucket's packed implementation is genuinely more intricate
  (bit-packing, wraparound-safe subtraction) for equivalent behavior with
  strictly worse precision — kept anyway, since the milestone's goal is
  demonstrating the trade-off, not avoiding the harder implementation.

## Consequences

- `RateLimiterFactory` and `RateLimiterConfig` treat all four algorithms
  as equally first-class — algorithm choice is a config value, not an
  implicit "the good one plus three toys." GCRA being the flagship affects
  which one gets the deepest documentation treatment, not which one is
  "supported."
- Future milestones needing a single-word CAS state machine (none
  identified yet in Timeout/Retry/Circuit Breaker) should default to
  GCRA's minimal-state pattern over a packed-word pattern unless a
  concrete reason argues otherwise.

## Alternatives

Would reconsider if a future benchmark (Milestone 1's JMH suite, not yet
run) showed GCRA's `Math.max`/branch-per-call pattern meaningfully
underperforming Token Bucket's arithmetic under real contention — no
evidence of that yet, and the algorithms are close enough in per-call work
that a significant difference would itself be a finding worth
investigating rather than assuming.
