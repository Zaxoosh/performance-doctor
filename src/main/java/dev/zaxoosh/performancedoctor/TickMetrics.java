package dev.zaxoosh.performancedoctor;

public record TickMetrics(
        int sampleCount,
        double averageMspt,
        double p95Mspt,
        double maxMspt,
        double effectiveTps,
        long slowTicks,
        long severeTicks
) {
    public static TickMetrics empty() {
        return new TickMetrics(0, 0.0D, 0.0D, 0.0D, 20.0D, 0L, 0L);
    }
}
