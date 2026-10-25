package dev.opentvtouchpad.hidprobe;

/**
 * Main-thread-only, in-memory eligibility gate for the isolated compatibility probe.
 * Numeric comparison and user confirmation do not prove cryptographic authentication.
 * Peers are Bluetooth addresses, compared exactly; no pairing digits are accepted.
 * Callbacks must carry the generation captured when their session was started.
 */
public final class ProbeSession {
    private final EnrollmentStore enrollment;
    private long generation;
    private boolean running;
    private boolean foreground;
    private boolean registered;
    private String candidate;
    private boolean bonded;
    private boolean confirmed;
    private boolean connected;
    private boolean neutral;
    private String rejectedPeer;

    ProbeSession(EnrollmentStore enrollment) { this.enrollment = enrollment; }

    public String savedPeer() { return enrollment.peer(); }

    public boolean forget() {
        stop();
        return enrollment.forget();
    }

    /** Current OS observations, including while stopped; never a stale run callback. */
    public boolean revoke(String peer) {
        if (peer == null || !peer.equals(savedPeer())) return false;
        forget();
        return true;
    }

    public boolean canConnect(long generation) {
        return isRegistered(generation) && foreground && savedPeer() != null && !confirmed && rejectedPeer == null;
    }

    public boolean connectSaved(long generation, boolean currentlyBonded) {
        if (!canConnect(generation)) return false;
        if (!currentlyBonded) { forget(); return false; }
        candidate = savedPeer();
        bonded = true;
        confirmed = true;
        return true;
    }

    public boolean neutralSubmitted(long generation, String peer, boolean success) {
        if (!active(generation) || !foreground || !connected || peer == null
                || !peer.equals(eligiblePeer()) || neutral) return false;
        if (!success) { stop(); return false; }
        neutral = true;
        return true;
    }

    public long start() {
        stop();
        // Fail closed on exhaustion instead of ever reusing a generation.
        generation = Math.incrementExact(generation);
        running = true;
        return generation;
    }

    public void stop() {
        running = false;
        foreground = false;
        registered = false;
        candidate = null;
        bonded = false;
        confirmed = false;
        connected = false;
        neutral = false;
        rejectedPeer = null;
    }

    public boolean active(long generation) {
        return running && this.generation == generation;
    }

    public boolean isRegistered(long generation) {
        return active(generation) && registered;
    }

    /** Setup evidence survives covering the UI; a confirmed session does not. */
    public void foreground(long generation, boolean foreground) {
        if (!active(generation)) return;
        if (!foreground && confirmed) {
            stop();
            return;
        }
        this.foreground = foreground;
    }

    /** Whether a first explicit comparison can be accepted in the visible UI. */
    public boolean canConfirm(long generation) {
        return isRegistered(generation) && foreground && bonded && candidate != null && !confirmed
            && savedPeer() == null && !enrollment.failed() && rejectedPeer == null;
    }

    public void registered(long generation, boolean registered) {
        if (!active(generation)) return;
        if (!registered) {
            stop();
            return;
        }
        this.registered = true;
    }

    public boolean observePairing(long generation, String peer, int variant) {
        if (!active(generation)) return false;
        if (revoke(peer)) return false;
        if (savedPeer() != null || enrollment.failed()) return false;
        if (variant != 2) {
            // A changed pairing method invalidates the candidate's earlier comparison.
            if (candidate != null && candidate.equals(peer)) stop();
            return false;
        }
        if (!EnrollmentStore.validPeer(peer)) return false;
        if (candidate == null) candidate = peer;
        return candidate.equals(peer);
    }

    public void bonded(long generation, String peer) {
        if (active(generation) && candidate != null && candidate.equals(peer)) bonded = true;
    }

    /** Call only after the user explicitly confirms matching codes on BOTH screens. */
    public boolean confirmComparison(long generation) {
        if (!canConfirm(generation)) return false;
        if (!enrollment.save(candidate)) { stop(); return false; }
        confirmed = true;
        return true;
    }

    public String candidate() {
        return candidate;
    }

    public String eligiblePeer() {
        return running && registered && bonded && confirmed ? candidate : null;
    }

    /**
     * Returns whether this callback belongs to the current eligible peer.
     * After comparison, any candidate disconnect stops even a pending connection.
     * Pre-comparison auto-connections are rejected by the adapter; their deliberate
     * disconnection grants no input and does not discard the pairing observation.
     */
    public boolean connected(long generation, String peer, boolean connected) {
        if (!active(generation)) return false;
        if (!connected && peer != null && peer.equals(rejectedPeer)) {
            rejectedPeer = null;
            return false;
        }
        boolean accepted = peer != null && peer.equals(eligiblePeer());
        if (connected && !accepted && EnrollmentStore.validPeer(peer)) {
            if (rejectedPeer != null && !rejectedPeer.equals(peer)) stop();
            else rejectedPeer = peer;
        }
        if (!connected && accepted) {
            stop();
        } else if (connected && accepted) {
            if (this.connected) { stop(); return false; }
            this.connected = true;
        }
        return accepted;
    }

    public boolean canSend(long generation, String peer) {
        return active(generation) && foreground && connected && neutral && peer != null && peer.equals(eligiblePeer());
    }
}
