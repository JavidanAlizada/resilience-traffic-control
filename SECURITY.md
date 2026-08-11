# Security Policy

## Supported versions

Pre-1.0 — no tagged releases yet. Only `main` is maintained.

## Reporting a vulnerability

This library is itself a rate-limiting/traffic-control building block —
misconfiguration (an unbounded retry policy, a rate limiter with no burst
ceiling) is a more realistic risk than a classic memory-safety bug, since
there's no external input parsing beyond config values. If you find a
vulnerability or a self-inflicted-DoS footgun in the default configuration,
open a GitHub issue or email javidanalizada99@gmail.com. Expect an initial
response within a few days — this is a personal project, not a company with
an on-call rotation.

## Configuration safety

Every `*Config` builder validates at build time (fails fast on
`permitsPerSecond <= 0`, `burstCapacity < 1`, etc.) rather than clamping
silently to a default — a silently-clamped invalid config is exactly the
kind of misconfiguration that could mask a production incident. See
`docs/security/` for anti-patterns specific to each mechanism (e.g.
unjittered retries amplifying an outage into a retry storm).

## Dependencies

Dependency review runs on every PR via `actions/dependency-review-action` in
`.github/workflows/pr.yml`. Runtime dependencies are kept minimal and
explicitly justified per ADR (see ADR-011) — HdrHistogram and a config-file
parser are the only ones expected beyond the JDK.
