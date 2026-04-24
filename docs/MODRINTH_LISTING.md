# Modrinth Listing Draft

## Short Description

Server-side performance diagnostics with readable TPS, MSPT, memory, chunk, entity, and lag spike reports.

## Summary

Performance Doctor helps server owners understand lag without digging through profiler jargon. Run a command, get clear numbers, and export a JSON report that can be shared with staff, modpack maintainers, or support communities.

## Features

- Server-side only: clients do not need the mod.
- Fabric, NeoForge, and Forge builds for Minecraft 1.21.1.
- `/perfdoctor status` for a quick health check.
- `/perfdoctor spikes` for recent slow ticks.
- `/perfdoctor report` for a shareable JSON report.
- Tracks average MSPT, p95 MSPT, max MSPT, effective TPS, slow ticks, memory pressure, players, loaded chunks, and entities.
- Gives plain-language recommendations instead of only raw numbers.

## Good For

- SMP server owners
- Modpack testing
- Discord support
- Lag reports from players
- Quick checks before installing heavier profilers

## Limitations

Performance Doctor is server-side. It cannot see client FPS, GPU load, shaders, or render-only issues unless an optional client companion is added later.
