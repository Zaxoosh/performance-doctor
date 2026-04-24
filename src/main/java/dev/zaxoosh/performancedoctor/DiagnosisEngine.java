package dev.zaxoosh.performancedoctor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class DiagnosisEngine {
    public List<Diagnosis> diagnose(ServerSnapshot snapshot) {
        List<Diagnosis> diagnoses = new ArrayList<>();
        TickMetrics tick = snapshot.tickMetrics();
        MemoryMetrics memory = snapshot.memoryMetrics();

        if (tick.sampleCount() == 0) {
            diagnoses.add(new Diagnosis(Severity.INFO, "Warming up", "No tick samples have been recorded yet.", "Run the command again after the server has ticked for a few seconds."));
        } else if (tick.p95Mspt() >= 100.0D) {
            diagnoses.add(new Diagnosis(Severity.CRITICAL, "Severe tick spikes", format("95%% of recent ticks are at or below %.1f mspt, with a %.1f mspt max.", tick.p95Mspt(), tick.maxMspt()), "Profile heavy worlds, farms, ticking block entities, and entity collisions. Anything above 50 mspt can drop below 20 TPS."));
        } else if (tick.p95Mspt() >= 50.0D) {
            diagnoses.add(new Diagnosis(Severity.WARNING, "Borderline tick time", format("Recent p95 tick time is %.1f mspt.", tick.p95Mspt()), "Watch farms, worldgen, chunk loading, and active dimensions during player activity."));
        } else {
            diagnoses.add(new Diagnosis(Severity.INFO, "Tick time looks healthy", format("Average tick time is %.1f mspt and p95 is %.1f mspt.", tick.averageMspt(), tick.p95Mspt()), "No immediate server tick issue detected from recent samples."));
        }

        if (memory.usedPercent() >= 90.0D) {
            diagnoses.add(new Diagnosis(Severity.CRITICAL, "Heap pressure is high", format("The JVM is using %.1f%% of its max heap.", memory.usedPercent()), "Increase server RAM cautiously, reduce loaded dimensions/chunks, or investigate memory-heavy mods."));
        } else if (memory.usedPercent() >= 75.0D) {
            diagnoses.add(new Diagnosis(Severity.WARNING, "Heap pressure is rising", format("The JVM is using %.1f%% of its max heap.", memory.usedPercent()), "Check whether memory climbs over time. A stable high value is less urgent than a constant climb."));
        }

        int totalChunks = snapshot.worlds().stream().filter(world -> world.loadedChunks() > 0).mapToInt(WorldMetrics::loadedChunks).sum();
        int totalEntities = snapshot.worlds().stream().filter(world -> world.entities() > 0).mapToInt(WorldMetrics::entities).sum();

        if (totalChunks >= 10000) {
            diagnoses.add(new Diagnosis(Severity.WARNING, "Many chunks are loaded", format("%,d chunks are currently loaded across worlds.", totalChunks), "Check force-loaded chunks, chunk loaders, view distance, simulation distance, and idle dimensions."));
        }

        if (totalEntities >= 5000) {
            diagnoses.add(new Diagnosis(Severity.WARNING, "Many entities are loaded", format("%,d entities are currently loaded across worlds.", totalEntities), "Look for mob farms, dropped item piles, boats/minecarts, animal pens, and modded entities that do frequent ticking."));
        }

        snapshot.worlds().stream()
                .filter(world -> world.loadedChunks() >= 0 || world.entities() >= 0)
                .max(Comparator.comparingInt(world -> Math.max(world.loadedChunks(), 0) + Math.max(world.entities(), 0)))
                .ifPresent(world -> diagnoses.add(new Diagnosis(Severity.INFO, "Busiest dimension", world.dimension() + " has " + display(world.loadedChunks()) + " chunks and " + display(world.entities()) + " entities.", "Use this as the first place to inspect when lag spikes happen.")));

        return List.copyOf(diagnoses);
    }

    private static String display(int value) {
        return value < 0 ? "unknown" : String.format("%,d", value);
    }

    private static String format(String template, Object... values) {
        return String.format(template, values);
    }
}
