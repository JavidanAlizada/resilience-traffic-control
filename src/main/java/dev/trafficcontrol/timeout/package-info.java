/**
 * Timeout enforcement and deadline propagation: a synchronous and an async
 * way to bound how long a call may take, plus {@link dev.trafficcontrol.timeout.Deadline}
 * for passing a shrinking time budget through a call chain. Reuses
 * {@link dev.trafficcontrol.ratelimiter.NanoClock} rather than inventing a
 * second clock abstraction.
 */
package dev.trafficcontrol.timeout;
