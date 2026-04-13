# Changelog

## 1.0.0

### Features
- Added `scanBlocks` system for masking non-block-entity blocks (crafting tables, grindstones, anvils, looms, etc.) that can't be discovered via `Chunk.getTileEntities()`. Uses NMS reflection to access chunk section palettes for O(1) filtering — only sections whose palette contains a target material are fully scanned. Requires Paper 1.20.5+ with Mojang mappings; logs a warning and disables gracefully on unsupported servers.
- Removed `COMPOSTER`, `CARTOGRAPHY_TABLE`, `SMITHING_TABLE`, and `STONECUTTER` from default `blockEntities.maskMaterials` — these are not actual block entities and were silently never masked. They are now in the new `scanBlocks.materials` list instead.
- Added all leaf block types (oak, spruce, birch, jungle, acacia, dark oak, mangrove, cherry, pale oak, azalea, flowering azalea) to the default LOS passthrough materials list.
- Added all boat and chest boat types (including bamboo rafts and pale oak) to the default `suppressTypes` entity list.
- Added `requireCrouchToHide` for entities (default `false`) — when enabled, PLAYER entities are only hidden while crouching/sneaking. Non-crouching players remain visible regardless of LOS or proximity. Hiding is instant with no remask delay in this mode.
- Added `traceModeFallbackDistance` for block entities and entities (default 48 blocks) — beyond this distance the trace mode is forced to 1 (center only), reducing raycast count for distant targets. Set to 0 to disable.
- Added proximity-based tick priority — entities and block entities within a configurable `highPriorityRadius` (default 24 blocks) are now rechecked at a faster tick rate (`highPriorityInterval`), while distant targets keep the normal rate. Configured separately for block entities (default 4 ticks, base 8) and entities/item frames (default 5 ticks, base 10). The fast interval is clamped so it can never exceed the normal rate.

### Bug Fixes
- Fixed block entities and entities not being hidden on login — initial masking was gated by the remask delay and `remaskWhenLeaving` flag, causing blocks/entities to appear visible until the player viewed them and moved away. Block entities and entities that have never been revealed are now masked immediately on first encounter. Also removed the TPS guard from initial chunk masking (`MapChunkPacketHandler`) so it cannot be skipped during login.
- Fixed LOS passthrough not working for fence gates, glass, and other passthrough blocks — the ray only advanced 0.05 units past the hit surface, which was still inside the block, causing the same block to be hit repeatedly until retries were exhausted. The ray now calculates the exit face of the full 1×1×1 block AABB and resumes from just past it.
- Fixed `/blindspot reload` causing all hidden entities and block entities to become visible — the reload path cleared mask state and called `stop()` (which explicitly un-hides everything), then restarted with a 2-second delay. Services now use a `restart()` method that preserves hidden/suppressed state across config reloads.
- Fixed block entity cache (`ChunkBECache`) never invalidating when blocks are placed or broken — newly placed chests/furnaces/etc. were invisible to `BlockEntityVisibilityService` and never masked until a full plugin reload. Added `BlockChangeListener` to invalidate the cache for the affected chunk on `BlockPlaceEvent`/`BlockBreakEvent`.
- Fixed `BlockEntityVisibilityService` task leak — the scheduler task was never stopped on plugin disable or config reload, causing duplicate tasks to accumulate.
- Removed unused `Plugin` field from `TpsGuard`, `ChunkBECache`, and `ProximityService`.
- Fixed O(N²) entity lookup in `EntityVisibilityService.stop()` — now builds a UUID map for O(1) lookups instead of linear-scanning all world entities per hidden entity.
- Fixed code fallback defaults not matching shipped config (`revealRadius` was 14/28, now 12/12; passthrough defaults now `true`).
- Fixed `entityTraceMode` being read from wrong config path (`losPassthrough` → `entities`).
- Fixed `debugVerbose` being parsed after material lists that depend on it.
- Fixed `/blindspot reload` always reporting success even when config parsing fails.
- Removed duplicate `ALLAY` entry in default `suppressTypes` list.
- Deleted stale root `config.yml` — `src/main/resources/config.yml` is the single source of truth.

### Improvements
- Unified `blockTraceMode` and `entityTraceMode` to use the same 4-mode scheme: 1 = center, 2 = 6 face centers, 3 = 8 corners, 4 = 6 face centers + 8 corners (14 raycasts). Entity traces now properly sample all 6 faces and all 8 corners of the 3D bounding box instead of only 4 diagonal corners. `blockTraceMode` gains a new mode 3 (corners only, 8 raycasts) with the old mode 3 (faces + corners) moved to mode 4.
- Added `blockTraceMode` for block entity LOS checks (modes 1–4) — raycasts can now target multiple face centers instead of only the block center. Default mode 2 (6 face centers) fixes blocks being hidden when partially exposed (e.g. a chest with a solid block on top but a visible front face).
- Added packet-based item frame hiding (`ItemFrameVisibilityService`) — `Player.hideEntity()` does not reliably hide item frames because the server re-sends them via chunk entity tracking. The new service uses `DESTROY_ENTITIES` / `SPAWN_ENTITY` packets directly, with a packet interceptor to prevent the server from re-showing suppressed frames.
- Added LOS passthrough materials — raycasts can now pass through configurable block types (glass, fences, iron bars, etc.) with bounded re-tracing. Enabled by default; enable independently for blocks and/or entities via `losPassthrough.enableBlocks` / `losPassthrough.enableEntities`.
- Replaced entity LOS check from opaque `Player.hasLineOfSight()` to `World.rayTraceBlocks()` with configurable multi-point bounding-box traces (`entityTraceMode` 1–4).
- Replaced `losMode`/`losStrict`/`losAssist` booleans with a single `mode` parameter (1 = Proximity, 2 = LOS, 3 = Proximity OR LOS), configured independently for block entities and entities.
- Added `remaskDelay` parameter (default 10 s) for both block entities and entities — debounces re-masking so brief line-of-sight breaks don't flicker.
- Added `remaskWhenLeaving` to entities (default `true`) matching the existing block-entity option.
- Cached `EntityType` parsing in `PluginConfig` — eliminated per-tick string-to-enum conversion in `EntityVisibilityService`.
- Extracted `isBlockVisible()` into `ProximityService` to replace three duplicated LOS/radius check patterns across `BlockEntityMasker` and `BlockEntityVisibilityService`.
- Moved `sendBlockChange()` out of `PlayerMaskState`, making it a pure state tracker with no Bukkit dependencies.
- Extracted `MapChunkPacketHandler` and `TileEntityDataPacketHandler` from `BlockEntityMasker`, reducing it to a thin registration shell.

### Architecture
- Introduced `VisibilityChecker`, `MaskStateTracker`, `TpsThrottle`, and `BlockEntityCache` interfaces for all core services.
- All downstream consumers (`BlockEntityMasker`, `BlockEntityVisibilityService`, `EntityVisibilityService`) now depend on interfaces instead of concrete types.
- Migrated packet library from ProtocolLib to [PacketEvents](https://github.com/retrooper/packetevents) 2.12.0. Packet handlers now extend `PacketListenerAbstract` and use typed wrappers (`WrapperPlayServerChunkData`, `WrapperPlayServerBlockEntityData`) instead of ProtocolLib's generic `StructureModifier`.

### Testing
- Added JUnit 5 and Mockito test dependencies.
- Added unit tests for `MoreMaterials`, `PlayerMaskState`, and `TpsGuard` (20 tests).
- Added unit tests for `blockExitDistance` AABB ray-exit calculation (10 tests).
