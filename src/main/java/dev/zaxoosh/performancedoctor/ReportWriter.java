package dev.zaxoosh.performancedoctor;

import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class ReportWriter {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
    private final DiagnosisEngine diagnosisEngine;

    public ReportWriter(DiagnosisEngine diagnosisEngine) {
        this.diagnosisEngine = diagnosisEngine;
    }

    public Path write(MinecraftServer server, ServerSnapshot snapshot) throws IOException {
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
        field(builder, 1, "serverVersion", server.getVersion(), true);
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
