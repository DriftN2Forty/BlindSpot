# Visibility Modes

BlindSpot uses a single `mode` parameter (inspired by Paper's anti-xray engine)
to control how each category — block entities and entities — decides whether a
player can see a hidden target. Both categories share the same three modes.

---

## The Three Modes

### Mode 1 — Proximity

The simplest mode. If the player is within `revealRadius` blocks of the target,
it's visible. Walls, floors, and obstacles don't matter — only distance.

**Good for:** Servers that want lightweight anti-xray without the CPU cost of
raycasting. Works well for block entities in dense builds where most things are
nearby anyway.

**Drawback:** A player standing on the other side of a wall, within radius, will
still see the hidden object. This is usually fine in practice — the radius is
small enough that the player is already in the room or very close to it.

| Setting used | Ignored |
|---|---|
| `revealRadius` | `losMaxRevealDistance` |

### Mode 2 — Line of Sight (LOS)

The strictest mode. The server traces a ray from the player's eyes to the target.
If the ray reaches the target without hitting a solid block, it's visible. If
anything is in the way, it stays hidden — even if the target is 3 blocks away
behind a wall.

**Good for:** Maximum protection against xray and entity radar. Feels natural
because it mirrors what the player would actually see.

**Drawback:** Slightly more CPU-intensive (raycasting). Entities behind thin walls
or corners may flicker briefly as the player moves around them.

| Setting used | Ignored |
|---|---|
| `losMaxRevealDistance` | `revealRadius` |

### Mode 3 — Proximity OR LOS

The target is visible if **either** condition is true: the player is within
`revealRadius` **or** the player has line of sight within `losMaxRevealDistance`.

**Good for:** A balance between protection and smoothness. Players walking toward
a village see entities appear naturally as they round corners, and anything
already within the small proximity radius is revealed immediately without
waiting for a LOS check.

**Drawback:** Slightly more permissive than pure LOS — anything within the
proximity radius is visible regardless of walls.

| Setting used | Ignored |
|---|---|
| `revealRadius`, `losMaxRevealDistance` | *(none)* |

---

## Configuration

Both block entities and entities use the same `mode` key. Set it independently
for each category.

```yaml
blockEntities:
  mode: 2                    # 1 = Proximity, 2 = LOS, 3 = Proximity OR LOS
  revealRadius: 7            # used by modes 1 and 3
  remaskWhenLeaving: true    # re-mask when leaving reveal radius or LOS
  remaskDelay: 10            # seconds to wait before re-masking (debounce)
  losMaxRevealDistance: 120   # used by modes 2 and 3

entities:
  mode: 2                    # 1 = Proximity, 2 = LOS, 3 = Proximity OR LOS
  revealRadius: 12           # used by modes 1 and 3
  remaskWhenLeaving: true    # re-hide when leaving reveal radius or LOS
  remaskDelay: 10            # seconds to wait before re-hiding (debounce)
  losMaxRevealDistance: 120   # used by modes 2 and 3
```

### Re-masking Behaviour

Both categories support two settings that control what happens when a player
moves away from a previously revealed target:

| Setting | Default | Description |
|---|---|---|
| `remaskWhenLeaving` | `true` | Whether to re-mask/re-hide the target when the player can no longer see it. |
| `remaskDelay` | `10` | Seconds to wait after losing visibility before re-masking. Prevents flickering when a player briefly loses and regains line of sight (e.g. walking past a pillar). |

When `remaskWhenLeaving` is `false`, `remaskDelay` is irrelevant — revealed
targets stay visible until the chunk unloads (blocks) or the entity leaves
tracking range.

---

## Recommended Presets

### "Maximum Protection" (default)

Best for survival/SMP servers where xray and radar are a real concern.

```yaml
blockEntities:
  mode: 2
  losMaxRevealDistance: 120

entities:
  mode: 2
  losMaxRevealDistance: 120
```

Everything requires direct line of sight. Players must physically see a chest or
mob before it exists on their client. Xray packs and radar mods see nothing.

### "Balanced"

Good for servers that want protection without occasional corner-peeking flicker.

```yaml
blockEntities:
  mode: 2
  losMaxRevealDistance: 120

entities:
  mode: 3
  revealRadius: 12
  losMaxRevealDistance: 120
```

Blocks use strict LOS (most important for anti-xray). Entities use mode 3 —
mobs within 12 blocks appear immediately, and anything farther away still
appears if the player has a clear sightline.

### "Lightweight"

Minimal CPU overhead, still blocks casual xray.

```yaml
blockEntities:
  mode: 1
  revealRadius: 16

entities:
  mode: 1
  revealRadius: 16
```

Pure distance checks, no raycasting. Blocks and entities appear once the player
is close enough regardless of walls. Cheap to run, still prevents long-range
xray scanning.

---

## Common Questions

**Q: Can a player 2 blocks behind a wall see the target in mode 2?**
No. Mode 2 requires an unobstructed ray. The wall blocks the ray, so the target
stays hidden — even at point-blank range.

**Q: What happens when a player walks around a corner?**
In modes 2 and 3, the target appears the moment the raycast clears the corner.
In practice this feels instant because visibility is rechecked every 8 ticks
(blocks) or 10 ticks (entities) — roughly 2–3 times per second.

**Q: Does `losMaxRevealDistance` affect mode 1?**
No. It only caps the raycast distance in modes 2 and 3. In mode 1 only
`revealRadius` matters.

**Q: What does `remaskWhenLeaving` do?**
When enabled (default), targets that were previously revealed get re-hidden if
the player moves away and can no longer see them. This applies to both block
entities and entities. When disabled, once a target is revealed it stays visible
until the chunk unloads (blocks) or the entity leaves tracking range.

**Q: What does `remaskDelay` do?**
It's a debounce timer. After a player loses visibility of a target, BlindSpot
waits this many seconds before re-masking it. If the player regains visibility
within that window (e.g. walking past a pillar), the timer resets and the target
never disappears. Set to `0` for instant re-masking, or increase it to reduce
flickering in complex terrain.

**Q: In mode 3, does the proximity check reveal through walls?**
Yes — that's the point. If the player is within `revealRadius`, the target is
visible regardless of obstacles. The LOS part extends visibility *beyond* that
radius for targets the player can actually see.
