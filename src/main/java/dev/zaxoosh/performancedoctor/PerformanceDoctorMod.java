package dev.zaxoosh.performancedoctor;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PerformanceDoctorMod implements ModInitializer {
    public static final String MOD_ID = "performance-doctor";
    public static final Logger LOGGER = LoggerFactory.getLogger("Performance Doctor");

    private final TickMonitor monitor = new TickMonitor();
    private final DiagnosisEngine diagnosisEngine = new DiagnosisEngine();
    private final ReportWriter reportWriter = new ReportWriter(diagnosisEngine);

    @Override
    public void onInitialize() {
        ServerTickEvents.START_SERVER_TICK.register(server -> monitor.startTick());
        ServerTickEvents.END_SERVER_TICK.register(server -> monitor.endTick());
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                PerformanceDoctorCommands.register(dispatcher, monitor, diagnosisEngine, reportWriter));

        LOGGER.info("Performance Doctor is ready for server-side diagnostics.");
    }
}
