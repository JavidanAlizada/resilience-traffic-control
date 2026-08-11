/**
 * Retry with backoff: a RetryExecutor decorates a call (sync or async)
 * with retry behavior, backed by a pluggable BackoffStrategy. Reuses
 * TimeoutScheduler from the timeout package for non-blocking delayed
 * retries instead of building a second scheduling primitive.
 */
package dev.trafficcontrol.retry;
