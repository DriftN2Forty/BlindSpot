# BlindSpot

A Paper plugin that hides block entities and living entities from players until they have a clear line of sight or are within a configurable proximity. Built to combat X-ray texture packs and radar mods by never sending hidden data to the client in the first place.

## Features

- **Block Entity Masking** — Intercepts chunk and tile-entity packets to replace valuable blocks (chests, spawners, hoppers, etc.) with innocent-looking placeholders. Blocks are only revealed when the player can actually see them.
- **Entity Hiding** — Suppresses configurable entity types (villagers, iron golems, livestock, etc.) from nearby players until line-of-sight or proximity conditions are met.
- **Line-of-Sight Modes** — Supports both simple radius checks and strict LOS raycasting with a configurable max distance.
- **TPS Guard** — Automatically throttles visibility processing when server TPS drops below a threshold, preventing the plugin from contributing to lag.
- **Per-World Control** — Include or exclude specific worlds from protection.
- **Bypass Permission** — Grant `blindspot.bypass` to trusted players or staff to skip all hiding.
- **Hot Reload** — `/blindspot reload` applies config changes without a server restart.

## Requirements

- Paper 1.21+
- Java 21+
- [PacketEvents](https://github.com/retrooper/packetevents) 2.x (soft dependency)

## Installation

1. Build the plugin JAR with `./gradlew build` (output in `build/libs/`).
2. Drop the JAR into your server's `plugins/` folder.
3. Ensure PacketEvents is also installed (download from [Modrinth](https://modrinth.com/plugin/packetevents)).
4. Start the server — a default `config.yml` will be generated.

## Commands

| Command | Permission | Description |
|---|---|---|
| `/blindspot reload` | `blindspot.reload` (op) | Reload configuration and restart all services |

## Permissions

| Permission | Default | Description |
|---|---|---|
| `blindspot.reload` | op | Access to the reload command |
| `blindspot.bypass` | false | Exempt from all block and entity hiding |

## Documentation

- [Changelog](CHANGELOG.md)

## Credits

Inspired by [AntiPieChart](https://www.spigotmc.org/resources/antipiechart.128598/) by 198651234132.
