package dev.trafficcontrol.circuitbreaker;

/** Counts read out of a SlidingWindow at one moment. Rates are percentages, 0 when the window is empty. */
record WindowSnapshot(int totalCalls, int failedCalls, int slowCalls) {

    static final WindowSnapshot EMPTY = new WindowSnapshot(0, 0, 0);

    double failureRate() {
        return percentOfTotal(failedCalls);
    }

    double slowCallRate() {
        return percentOfTotal(slowCalls);
    }

    private double percentOfTotal(int count) {
        return totalCalls == 0 ? 0.0 : count * 100.0 / totalCalls;
    }
}
