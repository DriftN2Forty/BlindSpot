# LOS Passthrough Test Plan

## Unit Tests (automated, `BlockExitDistanceTest`)

| # | Scenario | Expected | Status |
|---|----------|----------|--------|
| 1 | Ray along +X through a block | Exits at far X face (distance = 1.5) | ✅ |
| 2 | Ray along −X through a block | Exits at near X face (distance = 1.5) | ✅ |
| 3 | Ray along +Z through a block | Exits at far Z face (distance = 1.5) | ✅ |
| 4 | Ray along −Y through a block | Exits at near Y face (distance = 2.0) | ✅ |
| 5 | Diagonal ray – exits on closest face | Picks Z face (closer than X face) | ✅ |
| 6 | Origin on entry face, heading inward | Exits on opposite face (distance = 1.0) | ✅ |
| 7 | Origin at centre of block | Exits at 0.5 | ✅ |
| 8 | Origin already beyond block | Returns 0 (clamped) | ✅ |
| 9 | Ray axis-aligned parallel to a face | Only Z contributes, exits at far Z | ✅ |
| 10 | Steep diagonal exits on Y face | Y exit t is smallest | ✅ |

---

## In-Game Manual Tests

### Setup
- Place a chest (or furnace, barrel, etc.) behind the passthrough material.
- Stand 5–10 blocks away with direct line of sight through the material.
- Confirm `losPassthrough.enableBlocks: true` and `losPassthrough.enableEntities: true`.
- Default `maxRetrace: 5`.

### A. Single-layer passthrough

| # | Material | Arrangement | Expected | Pass? |
|---|----------|-------------|----------|-------|
| A1 | Glass block | 1 glass block between player and chest | Chest visible | |
| A2 | Glass pane | 1 glass pane between player and chest | Chest visible | |
| A3 | Oak fence | 1 oak fence between player and chest | Chest visible | |
| A4 | Oak fence gate (closed) | 1 closed gate between player and chest | Chest visible | |
| A5 | Iron bars | 1 iron bar between player and chest | Chest visible | |
| A6 | Stained glass (any colour) | 1 stained glass between player and chest | Chest visible | |
| A7 | Cobweb | 1 cobweb between player and chest | Chest visible | |
| A8 | Ladder | 1 ladder on a wall between player and chest | Chest visible | |

### B. Multi-layer passthrough (consumes retraces)

| # | Layers | Material | Expected | Pass? |
|---|--------|----------|----------|-------|
| B1 | 2 | Glass blocks | Chest visible (2 retraces used) | |
| B2 | 3 | Glass panes + fences mixed | Chest visible (3 retraces) | |
| B3 | 5 | Glass blocks | Chest visible (5 retraces, maxRetrace limit) | |
| B4 | 6 | Glass blocks | Chest **hidden** (exceeds maxRetrace=5) | |

### C. Mixed opaque + passthrough

| # | Setup | Expected | Pass? |
|---|-------|----------|-------|
| C1 | Stone wall with glass window, chest behind glass | Chest visible through glass, hidden behind stone | |
| C2 | Fence on top of stone wall, chest behind fence area | Chest visible over fence, hidden behind stone below | |
| C3 | Glass block then stone block then chest | Chest **hidden** (stone is not passthrough) | |

### D. Entity passthrough (mobs/item frames)

| # | Entity | Material | Expected | Pass? |
|---|--------|----------|----------|-------|
| D1 | Villager behind glass block | Glass | Villager visible | |
| D2 | Item frame behind glass pane | Glass pane | Item frame visible | |
| D3 | Zombie behind oak fence wall | Fence | Zombie visible | |
| D4 | Creeper behind 6 glass blocks | Glass | Creeper **hidden** (exceeds maxRetrace) | |

### E. Edge cases

| # | Scenario | Expected | Pass? |
|---|----------|----------|-------|
| E1 | `maxRetrace: 0` in config | Passthrough disabled; glass blocks LOS | |
| E2 | `enableBlocks: false` | Block entities hidden behind glass; entities still visible | |
| E3 | `enableEntities: false` | Entities hidden behind glass; block entities still visible | |
| E4 | Material not in list (e.g. Oak Slab) | Blocks LOS as normal | |
| E5 | Player standing inside a glass block | Entities beyond it still visible | |
| E6 | Diagonal ray through corner of glass block | Visibility still works | |
| E7 | Very long distance (near `losMaxRevealDistance`) through 1 glass | Still visible if within reveal radius | |

### F. Performance sanity

| # | Scenario | Expected | Pass? |
|---|----------|----------|-------|
| F1 | Wall of 20 glass blocks, chest behind | Hidden (maxRetrace caps at 5), no lag | |
| F2 | 50+ entities behind glass wall, 10 players | TPS stays above 19 | |
| F3 | `maxRetrace: 10` with complex glass structures | No freeze or spike | |
