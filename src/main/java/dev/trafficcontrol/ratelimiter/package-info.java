/**
 * Rate limiting primitives: four interchangeable admission algorithms
 * (fixed window, sliding window counter, token bucket, GCRA) behind one
 * RateLimiter interface, selected and tuned through RateLimiterConfig.
 */
package dev.trafficcontrol.ratelimiter;
