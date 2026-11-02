package lucheart.zenithexporter.metrics;

/**
 * Adds up distance from per-tick positions. A jump bigger than anything a player can move in one tick is a
 * teleport, respawn or world change, and doesn't count as moved.
 */
public final class MovementTracker {
    static final double MAX_BLOCKS_PER_TICK = 16;

    private boolean hasPosition = false;
    private double lastX;
    private double lastY;
    private double lastZ;

    public double update(double x, double y, double z) {
        double dx = x - lastX;
        double dy = y - lastY;
        double dz = z - lastZ;
        boolean first = !hasPosition;
        hasPosition = true;
        lastX = x;
        lastY = y;
        lastZ = z;
        if (first) return 0;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return distance > MAX_BLOCKS_PER_TICK ? 0 : distance;
    }

    public void reset() {
        hasPosition = false;
    }
}
