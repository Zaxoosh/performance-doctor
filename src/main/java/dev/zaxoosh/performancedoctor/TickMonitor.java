package dev.zaxoosh.performancedoctor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

public final class TickMonitor {
    private static final int HISTORY_LIMIT = 20 * 60 * 5;
    private static final double NANOS_PER_MILLISECOND = 1_000_000.0D;

    private final Deque<TickSample> samples = new ArrayDeque<>(HISTORY_LIMIT);
    private long currentTickStartNanos;
    private long totalTicks;

    public void startTick() {
        currentTickStartNanos = System.nanoTime();
    }

    public void endTick() {
        if (currentTickStartNanos == 0L) {
            return;
        }

        long durationNanos = System.nanoTime() - currentTickStartNanos;
        currentTickStartNanos = 0L;
        totalTicks++;

        samples.addLast(new TickSample(totalTicks, durationNanos / NANOS_PER_MILLISECOND, System.currentTimeMillis()));
        while (samples.size() > HISTORY_LIMIT) {
            samples.removeFirst();
        }
    }

    public TickMetrics metrics() {
        if (samples.isEmpty()) {
            return TickMetrics.empty();
        }

        List<TickSample> copy = new ArrayList<>(samples);
        double average = copy.stream().mapToDouble(TickSample::mspt).average().orElse(0.0D);
        double max = copy.stream().mapToDouble(TickSample::mspt).max().orElse(0.0D);
        double p95 = percentile(copy, 0.95D);
        double effectiveTps = Math.min(20.0D, 1000.0D / Math.max(50.0D, average));
        long slowTicks = copy.stream().filter(sample -> sample.mspt() >= 50.0D).count();
        long severeTicks = copy.stream().filter(sample -> sample.mspt() >= 100.0D).count();

        return new TickMetrics(copy.size(), average, p95, max, effectiveTps, slowTicks, severeTicks);
    }

    public List<TickSample> slowestTicks(int limit) {
        return samples.stream()
                .sorted(Comparator.comparingDouble(TickSample::mspt).reversed())
                .limit(Math.max(1, limit))
                .toList();
    }

    private static double percentile(List<TickSample> ticks, double percentile) {
        List<Double> values = ticks.stream().map(TickSample::mspt).sorted().toList();
        int index = (int) Math.ceil(percentile * values.size()) - 1;
        return values.get(Math.max(0, Math.min(index, values.size() - 1)));
    }
}
