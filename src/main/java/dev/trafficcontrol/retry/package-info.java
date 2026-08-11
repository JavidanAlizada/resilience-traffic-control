/**
 * Retry with backoff: a {@link dev.trafficcontrol.retry.RetryExecutor} decorates a call (sync or
 * async) with retry behavior, backed by a pluggable {@link dev.trafficcontrol.retry.BackoffStrategy}.
 * Reuses {@link dev.trafficcontrol.timeout.TimeoutScheduler} for non-blocking delayed retries
 * instead of building a second scheduling primitive.
 */
package dev.trafficcontrol.retry;
