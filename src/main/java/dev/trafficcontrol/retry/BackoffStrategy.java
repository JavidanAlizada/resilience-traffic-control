package dev.trafficcontrol.retry;

import java.time.Duration;

/**
 * How long to wait before the next attempt. {@code attempt} is the attempt
 * number that just failed (1-based); {@code previousDelay} is the delay
 * used before that attempt, or {@code null} if it was the first one.
 */
public interface BackoffStrategy {

    Duration nextDelay(int attempt, Duration previousDelay);
}
