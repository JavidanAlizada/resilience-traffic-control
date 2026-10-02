/**
 * Circuit breaker: a CLOSED / OPEN / HALF_OPEN state machine fed by a
 * sliding window of recent call outcomes. The count-based window is a
 * lock-free ring buffer; the time-based one is bucketed per second behind
 * a lock.
 */
package dev.trafficcontrol.circuitbreaker;
