# Performance Optimization Plan

This document captures longer-term optimization ideas for reducing BlindSpot's
server-side processing cost. The existing distance-based trace-mode fallback
and tiered tick-priority system provide a solid baseline; everything listed
here builds on top of that foundation.

Items are grouped into phases by estimated impact. Within each phase they can
be tackled independently unless noted otherwise.

---

## Phase 1 — High-Impact Architectural Changes

### 1. Consolidate `getNearbyEntities()` Calls Across Entity Services

**Problem**
`EntityVisibilityService`, `PlayerVisibilityService`, and
`ItemFrameVisibilityService` each independently call
`Player.getNearbyEntities(scan, scan, scan)` for the **same player** in the
same tick cycle.  When all three fire together (every 10 ticks), a single
player triggers **three identical Bukkit entity scans** over a ~128-block
cube, each returning the same set of entities.

Block-entity and scan-block services (`BlockEntityVisibilityService`,
`BlockVisibilityService`) are not affected — they iterate cached chunk data
rather than calling `getNearbyEntities`.

**Suggestion**
Create a shared `EntityScanCache` that calls `getNearbyEntities` **once per
player per tick cycle** and makes the result available to all three entity
services.  Each service filters the shared list for its own entity types
(general entities, players, item frames) instead of performing a separate
Bukkit scan.  The cache is cleared at the start of each tick.

**Estimated Reduction:** ~60% of entity-scan overhead (eliminates 2 of 3
redundant calls per player).  
**Complexity:** Low.  
**Files:** `EntityVisibilityService`, `PlayerVisibilityService`,
`ItemFrameVisibilityService`, new `EntityScanCache` class.

---

### 2. Raycast Result Caching (Short-TTL)

**Problem**
A stationary player looking at a stationary block re-raycasts the same
block every 2–8 ticks with identical results.

**Suggestion**
Introduce a per-player `RaycastCache` keyed on
`(playerBlockPos, yaw/pitch bucket, blockPos)` with a TTL of 4–8 ticks.
Cache hit = skip raycast entirely.  Invalidate when the player moves to a
different block position or rotates beyond a threshold (e.g. > 5°).
Particularly effective for block entities and static entities such as item
frames and armour stands.

**Estimated Reduction:** 30–70% of raycast calls for block-entity checks.  
**Complexity:** Medium — needs a compact cache key and movement-based
invalidation.  
**Files:** `ProximityService`, new `RaycastCache` class.

---

### 3. Skip Stationary Players for Block Checks

**Problem**
All online players are checked every tick even if they haven't moved or
rotated since the last cycle. On many servers 30–50% of players are
effectively stationary at any given moment.

**Suggestion**
Before running the full scan loop for a player, compare their current
`Location` (x, y, z, yaw, pitch) against a snapshot from the previous check.
If the delta is below a threshold (< 0.1 blocks and < 2° rotation) **and** no
nearby block-change events have been flagged by `BlockChangeListener`, skip
that player's **block-entity and scan-block** checks entirely — the results
are unchanged because neither the player nor the blocks have moved.

**Important caveat:** `EntityVisibilityService` and `PlayerVisibilityService`
must still run for stationary players because other entities (players,
villagers, animals, etc.) move independently.
`ItemFrameVisibilityService` **can** also be skipped for stationary players
because item frames are stationary entities — however, a
`HangingPlaceEvent` / `HangingBreakEvent` listener would be needed to flag
nearby stationary players for a re-check when item frames are placed or
removed (item frames are entities, not blocks, so `BlockPlaceEvent` /
`BlockBreakEvent` do not fire for them).

**Estimated Reduction:** 30–50% of block-visibility work plus item-frame
visibility work (does not reduce mobile-entity visibility work).  
**Complexity:** Low.  
**Files:** `BlockEntityVisibilityService`, `BlockVisibilityService`,
`ItemFrameVisibilityService`, or a shared `PlayerDeltaTracker`. New
`HangingChangeListener` for item-frame placement/removal invalidation.

---

## Phase 2 — Medium-Impact Optimizations

### 4. Chunk-Level Field-of-View Culling

**Problem**
Block visibility services iterate all block entities in a 5×5 chunk grid
regardless of whether those chunks are in front of or behind the player.

**Suggestion**
Before entering the inner block loop for a chunk, compute a dot product
between the player's look direction and the vector from the player to the
chunk centre. If the chunk is entirely behind the player (dot < −0.3), skip
every block in it. A single dot product per chunk eliminates roughly half the
chunks on average.

**Estimated Reduction:** ~40–50% of chunk iterations.  
**Complexity:** Low.  
**Files:** `BlockEntityVisibilityService`, `BlockVisibilityService` (chunk
loop).

---

### 5. Stagger Player Processing Across Ticks

**Problem**
All players are processed in the same tick, producing burst CPU usage that
can spike individual tick times well above average.

**Suggestion**
Distribute players via round-robin across the tick interval.  For example,
with 10 players and a 2-tick interval, process players 0–4 on even ticks and
5–9 on odd ticks.  This halves peak per-tick cost while maintaining the same
per-player refresh rate.

**Estimated Reduction:** Halves peak per-tick cost (does not reduce total
work, but smooths it out).  
**Complexity:** Low.  
**Files:** All visibility services (tick methods).

---

### 6. Reduce Vector / Array Allocation in the Hot Path

**Problem**
`blockTracePoints()` and `entityTracePoints()` in `ProximityService` allocate
a new `Vector[]` with 1–14 `new Vector()` objects every call. In a loop over
hundreds of blocks × dozens of players this generates thousands of short-lived
objects per tick, increasing GC pressure.

