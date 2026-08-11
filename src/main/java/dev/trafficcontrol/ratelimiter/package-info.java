/**
 * Rate limiting primitives: four interchangeable admission algorithms
 * (fixed window, sliding window counter, token bucket, GCRA) behind one
 * {@link dev.trafficcontrol.ratelimiter.RateLimiter} interface, selected
 * and tuned through {@link dev.trafficcontrol.ratelimiter.RateLimiterConfig}.
 */
package dev.trafficcontrol.ratelimiter;
