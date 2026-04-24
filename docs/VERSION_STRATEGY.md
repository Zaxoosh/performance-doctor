# Version Strategy

Research date: 2026-04-24.

The counts below are Modrinth API `project_type:mod` result counts by Minecraft version. They are not exact download totals, but they are a useful proxy for ecosystem depth and compatibility demand.

| Rank | Minecraft version | Modrinth mods | Fabric mods | Notes |
| ---: | --- | ---: | ---: | --- |
| 1 | 1.20.1 | 29,302 | 15,407 | Deepest modern modpack ecosystem. Strong revenue target. |
| 2 | 1.21.1 | 22,636 | 14,997 | Best modern baseline. Popular for newer packs and servers. |
| 3 | 1.21.4 | 14,681 | 11,212 | Good newer target, but less concentrated than 1.21.1. |
| 4 | 1.21.8 | 13,577 | 11,362 | Newer player interest, smaller pack ecosystem. |
| 5 | 1.21.5 | 12,417 | 10,498 | Similar newer-version story. |
| 6 | 1.20.4 | 12,026 | 9,190 | Useful, but less strategically important than 1.20.1. |
| 7 | 1.21.10 | 11,807 | 10,521 | Current/newer support candidate after MVP. |
| 8 | 1.19.2 | 11,488 | 7,096 | Older but still meaningful for established packs. |
| 9 | 1.20.6 | 9,363 | 7,080 | Transitional release, lower priority. |
| 10 | 1.18.2 | 8,317 | 5,109 | Long-tail support candidate. |
| 11 | 1.16.5 | 5,316 | 2,698 | Legacy demand, higher maintenance cost. |
| 12 | 1.12.2 | 2,433 | 138 | Huge historical Forge culture, but poor Fabric relevance. Treat as a separate Forge-era product if ever targeted. |

## Recommendation

Start with Fabric `1.21.1`, then backport to Fabric `1.20.1` after the MVP is stable. That gives us a modern release and the largest modpack audience without exploding maintenance.

## Loader Plan

1. Fabric 1.21.1: current baseline in this repository.
2. NeoForge 1.21.1: added for modern modded servers.
3. Forge 1.21.1: added for server owners still using MinecraftForge on current releases.
4. Fabric 1.20.1: first older-version branch because it has the largest ecosystem.
5. Forge 1.20.1: high-value next port because many 1.20.1 packs are Forge-based.
6. Older versions: only add if analytics prove demand.

## Server-Side Compatibility Notes

The current feature set avoids client UI and rendering hooks so it can remain server-only. Cross-version risk is mostly command registration, world/entity APIs, and Minecraft server method names. The project should isolate version-sensitive calls behind small adapter classes before supporting multiple Minecraft lines.
