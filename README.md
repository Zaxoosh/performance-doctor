# Performance Doctor

Performance Doctor is a server-side Minecraft mod that explains server performance in plain language. It tracks recent tick time, estimates effective TPS, checks heap pressure, counts loaded chunks/entities where the server API allows it, and exports shareable JSON reports for server owners and modpack maintainers.

## Current Target

- Loader: Fabric
- Minecraft: 1.21.1
- Java: 21
- Environment: server only

The mod is intentionally server-side first. It does not require clients to install anything.

## Commands

Requires permission level 2.

```text
/perfdoctor status
/perfdoctor report
/perfdoctor spikes [count]
```

`/perfdoctor report` writes a JSON report to `perfdoctor-reports/` in the server working directory.

## What It Diagnoses

- Average, p95, max MSPT, and effective TPS
- Slow and severe tick counts
- JVM heap usage
- Player count
- Per-dimension loaded chunk counts
- Per-dimension entity counts when available
- Plain-language recommendations for likely pressure points

## Server-Side Scope

Server-side only is good for TPS/MSPT, memory, worlds, chunks, entities, dimensions, and exportable server reports. It cannot directly measure client FPS, shaders, render distance, GPU issues, or client-only mod conflicts. Those can become an optional companion client mod later without weakening the server-side core.

## Build

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.10'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
gradle build
```

The jar will be produced under `build/libs/`.

## Revenue Strategy

Performance Doctor is designed for broad utility: server owners, modpack players, pack maintainers, and Discord support helpers all need readable performance reports. That makes it a strong fit for Modrinth discoverability and GitHub stars because it solves a repeated support problem rather than a one-time novelty.

## Roadmap

- Configurable thresholds and report privacy controls
- Markdown report export alongside JSON
- Spark/Lithium/Chunky/C2ME-aware hints where installed
- Scheduled report snapshots
- Modrinth/Discord-friendly report summary copy
- Optional client companion for FPS/render diagnostics
