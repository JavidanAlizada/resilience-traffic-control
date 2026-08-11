# Performance

No JMH numbers exist yet — this file will be filled in once Milestone 1's
benchmark suite lands (per-call admission overhead, throughput/p50/p95/p99
under contention, allocation rate, comparison against Guava `RateLimiter`
and Resilience4j `RateLimiter`; see doc 02's Benchmark plan). Until then,
treat any claim about relative speed between the four algorithms as
unverified — the correctness/concurrency work landed first, deliberately,
per this portfolio's "never say fast without proof" rule.

Known-by-construction (not yet measured) characteristics worth tracking
once benchmarked:

- `GcraRateLimiter` and `TokenBucketRateLimiter` allocate nothing per call
  (single `AtomicLong` CAS loop) — expected to hold up under the JMH GC
  profiler, but not yet confirmed.
- `AbstractWindowRateLimiter` (Fixed Window, Sliding Window Counter)
  allocates a new `WindowState` record on every window rollover and every
  admitted call — a real, documented trade-off (see docs/design), expected
  to show up as non-zero allocation rate where GCRA/Token Bucket show zero.
