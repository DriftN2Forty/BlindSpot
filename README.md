# BlindSpot

A Paper plugin that hides block entities and living entities from players until they have a clear line of sight or are within a configurable proximity. Built to combat X-ray texture packs and radar mods by never sending hidden data to the client in the first place.

## Features

- **Block Entity Masking** — Intercepts chunk and tile-entity packets to replace valuable blocks (chests, spawners, hoppers, etc.) with innocent-looking placeholders. Blocks are only revealed when the player can actually see them.
- **Entity Hiding** — Suppresses configurable entity types (villagers, iron golems, livestock, item frames, etc.) from nearby players until line-of-sight or proximity conditions are met. Item frames use packet-level hiding to work around Bukkit's unreliable `hideEntity()` for that entity type.
- **Visibility Modes** — Three modes per category: proximity-only radius, strict LOS raycasting, or proximity-OR-LOS. Configured independently for block entities and entities.
- **Multi-Point Raycasting** — Configurable trace modes for both blocks and entities. Check a single center point, face centers, corners, or all combined to avoid false negatives when targets are partially exposed.
- **LOS Passthrough** — Raycasts can pass through configurable block types (glass, fences, iron bars, vines, etc.) with bounded re-tracing to avoid runaway cost.
- **Re-masking with Debounce** — When a player loses line of sight, blocks and entities can be re-hidden after a configurable delay to prevent flickering.
- **TPS Guard** — Automatically skips visibility processing when server TPS drops below a configurable threshold, preventing the plugin from contributing to lag.
- **Per-World Control** — Include or exclude specific worlds from protection.
- **Bypass Permission** — Grant `blindspot.bypass` to trusted players or staff to skip all hiding.
- **Timings** — `/blindspot timings` shows per-tick min/max/avg processing time for entities and blocks, plus a combined total.
- **Hot Reload** — `/blindspot reload` applies config changes without a server restart.

## Requirements

- Paper 1.21+
- Java 21+
- [PacketEvents](https://github.com/retrooper/packetevents) 2.x

## Installation

1. Build the plugin JAR with `./gradlew shadowJar` (output in `build/libs/`).
2. Drop the JAR into your server's `plugins/` folder.
3. Ensure PacketEvents is also installed (download from [Modrinth](https://modrinth.com/plugin/packetevents)).
4. Start the server — a default `config.yml` will be generated.

## Commands

| Command | Permission | Description |
|---|---|---|
| `/blindspot reload` | `blindspot.reload` (op) | Reload configuration and restart all services |
| `/blindspot timings` | `blindspot.timings` (op) | Show per-tick timing stats for all services |

## Permissions

| Permission | Default | Description |
|---|---|---|
| `blindspot.reload` | op | Access to the reload command |
| `blindspot.timings` | op | Access to the timings command |
| `blindspot.bypass` | false | Exempt from all block and entity hiding |

## Configuration

The default `config.yml` covers all options with comments. Key sections:

| Section | Description |
|---|---|
| `blockEntities` | Enable/disable, visibility mode, reveal radius, LOS distance, trace mode, placeholder mappings, mask materials |
| `entities` | Enable/disable, visibility mode, reveal radius, LOS distance, trace mode, suppressed entity types |
| `losPassthrough` | Materials that raycasts pass through (glass, fences, etc.), max retrace depth |
| `tpsGuard` | Enable/disable, minimum TPS threshold before skipping processing |
| `worlds` | Include/exclude lists for per-world filtering |

## Timings & Performance Tuning

**Understanding the performance impact of your raycast configuration is critical.** Higher trace modes (more raycast points per target) and LOS passthrough (re-tracing through glass, fences, etc.) directly increase CPU cost per player per tick. On busy servers with many players and dense passthrough blocks, an aggressive configuration can measurably affect server TPS.

Use `/blindspot timings` to measure the real cost on your server:

```
─── BlindSpot Timings ───
Entities: min 0.045ms | max 8.061ms | avg 0.404ms
Blocks:   min 0.102ms | max 21.077ms | avg 0.485ms
Total:    min 0.147ms | max 29.138ms | avg 0.889ms
```

- **min** — best-case tick over the last 20 samples
- **max** — worst-case tick (watch for spikes here)
- **avg** — rolling average cost per tick

A full server tick budget is 50ms (20 TPS). If the **avg** total is consistently above 2–3ms, or **max** spikes are frequent, consider:

1. Lowering `blockTraceMode` or `entityTraceMode` (fewer raycasts per target)
2. Lowering `traceModeFallbackDistance` (force center-only traces sooner for distant targets)
3. Reducing `losMaxRevealDistance` (shorter raycast reach)
4. Reducing `losPassthrough.maxRetrace` or disabling passthrough entirely
5. Switching from LOS (mode 2) to proximity-only (mode 1) for one category

Run `/blindspot timings` after each change to compare the impact.

## Documentation

- [Changelog](CHANGELOG.md)

## Credits

Inspired by [AntiPieChart](https://www.spigotmc.org/resources/antipiechart.128598/) by 198651234132.
