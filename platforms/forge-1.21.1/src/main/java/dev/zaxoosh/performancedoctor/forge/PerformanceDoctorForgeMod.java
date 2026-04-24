package dev.zaxoosh.performancedoctor.forge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

@Mod(PerformanceDoctorForgeMod.MOD_ID)
@Mod.EventBusSubscriber(modid = PerformanceDoctorForgeMod.MOD_ID)
public final class PerformanceDoctorForgeMod {
    public static final String MOD_ID = "performance_doctor";
    private static final Logger LOGGER = LoggerFactory.getLogger("Performance Doctor");
    private static final TickMonitor MONITOR = new TickMonitor();
    private static final DiagnosisEngine DIAGNOSIS_ENGINE = new DiagnosisEngine();
    private static final ReportWriter REPORT_WRITER = new ReportWriter(DIAGNOSIS_ENGINE);

    public PerformanceDoctorForgeMod() {
        LOGGER.info("Performance Doctor Forge diagnostics are ready.");
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            MONITOR.startTick();
        } else if (event.phase == TickEvent.Phase.END) {
            MONITOR.endTick();
        }
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        PerformanceDoctorCommands.register(event.getDispatcher(), MONITOR, DIAGNOSIS_ENGINE, REPORT_WRITER);
    }

    private static final class PerformanceDoctorCommands {
        private PerformanceDoctorCommands() {
        }

        static void register(CommandDispatcher<CommandSourceStack> dispatcher, TickMonitor monitor, DiagnosisEngine diagnosisEngine, ReportWriter reportWriter) {
            dispatcher.register(Commands.literal("perfdoctor")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("status")
                            .executes(context -> status(context.getSource(), monitor, diagnosisEngine)))
                    .then(Commands.literal("report")
                            .executes(context -> report(context.getSource(), monitor, reportWriter)))
                    .then(Commands.literal("spikes")
                            .executes(context -> spikes(context.getSource(), monitor, 5))
                            .then(Commands.argument("count", IntegerArgumentType.integer(1, 20))
                                    .executes(context -> spikes(context.getSource(), monitor, IntegerArgumentType.getInteger(context, "count"))))));
        }

        private static int status(CommandSourceStack source, TickMonitor monitor, DiagnosisEngine diagnosisEngine) {
            ServerSnapshot snapshot = ServerSnapshot.capture(source.getServer(), monitor);
            TickMetrics tick = snapshot.tickMetrics();
            MemoryMetrics memory = snapshot.memoryMetrics();

            send(source, "Performance Doctor");
            send(source, String.format("TPS %.2f | avg %.1f mspt | p95 %.1f mspt | max %.1f mspt", tick.effectiveTps(), tick.averageMspt(), tick.p95Mspt(), tick.maxMspt()));
            send(source, String.format("Memory %.1f%% used (%s / %s)", memory.usedPercent(), bytes(memory.usedBytes()), bytes(memory.maxBytes())));
            send(source, "Players " + snapshot.onlinePlayers() + "/" + snapshot.maxPlayers());

            for (Diagnosis diagnosis : diagnosisEngine.diagnose(snapshot)) {
                send(source, "[" + diagnosis.severity() + "] " + diagnosis.title() + ": " + diagnosis.recommendation());
            }

            return 1;
        }

        private static int report(CommandSourceStack source, TickMonitor monitor, ReportWriter reportWriter) {
            try {
                ServerSnapshot snapshot = ServerSnapshot.capture(source.getServer(), monitor);
                Path report = reportWriter.write(source.getServer(), snapshot);
                send(source, "Performance Doctor report exported: " + report);
                return 1;
            } catch (IOException exception) {
                source.sendFailure(Component.literal("Performance Doctor could not write a report: " + exception.getMessage()));
                LOGGER.warn("Could not write Performance Doctor report", exception);
                return 0;
            }
        }

        private static int spikes(CommandSourceStack source, TickMonitor monitor, int count) {
            List<TickSample> samples = monitor.slowestTicks(count);
            if (samples.isEmpty()) {
                send(source, "No tick samples recorded yet.");
                return 0;
            }

            send(source, "Slowest recent ticks:");
            for (TickSample sample : samples) {
                send(source, String.format("tick %,d: %.1f mspt", sample.tick(), sample.mspt()));
            }

            return samples.size();
        }

        private static void send(CommandSourceStack source, String message) {
            source.sendSuccess(() -> Component.literal(message), false);
        }

        private static String bytes(long bytes) {
            if (bytes < 1024L) {
                return bytes + " B";
            }

            double value = bytes;
            String[] units = {"KiB", "MiB", "GiB", "TiB"};
            int unit = -1;
            do {
                value /= 1024.0D;
                unit++;
            } while (value >= 1024.0D && unit < units.length - 1);

            return String.format("%.1f %s", value, units[unit]);
        }
    }

    private static final class TickMonitor {
        private static final int HISTORY_LIMIT = 20 * 60 * 5;
        private static final double NANOS_PER_MILLISECOND = 1_000_000.0D;

        private final Deque<TickSample> samples = new ArrayDeque<>(HISTORY_LIMIT);
        private long currentTickStartNanos;
        private long totalTicks;

        void startTick() {
            currentTickStartNanos = System.nanoTime();
        }

        void endTick() {
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

        TickMetrics metrics() {
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

        List<TickSample> slowestTicks(int limit) {
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

    private record TickSample(long tick, double mspt, long epochMillis) {
    }

    private record TickMetrics(int sampleCount, double averageMspt, double p95Mspt, double maxMspt, double effectiveTps, long slowTicks, long severeTicks) {
        static TickMetrics empty() {
            return new TickMetrics(0, 0.0D, 0.0D, 0.0D, 20.0D, 0L, 0L);
        }
    }

    private record MemoryMetrics(long usedBytes, long committedBytes, long maxBytes, double usedPercent) {
        static MemoryMetrics capture() {
            Runtime runtime = Runtime.getRuntime();
            long max = runtime.maxMemory();
            long committed = runtime.totalMemory();
            long used = committed - runtime.freeMemory();
            double percent = max <= 0L ? 0.0D : (used * 100.0D) / max;
            return new MemoryMetrics(used, committed, max, percent);
        }
    }

    private record WorldMetrics(String dimension, int loadedChunks, int entities) {
    }

    private record ServerSnapshot(TickMetrics tickMetrics, MemoryMetrics memoryMetrics, int onlinePlayers, int maxPlayers, List<WorldMetrics> worlds) {
        static ServerSnapshot capture(MinecraftServer server, TickMonitor monitor) {
            List<WorldMetrics> worlds = new ArrayList<>();
            for (ServerLevel level : server.getAllLevels()) {
                worlds.add(new WorldMetrics(
                        level.dimension().location().toString(),
                        loadedChunks(level),
                        entityCount(level)));
            }

            return new ServerSnapshot(
                    monitor.metrics(),
                    MemoryMetrics.capture(),
                    server.getPlayerCount(),
                    server.getMaxPlayers(),
                    List.copyOf(worlds));
        }

        private static int loadedChunks(ServerLevel level) {
            try {
                Object chunkSource = level.getChunkSource();
                Method method = chunkSource.getClass().getMethod("getLoadedChunksCount");
                return ((Number) method.invoke(chunkSource)).intValue();
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return -1;
            }
        }

        private static int entityCount(ServerLevel level) {
            try {
                Method method = level.getClass().getMethod("getAllEntities");
                Object result = method.invoke(level);
                if (result instanceof Iterable<?> iterable) {
                    int count = 0;
                    for (Object ignored : iterable) {
                        count++;
                    }
                    return count;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return -1;
            }

            return -1;
        }
    }

    private enum Severity {
        INFO,
        WARNING,
        CRITICAL
    }

    private record Diagnosis(Severity severity, String title, String detail, String recommendation) {
    }

    private static final class DiagnosisEngine {
        List<Diagnosis> diagnose(ServerSnapshot snapshot) {
            List<Diagnosis> diagnoses = new ArrayList<>();
            TickMetrics tick = snapshot.tickMetrics();
            MemoryMetrics memory = snapshot.memoryMetrics();

            if (tick.sampleCount() == 0) {
                diagnoses.add(new Diagnosis(Severity.INFO, "Warming up", "No tick samples have been recorded yet.", "Run the command again after the server has ticked for a few seconds."));
            } else if (tick.p95Mspt() >= 100.0D) {
                diagnoses.add(new Diagnosis(Severity.CRITICAL, "Severe tick spikes", String.format("95%% of recent ticks are at or below %.1f mspt, with a %.1f mspt max.", tick.p95Mspt(), tick.maxMspt()), "Profile heavy worlds, farms, ticking block entities, and entity collisions. Anything above 50 mspt can drop below 20 TPS."));
            } else if (tick.p95Mspt() >= 50.0D) {
                diagnoses.add(new Diagnosis(Severity.WARNING, "Borderline tick time", String.format("Recent p95 tick time is %.1f mspt.", tick.p95Mspt()), "Watch farms, worldgen, chunk loading, and active dimensions during player activity."));
            } else {
                diagnoses.add(new Diagnosis(Severity.INFO, "Tick time looks healthy", String.format("Average tick time is %.1f mspt and p95 is %.1f mspt.", tick.averageMspt(), tick.p95Mspt()), "No immediate server tick issue detected from recent samples."));
            }

            if (memory.usedPercent() >= 90.0D) {
                diagnoses.add(new Diagnosis(Severity.CRITICAL, "Heap pressure is high", String.format("The JVM is using %.1f%% of its max heap.", memory.usedPercent()), "Increase server RAM cautiously, reduce loaded dimensions/chunks, or investigate memory-heavy mods."));
            } else if (memory.usedPercent() >= 75.0D) {
                diagnoses.add(new Diagnosis(Severity.WARNING, "Heap pressure is rising", String.format("The JVM is using %.1f%% of its max heap.", memory.usedPercent()), "Check whether memory climbs over time. A stable high value is less urgent than a constant climb."));
            }

            int totalChunks = snapshot.worlds().stream().filter(world -> world.loadedChunks() > 0).mapToInt(WorldMetrics::loadedChunks).sum();
            int totalEntities = snapshot.worlds().stream().filter(world -> world.entities() > 0).mapToInt(WorldMetrics::entities).sum();

            if (totalChunks >= 10000) {
                diagnoses.add(new Diagnosis(Severity.WARNING, "Many chunks are loaded", String.format("%,d chunks are currently loaded across worlds.", totalChunks), "Check force-loaded chunks, chunk loaders, view distance, simulation distance, and idle dimensions."));
            }

            if (totalEntities >= 5000) {
                diagnoses.add(new Diagnosis(Severity.WARNING, "Many entities are loaded", String.format("%,d entities are currently loaded across worlds.", totalEntities), "Look for mob farms, dropped item piles, boats/minecarts, animal pens, and modded entities that do frequent ticking."));
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
    }

    private static final class ReportWriter {
        private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
        private final DiagnosisEngine diagnosisEngine;

        ReportWriter(DiagnosisEngine diagnosisEngine) {
            this.diagnosisEngine = diagnosisEngine;
        }

        Path write(MinecraftServer server, ServerSnapshot snapshot) throws IOException {
            Path directory = Path.of("perfdoctor-reports");
            Files.createDirectories(directory);
            Path report = directory.resolve("performance-doctor-" + FILE_TIME.format(Instant.now()) + ".json");
            Files.writeString(report, json(server, snapshot, diagnosisEngine.diagnose(snapshot)), StandardCharsets.UTF_8);
            return report.toAbsolutePath().normalize();
        }

        private static String json(MinecraftServer server, ServerSnapshot snapshot, List<Diagnosis> diagnoses) {
            StringBuilder builder = new StringBuilder();
            TickMetrics tick = snapshot.tickMetrics();
            MemoryMetrics memory = snapshot.memoryMetrics();

            builder.append("{\n");
            field(builder, 1, "generatedAt", Instant.now().toString(), true);
            field(builder, 1, "serverVersion", server.getServerVersion(), true);
            number(builder, 1, "onlinePlayers", snapshot.onlinePlayers(), true);
            number(builder, 1, "maxPlayers", snapshot.maxPlayers(), true);
            builder.append(indent(1)).append("\"tick\": {\n");
            number(builder, 2, "samples", tick.sampleCount(), true);
            number(builder, 2, "averageMspt", tick.averageMspt(), true);
            number(builder, 2, "p95Mspt", tick.p95Mspt(), true);
            number(builder, 2, "maxMspt", tick.maxMspt(), true);
            number(builder, 2, "effectiveTps", tick.effectiveTps(), true);
            number(builder, 2, "slowTicks", tick.slowTicks(), true);
            number(builder, 2, "severeTicks", tick.severeTicks(), false);
            builder.append(indent(1)).append("},\n");
            builder.append(indent(1)).append("\"memory\": {\n");
            number(builder, 2, "usedBytes", memory.usedBytes(), true);
            number(builder, 2, "committedBytes", memory.committedBytes(), true);
            number(builder, 2, "maxBytes", memory.maxBytes(), true);
            number(builder, 2, "usedPercent", memory.usedPercent(), false);
            builder.append(indent(1)).append("},\n");
            builder.append(indent(1)).append("\"worlds\": [\n");
            for (int i = 0; i < snapshot.worlds().size(); i++) {
                WorldMetrics world = snapshot.worlds().get(i);
                builder.append(indent(2)).append("{\n");
                field(builder, 3, "dimension", world.dimension(), true);
                number(builder, 3, "loadedChunks", world.loadedChunks(), true);
                number(builder, 3, "entities", world.entities(), false);
                builder.append(indent(2)).append("}");
                builder.append(i == snapshot.worlds().size() - 1 ? "\n" : ",\n");
            }
            builder.append(indent(1)).append("],\n");
            builder.append(indent(1)).append("\"diagnoses\": [\n");
            for (int i = 0; i < diagnoses.size(); i++) {
                Diagnosis diagnosis = diagnoses.get(i);
                builder.append(indent(2)).append("{\n");
                field(builder, 3, "severity", diagnosis.severity().name(), true);
                field(builder, 3, "title", diagnosis.title(), true);
                field(builder, 3, "detail", diagnosis.detail(), true);
                field(builder, 3, "recommendation", diagnosis.recommendation(), false);
                builder.append(indent(2)).append("}");
                builder.append(i == diagnoses.size() - 1 ? "\n" : ",\n");
            }
            builder.append(indent(1)).append("]\n");
            builder.append("}\n");
            return builder.toString();
        }

        private static void field(StringBuilder builder, int depth, String name, String value, boolean comma) {
            builder.append(indent(depth)).append("\"").append(escape(name)).append("\": \"").append(escape(value)).append("\"").append(comma ? "," : "").append("\n");
        }

        private static void number(StringBuilder builder, int depth, String name, Number value, boolean comma) {
            builder.append(indent(depth)).append("\"").append(escape(name)).append("\": ").append(value).append(comma ? "," : "").append("\n");
        }

        private static String escape(String value) {
            return value.replace("\\", "\\\\").replace("\"", "\\\"");
        }

        private static String indent(int depth) {
            return "  ".repeat(depth);
        }
    }
}
