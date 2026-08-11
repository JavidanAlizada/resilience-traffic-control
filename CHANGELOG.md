# Changelog

All notable changes to this project are documented here. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.0.0/); entries are grouped
per milestone, not per commit.

## [Unreleased]

### Milestone 1 — Rate Limiter (in progress)

- Gradle build scaffolding: Java 21 toolchain, Checkstyle, SpotBugs, JaCoCo,
  JMH plugin wiring, CI (PR pipeline + nightly job).
- Repo governance docs (README, CONTRIBUTING, SECURITY, LICENSE) and the
  `docs/` structure.
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

### Still open for Milestone 1

JMH benchmark suite (per-call overhead, throughput/p50/p95/p99, allocation
rate vs. Guava/Resilience4j baselines) — see
`docs/instructions/task-02-resilience-traffic-control/` in the portfolio
workspace for what's tracked.
