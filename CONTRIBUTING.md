# Contributing

This is a solo portfolio project, but it's built with the same discipline as
a real internal library, so the same rules apply if that ever changes.

## Before opening a PR

```bash
./gradlew compileJava checkstyleMain checkstyleTest spotbugsMain spotbugsTest
./gradlew test
./gradlew jacocoTestReport
```

All four must pass locally before pushing — CI runs the identical set and
will reject anything that doesn't.

## Code style

- Checkstyle is enforced with zero tolerance for warnings (`maxWarnings = 0`).
- Comments explain *why*, not *what* — if a comment just restates the method
  name, delete it. Save comments for genuinely non-obvious things: a
  concurrency invariant, a numeric-precision trade-off, a workaround.
- No resilience/rate-limiting library (Resilience4j, Guava RateLimiter,
  Bucket4j, ...) as an internal implementation detail of any mechanism — the
  algorithm being studied must stay explicit. JDK concurrency primitives
  (`AtomicLong`, `Semaphore`, `ScheduledExecutorService`) are fine to use
  directly; say why in the ADR when the choice isn't obvious.
- Time-based code depends on `NanoClock`, never `System.nanoTime()`
  directly, so tests can drive it deterministically.

## Commits

One logical unit of work per commit — a build change, a class, a test suite,
a doc — not a squash of a whole milestone. Each commit should compile, pass
its own tests, and pass static analysis on its own.

## Design docs before implementation

Any new mechanism gets a short design write-up (algorithm, concurrency
model, memory-model implications, test/benchmark plan) before the
implementation lands — see
`docs/instructions/task-02-resilience-traffic-control/` in the portfolio
workspace for the proposal format.
