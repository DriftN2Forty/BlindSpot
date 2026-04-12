# Changelog

## 1.0.0

### Bug Fixes
- Fixed block entity cache (`ChunkBECache`) never invalidating when blocks are placed or broken — newly placed chests/furnaces/etc. were invisible to `MovementRevealer` and never masked until a full plugin reload. Added `BlockChangeListener` to invalidate the cache for the affected chunk on `BlockPlaceEvent`/`BlockBreakEvent`.
- Fixed `MovementRevealer` task leak — the scheduler task was never stopped on plugin disable or config reload, causing duplicate tasks to accumulate.
- Removed unused `Plugin` field from `TpsGuard`, `ChunkBECache`, and `ProximityService`.
- Fixed O(N²) entity lookup in `EntityVisibilityService.stop()` — now builds a UUID map for O(1) lookups instead of linear-scanning all world entities per hidden entity.
- Fixed code fallback defaults not matching shipped config (`revealRadius` was 14/28, now 12/12; passthrough defaults now `true`).
- Fixed `entityTraceMode` being read from wrong config path (`losPassthrough` → `entities`).
- Fixed `debugVerbose` being parsed after material lists that depend on it.
- Fixed `/blindspot reload` always reporting success even when config parsing fails.
- Removed duplicate `ALLAY` entry in default `suppressTypes` list.
- Deleted stale root `config.yml` — `src/main/resources/config.yml` is the single source of truth.

### Improvements
- Added packet-based item frame hiding (`ItemFrameService`) — `Player.hideEntity()` does not reliably hide item frames because the server re-sends them via chunk entity tracking. The new service uses `DESTROY_ENTITIES` / `SPAWN_ENTITY` packets directly, with a packet interceptor to prevent the server from re-showing suppressed frames.
- Added LOS passthrough materials — raycasts can now pass through configurable block types (glass, fences, iron bars, etc.) with bounded re-tracing. Enabled by default; enable independently for blocks and/or entities via `losPassthrough.enableBlocks` / `losPassthrough.enableEntities`.
- Replaced entity LOS check from opaque `Player.hasLineOfSight()` to `World.rayTraceBlocks()` with configurable multi-point bounding-box traces (`entityTraceMode` 1–4).
- Replaced `losMode`/`losStrict`/`losAssist` booleans with a single `mode` parameter (1 = Proximity, 2 = LOS, 3 = Proximity OR LOS), configured independently for block entities and entities.
- Added `remaskDelay` parameter (default 10 s) for both block entities and entities — debounces re-masking so brief line-of-sight breaks don't flicker.
- Added `remaskWhenLeaving` to entities (default `true`) matching the existing block-entity option.
- Cached `EntityType` parsing in `PluginConfig` — eliminated per-tick string-to-enum conversion in `EntityVisibilityService`.
- Extracted `isBlockVisible()` into `ProximityService` to replace three duplicated LOS/radius check patterns across `BlockEntityMasker` and `MovementRevealer`.
- Moved `sendBlockChange()` out of `PlayerMaskState`, making it a pure state tracker with no Bukkit dependencies.
- Extracted `MapChunkPacketHandler` and `TileEntityDataPacketHandler` from `BlockEntityMasker`, reducing it to a thin registration shell.

### Architecture
- Introduced `VisibilityChecker`, `MaskStateTracker`, `TpsThrottle`, and `BlockEntityCache` interfaces for all core services.
- All downstream consumers (`BlockEntityMasker`, `MovementRevealer`, `EntityVisibilityService`) now depend on interfaces instead of concrete types.
- Migrated packet library from ProtocolLib to [PacketEvents](https://github.com/retrooper/packetevents) 2.12.0. Packet handlers now extend `PacketListenerAbstract` and use typed wrappers (`WrapperPlayServerChunkData`, `WrapperPlayServerBlockEntityData`) instead of ProtocolLib's generic `StructureModifier`.

### Testing
- Added JUnit 5 and Mockito test dependencies.
- Added unit tests for `MoreMaterials`, `PlayerMaskState`, and `TpsGuard` (20 tests).
