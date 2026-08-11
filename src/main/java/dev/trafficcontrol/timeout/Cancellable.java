package dev.trafficcontrol.timeout;

/** A pending scheduled timeout that hasn't fired yet. */
public interface Cancellable {

    /** No-op if it already fired or was already cancelled. */
    void cancel();
}
