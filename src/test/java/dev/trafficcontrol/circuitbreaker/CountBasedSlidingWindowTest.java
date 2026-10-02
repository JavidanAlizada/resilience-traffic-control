package dev.trafficcontrol.circuitbreaker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;
import org.junit.jupiter.api.Test;

class CountBasedSlidingWindowTest {

    // Seeded so a failing run is reproducible.
    private final Random random = new Random(42);

    @Test
    void emptyWindowHasZeroCountsAndRates() {
        WindowSnapshot snapshot = new CountBasedSlidingWindow(10).snapshot();

        assertEquals(WindowSnapshot.EMPTY, snapshot);
        assertEquals(0.0, snapshot.failureRate());
        assertEquals(0.0, snapshot.slowCallRate());
    }

    @Test
    void countsUpToSizeBeforeEvicting() {
        CountBasedSlidingWindow window = new CountBasedSlidingWindow(4);
        window.record(true, false);
        window.record(false, true);
        window.record(true, true);

        assertEquals(new WindowSnapshot(3, 2, 2), window.snapshot());
    }

    @Test
    void oldestOutcomeIsEvictedOnceFull() {
        CountBasedSlidingWindow window = new CountBasedSlidingWindow(3);
        window.record(true, true);
        window.record(true, true);
        window.record(false, false);
        window.record(false, false); // evicts the first failure
        window.record(false, false); // evicts the second

        assertEquals(new WindowSnapshot(3, 0, 0), window.snapshot());
    }

    @Test
    void ratesArePercentagesOfRecordedCalls() {
        CountBasedSlidingWindow window = new CountBasedSlidingWindow(10);
        window.record(true, false);
        window.record(false, true);
        window.record(false, false);
        window.record(false, false);

        assertEquals(25.0, window.snapshot().failureRate());
        assertEquals(25.0, window.snapshot().slowCallRate());
    }

    @Test
    void matchesAReferenceModelOverRandomSequences() {
        for (int run = 0; run < 200; run++) {
            int size = 1 + random.nextInt(20);
            CountBasedSlidingWindow window = new CountBasedSlidingWindow(size);
            Deque<boolean[]> model = new ArrayDeque<>();

            int records = random.nextInt(100);
            for (int i = 0; i < records; i++) {
                boolean failed = random.nextBoolean();
                boolean slow = random.nextBoolean();
                window.record(failed, slow);
                model.addLast(new boolean[] {failed, slow});
                if (model.size() > size) {
                    model.removeFirst();
                }
            }

            int failed = (int) model.stream().filter(o -> o[0]).count();
            int slow = (int) model.stream().filter(o -> o[1]).count();
            assertEquals(new WindowSnapshot(model.size(), failed, slow), window.snapshot(), "run " + run);
        }
    }

    @Test
    void rejectsNonPositiveSize() {
        assertThrows(IllegalArgumentException.class, () -> new CountBasedSlidingWindow(0));
    }
}