**Suggestion**
Pre-compute trace-point offsets as `static final` arrays and apply
block-position offsets in-place using a reusable scratch `Vector[]` buffer, or
inline the loop over trace points so no intermediate array is needed.

**Estimated Reduction:** Significant reduction in GC pressure; minor CPU
savings.  
**Complexity:** Medium.  
**Files:** `ProximityService` (`blockTracePoints`, `entityTracePoints`).

---

### 7. Eliminate `Location` Allocation in Distance Checks

**Problem**
`BlockEntityVisibilityService` and `BlockVisibilityService` create
`new Location(world, x, y, z)` for every block in every chunk for every
player, solely to call `distanceSquared()`.

**Suggestion**
Replace with raw coordinate arithmetic:

```java
double dx = loc.getX() - bp.getBlockX();
double dy = loc.getY() - bp.getBlockY();
double dz = loc.getZ() - bp.getBlockZ();
double distSq = dx * dx + dy * dy + dz * dz;
```

Only construct a `Location` when actually calling `sendBlockChange()`.

**Estimated Reduction:** Eliminates one object allocation per block per player
per tick.  
**Complexity:** Low.  
**Files:** `BlockEntityVisibilityService`, `BlockVisibilityService`.

---

## Phase 3 — Incremental Optimizations

### 8. Replace Stream-Based Cache Computation with Imperative Loop

`ChunkBECache.compute()` uses
`Arrays.stream(states).filter().filter().map().collect()`, which allocates a
stream pipeline per cache miss. Replace with a simple for-loop and a pre-sized
`ArrayList`.

**Complexity:** Low.  
**Files:** `ChunkBECache`.

---

### 9. Cache `Bukkit.createBlockData()` Results

Both block visibility services call `Bukkit.createBlockData(placeholder)` each
time they mask a block. Since placeholders are a fixed set of materials
(e.g. `STONE`), cache a `Map<Material, BlockData>` on config reload and reuse
it.

**Complexity:** Low.  
**Files:** `PluginConfig`, `BlockEntityVisibilityService`,
`BlockVisibilityService`.

---

### 10. Y-Distance Pre-Filter for Block Entities

Before raycasting a block entity, check
`|player.Y − block.Y| > losMaxRevealDistance` and skip if true. This is
essentially free and eliminates blocks that can never be visible (e.g. player
at Y = 64, BE at Y = 200).

**Complexity:** Low.  
**Files:** `BlockEntityVisibilityService`, `BlockVisibilityService` (inner
block loop).

---

### 11. Graduated TPS Throttling

The current `TpsGuard` is binary — either all work runs or none does. This
can cause oscillation near the threshold.

**Suggestion**

| TPS Range   | Behaviour                              |
|-------------|----------------------------------------|
| ≥ 19.5      | Full work                              |
| 18.5 – 19.5 | High-priority only (skip full ticks)  |
| 17.5 – 18.5 | Reduce scan radius by 50%             |
| < 17.5      | Skip entirely                          |

**Complexity:** Low.  
**Files:** `TpsGuard`, all visibility services.

---

### 12. Batch `DESTROY_ENTITIES` Packets

`PlayerVisibilityService` and `ItemFrameVisibilityService` send individual
`WrapperPlayServerDestroyEntities(entityId)` packets per entity per tick.
The packet natively supports multiple entity IDs.

**Suggestion**
Collect all entity IDs to destroy for a given player within a single tick,
then send one batched packet.

**Complexity:** Low.  
**Files:** `PlayerVisibilityService`, `ItemFrameVisibilityService`.

---

### 13. Async Pre-Computation of Work Lists (Advanced)

Raycasting itself must run on the main thread (world-state access), but the
**setup work** — deciding which blocks/entities to check, distance filtering,
direction filtering — could be performed on an async thread to produce a
compact work list that the main thread then processes.

**Risk:** Higher complexity, minimal gain unless setup filtering is the
bottleneck rather than raycasting.  
**Complexity:** High.  
**Files:** All visibility services.

---

## Phase 4 — Configuration / Documentation

### 14. Document Recommended Settings for High Player Counts

For servers with 20+ players, recommend:

- Mode 1 (proximity-only) or Mode 3 with a small `revealRadius`.
- Aggressive `traceModeFallbackDistance` (e.g. 24 instead of 48).
- Lower `maxRetrace` (2 instead of 5).
- Higher `highPriorityInterval` values to reduce tick frequency.

This is a documentation / README improvement, not a code change.

---

## Quick-Reference Impact Table

| #  | Optimisation                        | Est. Reduction       | Complexity |
|----|-------------------------------------|----------------------|------------|
| 1  | Consolidate getNearbyEntities       | ~60% entity scan     | Low        |
| 2  | Raycast result cache                | 30–70% raycasts      | Medium     |
| 3  | Skip stationary players (blocks)    | 30–50% block work    | Low        |
| 4  | Chunk FOV culling                   | ~40% chunk iterations| Low        |
| 5  | Stagger players across ticks        | Halves peak tick cost| Low        |
| 6  | Reduce Vector allocation            | GC pressure          | Medium     |
| 7  | Eliminate Location allocation       | Allocation reduction | Low        |
| 8  | Stream → imperative loop in cache   | Minor                | Low        |
| 9  | Cache Bukkit.createBlockData()      | Minor                | Low        |
| 10 | Y-distance pre-filter               | 5–15%                | Low        |
| 11 | Graduated TPS throttling            | Stability            | Low        |
| 12 | Batch DESTROY_ENTITIES packets      | Packet overhead      | Low        |
| 13 | Async pre-computation               | Setup cost           | High       |
| 14 | High-player-count config guide      | User-side tuning     | Low        |

**Recommended starting point:** Items 1, 3, and 5 — best bang-for-buck and
fully independent of each other.
