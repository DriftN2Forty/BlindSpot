---
description: "Implement proximity-based tick priority so nearby entities/block-entities recheck more frequently than distant ones"
agent: "agent"
---

# Proximity-based tick priority

## Goal

Add a **high-priority tick tier** to `EntityVisibilityService`, `ItemFrameVisibilityService`, and `BlockEntityVisibilityService`. Entities/block-entities within a configurable radius recheck at a faster tick rate; everything else keeps the current default rate. This makes reveals/hides feel snappier for targets the player is walking toward without increasing total work, since nearby targets are a small fraction of the full scan volume.

## New config parameters

Add these under new `tickPriority` sections for each category:

```yaml
blockEntities:
  tickPriority:
    highPriorityRadius: 16       # blocks; entities within this distance use the fast rate
    highPriorityInterval: 2      # ticks; fast-rate interval (must be <= normal 8-tick interval)

entities:
  tickPriority:
    highPriorityRadius: 16       # blocks; entities within this distance use the fast rate
    highPriorityInterval: 2      # ticks; fast-rate interval (must be <= normal 10-tick interval)
```

### Config loading rules (`PluginConfig`)

- `highPriorityRadius` — integer, default `24`, minimum `1`. This applies globally to all entity types (players, mobs, item frames, block entities) within that category.
- `highPriorityInterval` — integer, default `5` for entities / `4` for block entities. **Clamped** so it can never exceed the normal interval for that service (10 ticks for entity/item-frame services, 8 ticks for block-entity service). Use `Math.min(value, normalInterval)` at load time so an invalid config silently corrects rather than breaking.
- Add corresponding fields to `PluginConfig`: `beHighPriorityRadius`, `beHighPriorityInterval`, `entityHighPriorityRadius`, `entityHighPriorityInterval`.
- Pre-compute squared radius values (`beHighPriorityRadiusSq`, `entityHighPriorityRadiusSq`) in `PluginConfig.reload()` for use in distance checks (same pattern as existing `revealRadius`).

## Implementation design

### Scheduling approach — dual-rate single timer

Do **not** run two separate `runTaskTimer` schedulers. Instead, keep the existing single timer at the **high-priority interval** and use a tick counter to decide when to also process the normal-tier entities:

```
// Pseudocode for EntityVisibilityService
private int tickCounter = 0;
private static final int NORMAL_INTERVAL = 10;  // existing

void tick() {
    tickCounter++;
    boolean fullTick = (tickCounter % (NORMAL_INTERVAL / config.entityHighPriorityInterval) == 0);

    for (Player p : onlinePlayers) {
        // ... existing world/permission checks ...
        for (Entity e : nearbyEntities) {
            double distSq = p.getLocation().distanceSquared(e.getLocation());
            boolean highPriority = distSq <= config.entityHighPriorityRadiusSq;

            if (highPriority || fullTick) {
                // evaluate visibility and update hide/show state
            }
        }
    }
}
```

Key points:

1. **Timer interval changes** — The `runTaskTimer` repeat period becomes `config.entityHighPriorityInterval` (e.g., 5 ticks) instead of the current hardcoded `10L`. Same logic for `ItemFrameVisibilityService` and `BlockEntityVisibilityService` (period becomes `config.beHighPriorityInterval` instead of `8L`).

2. **Full-tick cadence** — Every `ceil(NORMAL_INTERVAL / highPriorityInterval)` invocations is a "full tick" that processes all entities regardless of distance. Entities within the high-priority radius are processed on **every** invocation. Use `ceil` (round up) specifically — this guarantees the normal tier never fires *faster* than the original base rate. When the interval isn't a clean divisor (e.g., `ceil(10/3) = 4` → full tick every 12 game ticks instead of 10), distant entities update slightly slower than the base rate. This is the correct tradeoff: predictable cadence, no jitter, and the slight slowdown is imperceptible for far-away targets. Only clean divisors (5 into 10, 4 into 8) give exact parity with the base rate.

3. **Distance check** — Use the pre-computed squared radius for the distance comparison. The distance is already available in the scan loop (entity distance from player). No new spatial data structures needed.

4. **TpsGuard interaction** — The existing `TpsGuard` skip-tick logic should remain unchanged. When TPS is low, the entire tick is skipped regardless of priority tier. This is the existing behavior and should not change.

### Service-specific notes

- **EntityVisibilityService**: Normal interval = 10 ticks. Timer runs at `entityHighPriorityInterval`. Full tick every `ceil(10 / entityHighPriorityInterval)` invocations.
- **ItemFrameVisibilityService**: Same intervals and logic as `EntityVisibilityService` — they share the 10-tick base rate.
- **BlockEntityVisibilityService**: Normal interval = 8 ticks. Timer runs at `beHighPriorityInterval`. Full tick every `ceil(8 / beHighPriorityInterval)` invocations.

### Files to modify

1. [src/main/resources/config.yml](src/main/resources/config.yml) — Add `tickPriority` sub-sections under `blockEntities` and `entities`.
2. [src/main/java/dev/driftn2forty/blindspot/config/PluginConfig.java](src/main/java/dev/driftn2forty/blindspot/config/PluginConfig.java) — New fields + loading logic with clamping.
3. [src/main/java/dev/driftn2forty/blindspot/entity/EntityVisibilityService.java](src/main/java/dev/driftn2forty/blindspot/entity/EntityVisibilityService.java) — Dual-rate tick logic.
4. [src/main/java/dev/driftn2forty/blindspot/entity/ItemFrameVisibilityService.java](src/main/java/dev/driftn2forty/blindspot/entity/ItemFrameVisibilityService.java) — Same dual-rate tick logic.
5. [src/main/java/dev/driftn2forty/blindspot/proximity/BlockEntityVisibilityService.java](src/main/java/dev/driftn2forty/blindspot/proximity/BlockEntityVisibilityService.java) — Dual-rate tick logic.
6. [CHANGELOG.md](CHANGELOG.md) — Document the new feature.
7. [README.md](README.md) — Update config documentation if applicable.

### What NOT to change

- `ProximityService` — No changes needed. The distance check for tick priority is separate from the visibility/LOS evaluation; it just gates whether we run the existing visibility check this tick.
- Debounce/remask timers — These are time-based (milliseconds), not tick-based, so they remain unaffected.
- Scan radius / `getNearbyEntities` range — The scan volume stays the same. We still scan the full range every high-priority tick; we just skip the visibility evaluation for distant entities on non-full ticks.

## Testing considerations

- Verify that when `highPriorityInterval` equals the normal interval (e.g., 10), behavior is identical to current — every tick is a full tick.
- Verify that setting `highPriorityInterval` higher than the normal interval gets clamped down.
- Verify `highPriorityRadius: 0` or `1` doesn't cause errors.
- Ensure TpsGuard still correctly skips all processing when TPS is low.
