package dev.zaxoosh.performancedoctor;

public record MemoryMetrics(long usedBytes, long committedBytes, long maxBytes, double usedPercent) {
    public static MemoryMetrics capture() {
        Runtime runtime = Runtime.getRuntime();
        long max = runtime.maxMemory();
        long committed = runtime.totalMemory();
        long used = committed - runtime.freeMemory();
        double percent = max <= 0L ? 0.0D : (used * 100.0D) / max;
        return new MemoryMetrics(used, committed, max, percent);
    }
}
