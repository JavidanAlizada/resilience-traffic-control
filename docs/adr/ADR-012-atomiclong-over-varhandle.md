# ADR-012 — `AtomicLong` Over `VarHandle` for CAS State

## Context

Project 01 (Concurrent Collections & Lock-Free Structures) hand-rolls
`VarHandle` for its CAS-based structures, deliberately, because controlling
the exact memory-access mode (plain / opaque / acquire-release / volatile)
*is* that project's subject matter. This project's CAS-based state (GCRA's
`theoreticalArrivalTime`, Token Bucket's packed word, and later Circuit
Breaker's state machine) needed the same class of decision made explicitly
rather than copied by default from Project 01's precedent.

## Constraints

- The algorithm being studied in this project is the traffic-control logic
  (GCRA, token bucket, circuit breaker state transitions) — not JVM memory
  model / CAS mechanics, which is Project 01's explicit territory.
- AGENTS.md §3 warns against hiding the algorithm under study behind a
  library abstraction — this decision needed to be checked against that
  rule, not just asserted as "simpler."
- Whatever primitive is chosen must still provide the same happens-before
  guarantees a hand-rolled `VarHandle` CAS would.

## Options

**Option A — `VarHandle`, matching Project 01's convention directly.**
Full control over access mode, consistent style across portfolio projects,
but introduces a second, unrelated "raw memory access" lesson layered on
top of the traffic-control algorithm lesson this project is actually
about.

**Option B — `java.util.concurrent.atomic.AtomicLong`/`AtomicReference`.**
Same CAS + volatile-read/write happens-before guarantees (per the JDK's
own `Atomic*` implementation, itself built on the equivalent of `VarHandle`
underneath), idiomatic, no access-mode ceremony needed since these
algorithms only ever need plain volatile CAS semantics — no opaque/acquire-
release variant is used anywhere in this project's design.

## Decision

Option B — explicit user instruction, adopted project-wide.

## Rationale

AGENTS.md §3's "don't hide the algorithm" rule targets hiding the
resilience/traffic-control logic itself behind a higher-level library
(Resilience4j, Guava RateLimiter) — it does not mandate the lowest-level
JDK primitive available for every internal detail. `AtomicLong` and
`VarHandle` provide identical correctness guarantees for the plain-CAS
usage this project needs; the choice between them is an abstraction-level
decision, not a correctness or transparency one. Project 01 already
demonstrates this project doesn't even need to be internally consistent
with it — Project 01's own MPSC queue milestone switched from `VarHandle`
to `Atomic*` mid-project once `VarHandle`'s access-mode control stopped
being load-bearing for that specific structure.

## Trade-offs

- Loses the ability to use non-volatile access modes (plain, opaque) if a
  future milestone ever needed to relax ordering for a measured
  performance reason — no such need has been identified, and `AtomicLong`
  can still be replaced with `VarHandle` later for a specific class if a
  benchmark motivates it, per this portfolio's "never optimize without
  evidence" rule.
- Slightly more allocation-free/idiomatic-looking code (`AtomicLong` reads
  as "this is atomic state," `VarHandle` reads as "this is a raw memory
  access mechanism") — a readability trade-off in this project's favor
  specifically, given its subject matter.

## Consequences

- Every CAS-based state in this project — `GcraRateLimiter`,
  `TokenBucketRateLimiter`, and later `CircuitBreaker`'s state machine —
  uses `Atomic*` classes, not `VarHandle`. This is a standing decision, not
  a one-off; see doc 02, Decision H.
- Documentation for each algorithm still states the happens-before argument
  explicitly (see `docs/algorithms/token-bucket-and-gcra.md`) — switching
  primitives doesn't reduce the obligation to reason about memory ordering,
  only changes which API expresses it.

## Alternatives

Would reconsider per-class, not project-wide, if a specific future
mechanism genuinely needed a non-volatile access mode for a
benchmark-justified reason — `VarHandle` remains available in the JDK and
nothing here rules it out permanently, it's simply not the default for
this project the way it is for Project 01.
