package com.pureneko.vjce.client;

/** Local animation time; never resets another mod's global animation clock. */
public final class SceneClock {
    private long previous = System.nanoTime();
    private double ticks;
    public boolean playing = true;
    public double time(double speed) {
        long now = System.nanoTime();
        if (playing) ticks += Math.min(.25, Math.max(0, (now - previous) / 1e9)) * 20 * (Double.isFinite(speed) ? Math.max(0, Math.min(20, speed)) : 1);
        previous = now; return ticks;
    }
    public void reset() { ticks = 0; previous = System.nanoTime(); }
}
