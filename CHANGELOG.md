# Changelog

All notable changes to this project are documented here. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.0.0/); entries are grouped
per milestone, not per commit.

## [Unreleased]

### Milestone 1 — Rate Limiter (in progress)

- Gradle build scaffolding: Java 21 toolchain, Checkstyle, SpotBugs, JaCoCo,
  JMH plugin wiring, CI (PR pipeline + nightly job).
- Repo governance docs (README, LICENSE) and the `docs/` structure.
- `dev.trafficcontrol.ratelimiter`: `RateLimiter` interface with four
  selectable algorithms behind `RateLimiterConfig` + `RateLimiterFactory` —
  `GcraRateLimiter` (flagship, single-`AtomicLong` CAS), `TokenBucketRateLimiter`
  (packed-state CAS comparison), `SlidingWindowCounterRateLimiter` and
  `FixedWindowRateLimiter` (sharing `AbstractWindowRateLimiter`'s Template
  Method for window rollover).
- `NanoClock` seam (`SYSTEM` default, `FakeClock` test double) — every
  algorithm is testable without wall-clock sleeps.
- Unit tests (per-algorithm correctness, boundary edges, the Fixed Window
  boundary-burst flaw reproduced and contrasted with Sliding Window Counter,
  config validation) and concurrent tests (GCRA and Token Bucket, frozen
  clock, exact admitted-count invariant under contention).
- `docs/algorithms/token-bucket-and-gcra.md`, `docs/algorithms/sliding-window-rate-limiter.md`,
  `docs/design/clock-abstraction.md`, `docs/design/configuration-and-extensibility.md`.
- ADR-002 (Clock Abstraction), ADR-003 (GCRA vs. Token Bucket), ADR-009
  (Algorithm Selection via Strategy Pattern), ADR-012 (AtomicLong over
  VarHandle).
- `AbstractRateLimiter` — shared permit validation, replacing three copies
  of the same check.
- `NoOpRateLimiter` (Null Object): always-admit limiter for turning rate
  limiting off via config.
- `RateLimiters` (Facade): one-line entry points for the common cases,
  tying Factory Method, Builder, and Null Object together.

Decorator/Observer and Composite variants were built and then removed —
not a fit for this milestone's actual scope. See
docs/design/configuration-and-extensibility.md, "Patterns considered and
dropped for this milestone."

**Milestone 1 is otherwise complete.**

### Scope note — JMH benchmarking dropped, not deferred

Per explicit decision, JMH benchmarking is no longer a mandatory
requirement for this project — matching
[concurrent-collections-lock-free](https://github.com/JavidanAlizada/concurrent-collections-lock-free)'s
own precedent (that project dropped it entirely for the same reason:
correctness and design work first, performance claims only when there's a
concrete need for them). No throughput/latency/allocation numbers are
claimed anywhere in this project's docs as a result — ADR-002 and ADR-003
already reason about trade-offs qualitatively, not from benchmark data. The
`me.champeau.jmh` plugin wiring in `build.gradle.kts` and the nightly
workflow's benchmark step are left in place but unused, in case a later
milestone changes course; per AGENTS.md §7, this decision applies to this
repo going forward and doesn't reopen automatically.

### Milestone 2 — Timeout Management

- `dev.trafficcontrol.timeout`: `Deadline` (reuses `NanoClock`), three
  interchangeable `TimeoutScheduler`s (`ScheduledExecutorServiceTimeoutScheduler`,
  `VirtualThreadTimeoutScheduler`, `HashedWheelTimeoutScheduler` — flagship,
  hand-rolled), `TimeoutExecutor` (sync `execute` via `Future.get(timeout)`,
  async `executeAsync` via the scheduler), `TimeoutConfig` +
  `TimeoutExecutorFactory`.
- Tests: a shared `TimeoutSchedulerContractTest` run against all three
  schedulers, `Deadline` (deterministic, fake-clock), `TimeoutExecutor`
  sync/async, and a concurrent schedule/cancel stress test for the hashed
  wheel.
- `docs/algorithms/timeout-management.md`, ADR-006 (Timeout Enforcement
  Mechanism).

### Scope note — documentation consolidated into README, docs/ removed entirely

Per explicit decision (revised twice in the same session): first the empty
`docs/{architecture,benchmarks,performance,security,operations}/README.md`
stubs and index files were removed as filler with no real content. Then,
on further instruction, the whole `docs/` tree — including the real ADRs
and algorithm/design write-ups — was removed too, and `CONTRIBUTING.md`/
`SECURITY.md`/`PERFORMANCE.md` were dropped rather than folded in.
`README.md` is now the only documentation file besides `LICENSE` and this
changelog; it covers what/why, usage, build/test, what CI actually does,
containerization (deliberately absent — this is a library), and
contributing, in prose. `principal-engineer-portfolio.md`'s Architecture
Documentation and ADR requirements were revised to match (a single detailed
README instead of a docs/ tree and formal ADR files) — applies portfolio-
wide going forward, not just this repo.

### Milestone 3 — Retry Engine

- `dev.trafficcontrol.retry`: `BackoffStrategy` with five implementations
  (`FixedDelayBackoff`; `ExponentialBackoff`, `FullJitterBackoff`,
  `EqualJitterBackoff` sharing `AbstractExponentialBackoff`'s Template
  Method; `DecorrelatedJitterBackoff` standalone, AWS's formula).
  `RetryExecutor` (Decorator) with sync `execute` (blocks between attempts)
  and async `executeAsync` (reuses Milestone 2's `TimeoutScheduler` for
  non-blocking delayed retries instead of a second scheduling primitive).
  `RetryExhaustedException` carries every prior failure via
  `getSuppressed()`. `RetryConfig` + `RetryExecutorFactory` + `Retries`
  facade.
- Tests: deterministic arithmetic/bounds tests for every `BackoffStrategy`
  (no clock needed at all), sync/async `RetryExecutor` tests (success,
  exhaustion, non-retryable predicate fails fast), config validation.
- `RetryEngineDemo` (`src/test/java`): runnable usage examples, same idea
  as the design-patterns repo's per-pattern `App.java` — kept out of the
  production JAR on purpose.
- README updated with the Retry Engine section and the pattern-selection
  rationale (Decorator/Template Method included because they have a real
  caller here; Command/Chain of Responsibility/a bespoke Observer SPI
  considered and rejected, with reasons).
