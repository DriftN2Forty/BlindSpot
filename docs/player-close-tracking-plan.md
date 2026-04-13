# Player Close-Tracking Plan

## Problem

Vanilla Minecraft lets players track each other up to the server's
`entity-tracking-range.players` distance (often 48–128 blocks) regardless of
walls, terrain, or line of sight. Combat radar / entity trackers exploit this to
reveal underground players to someone on the surface, making it nearly impossible
to hide below ground.

BlindSpot already supports `requireCrouchToHide`, which forces standing players
to always be visible and only hides crouching players when LOS is broken. This
works well for close-quarters ambush gameplay but is too aggressive at long
range — a standing player 100 blocks away behind a hill is still fully
trackable.

## Goal

Combine short-range proximity awareness with long-range LOS-only tracking so
that:

* **Close-range fights feel natural** — you can sense a standing player nearby
  even through a thin wall (footsteps, sounds, etc.).
* **Long-range hiding works** — a standing player far away is invisible unless
  you have line of sight to them.
* **Crouching always requires LOS** — a sneaking player is only visible when the
  observer actually has line of sight, at any distance.

## Visibility Rules

When `requireCrouchToHide: true` and `closeTrackingRadius > 0`:

| Stance    | Distance ≤ closeTrackingRadius | Distance > closeTrackingRadius |
|-----------|-------------------------------|-------------------------------|
| Standing  | **Always visible**            | Visible only with LOS         |
| Crouching | Visible only with LOS         | Visible only with LOS         |

When `requireCrouchToHide: true` and `closeTrackingRadius: 0` (or omitted):

| Stance    | Any distance                  |
|-----------|-------------------------------|
| Standing  | **Always visible**            |
| Crouching | Visible only with LOS         |

This is the current (legacy) behaviour — `closeTrackingRadius: 0` means there
is no distance gate, so standing players are always exposed at any range.

When `requireCrouchToHide: false`:

All players follow the normal visibility mode (mode 1/2/3) regardless of stance.
`closeTrackingRadius` is ignored.

## Config Changes

Only one new parameter is needed. The existing `requireCrouchToHide` keeps its
meaning but gains a distance-aware layer when paired with the new radius.

```yaml
entities:
  # requireCrouchToHide — when true, player visibility is stance-aware:
  #   Standing players are easier to detect (always tracked within
  #   closeTrackingRadius, LOS-only beyond it).
  #   Crouching players always require line of sight to be seen.
  # When false, all players use the normal visibility mode regardless of stance.
  requireCrouchToHide: true

  # closeTrackingRadius — the short-range distance (in blocks) within which
  # a standing player is always tracked by the client (their nametag will be
  # visible through walls). Beyond this radius, standing players require LOS
  # just like crouching players.
  # Set to 0 to disable the distance gate (standing = always tracked at any
  # range, which is the legacy behaviour).
  # Only used when requireCrouchToHide is true. Ignored otherwise.
  closeTrackingRadius: 16
```

### Why this is enough

| Question an admin asks                          | Answer                              |
|-------------------------------------------------|-------------------------------------|
| "Should stance matter at all?"                  | `requireCrouchToHide`               |
| "How close before I can sense a standing player?"| `closeTrackingRadius`              |
| "What about crouching players?"                 | Always LOS, any distance            |
| "What if I want the old always-visible behaviour?"| Set `closeTrackingRadius: 0`       |

Two parameters, one concept. No extra modes or enums.

## Implementation

### Config (`PluginConfig`)

* Add `public int entityCloseTrackingRadius;` and a precomputed
  `entityCloseTrackingRadiusSq` (double).
* Read from `entities.closeTrackingRadius`, default `0`.

### Default config (`config.yml`)

* Add `closeTrackingRadius: 0` under `entities:` (preserves current default so
  existing installs are unaffected).
* Update the `requireCrouchToHide` comment block to explain the interaction.

### Visibility logic (`PlayerVisibilityService.computeVisibility`)

Current:

```java
private boolean computeVisibility(Player observer, Player target) {
    if (config.entityRequireCrouchToHide) {
        return !target.isSneaking() || proximity.isEntityVisible(observer, target);
    }
    return proximity.isEntityVisible(observer, target);
}
```

Proposed:

```java
private boolean computeVisibility(Player observer, Player target) {
    if (config.entityRequireCrouchToHide) {
        boolean standing = !target.isSneaking();

        if (standing && config.entityCloseTrackingRadiusSq > 0) {
            // Close-range: standing player is always visible within radius
            double distSq = observer.getLocation().distanceSquared(target.getLocation());
            if (distSq <= config.entityCloseTrackingRadiusSq) {
                return true;
            }
            // Beyond close range: standing player needs LOS
            return proximity.isEntityVisible(observer, target);
        }

        // closeTrackingRadius == 0 (legacy) or crouching
        return standing || proximity.isEntityVisible(observer, target);
    }
    return proximity.isEntityVisible(observer, target);
}
```

### Re-masking behaviour

The existing instant-hide for crouch mode stays. When a standing player moves
beyond `closeTrackingRadius` and loses LOS, normal `remaskDelay` debouncing
applies (same as non-crouch mode today).

### Recommended server setup

```
# server.properties or spigot.yml
entity-tracking-range.players: 128
```

```yaml
# BlindSpot config.yml
entities:
  enabled: true
  mode: 2                     # LOS-only
  requireCrouchToHide: true
  closeTrackingRadius: 16     # standing players visible within ±16 blocks
  losMaxRevealDistance: 120
```

This gives full 128-block tracking range at the server level, with BlindSpot
masking anyone beyond 16 blocks who lacks LOS. Close fights stay responsive;
underground players are invisible from the surface.

## Testing

1. **Standing, within radius** — stand 10 blocks from a wall, target on the
   other side. Target should be visible.
2. **Standing, beyond radius** — stand 30 blocks away behind terrain. Target
   should be hidden. Step into LOS → target appears.
3. **Crouching, within radius, no LOS** — crouch 5 blocks from observer behind
   a wall. Target should be hidden.
4. **Crouching, within radius, has LOS** — crouch in the open 5 blocks away.
   Target should be visible.
5. **Legacy mode** — set `closeTrackingRadius: 0`. Standing player should be
   visible at any distance regardless of LOS.
6. **Feature off** — set `requireCrouchToHide: false`. Both standing and
   crouching follow normal mode checks.
