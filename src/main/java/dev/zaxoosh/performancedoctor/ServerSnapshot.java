package dev.zaxoosh.performancedoctor;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public record ServerSnapshot(
        TickMetrics tickMetrics,
        MemoryMetrics memoryMetrics,
        int onlinePlayers,
        int maxPlayers,
        List<WorldMetrics> worlds
) {
    public static ServerSnapshot capture(MinecraftServer server, TickMonitor monitor) {
        List<WorldMetrics> worlds = new ArrayList<>();
        for (ServerWorld world : server.getWorlds()) {
            worlds.add(new WorldMetrics(
                    world.getRegistryKey().getValue().toString(),
                    loadedChunks(world),
                    entityCount(world)));
        }

        return new ServerSnapshot(
                monitor.metrics(),
                MemoryMetrics.capture(),
                server.getCurrentPlayerCount(),
                server.getMaxPlayerCount(),
                List.copyOf(worlds));
    }

    private static int loadedChunks(ServerWorld world) {
        try {
            Object chunkManager = world.getChunkManager();
            Method method = chunkManager.getClass().getMethod("getLoadedChunkCount");
            return ((Number) method.invoke(chunkManager)).intValue();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return -1;
        }
    }

    private static int entityCount(ServerWorld world) {
        try {
            Method method = world.getClass().getMethod("iterateEntities");
            Object result = method.invoke(world);
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
