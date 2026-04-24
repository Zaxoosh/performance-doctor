package dev.zaxoosh.performancedoctor;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class PerformanceDoctorCommands {
    private PerformanceDoctorCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, TickMonitor monitor, DiagnosisEngine diagnosisEngine, ReportWriter reportWriter) {
        dispatcher.register(literal("perfdoctor")
                .requires(source -> source.hasPermissionLevel(2))
                .then(literal("status")
                        .executes(context -> status(context.getSource(), monitor, diagnosisEngine)))
                .then(literal("report")
                        .executes(context -> report(context.getSource(), monitor, reportWriter)))
                .then(literal("spikes")
                        .executes(context -> spikes(context.getSource(), monitor, 5))
                        .then(argument("count", IntegerArgumentType.integer(1, 20))
                                .executes(context -> spikes(context.getSource(), monitor, IntegerArgumentType.getInteger(context, "count"))))));
    }

    private static int status(ServerCommandSource source, TickMonitor monitor, DiagnosisEngine diagnosisEngine) {
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

    private static int report(ServerCommandSource source, TickMonitor monitor, ReportWriter reportWriter) {
        try {
            ServerSnapshot snapshot = ServerSnapshot.capture(source.getServer(), monitor);
            Path report = reportWriter.write(source.getServer(), snapshot);
            send(source, "Performance Doctor report exported: " + report);
            return 1;
        } catch (IOException exception) {
            source.sendError(Text.literal("Performance Doctor could not write a report: " + exception.getMessage()));
            PerformanceDoctorMod.LOGGER.warn("Could not write Performance Doctor report", exception);
            return 0;
        }
    }

    private static int spikes(ServerCommandSource source, TickMonitor monitor, int count) {
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

    private static void send(ServerCommandSource source, String message) {
        source.sendFeedback(() -> Text.literal(message), false);
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
