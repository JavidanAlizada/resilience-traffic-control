package dev.trafficcontrol.timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DeadlineTest {

    @Test
    void remainingShrinksAsTheClockAdvances() {
        FakeClock clock = new FakeClock(0);
        Deadline deadline = Deadline.after(Duration.ofSeconds(10), clock);

        assertEquals(Duration.ofSeconds(10), deadline.remaining());

        clock.advance(Duration.ofSeconds(4).toNanos());
        assertEquals(Duration.ofSeconds(6), deadline.remaining());
    }

    @Test
    void neverGoesNegative() {
        FakeClock clock = new FakeClock(0);
        Deadline deadline = Deadline.after(Duration.ofSeconds(1), clock);

        clock.advance(Duration.ofSeconds(5).toNanos());

        assertEquals(Duration.ZERO, deadline.remaining());
        assertTrue(deadline.isExpired());
    }

    @Test
    void notExpiredWithTimeStillLeft() {
        FakeClock clock = new FakeClock(0);
        Deadline deadline = Deadline.after(Duration.ofSeconds(1), clock);

        assertFalse(deadline.isExpired());
    }

    @Test
    void eachHopSeesItsOwnRemainingBudgetFromTheSameDeadline() {
        // This is the whole point of the class: one Deadline, passed through
        // several hops, each hop reading a shrinking budget instead of a
        // fresh fixed duration.
        FakeClock clock = new FakeClock(0);
        Deadline deadline = Deadline.after(Duration.ofSeconds(9), clock);

        Duration hop1Budget = deadline.remaining();
        clock.advance(Duration.ofSeconds(3).toNanos());
        Duration hop2Budget = deadline.remaining();
        clock.advance(Duration.ofSeconds(3).toNanos());
        Duration hop3Budget = deadline.remaining();

        assertEquals(Duration.ofSeconds(9), hop1Budget);
        assertEquals(Duration.ofSeconds(6), hop2Budget);
        assertEquals(Duration.ofSeconds(3), hop3Budget);
    }
}
