# Block Entity Reference

BlindSpot masks blocks by intercepting `Chunk.getTileEntities()`, which only returns **block entities** (tile entities). This document lists interactive/special blocks and whether they can be masked.

## Maskable Blocks (Block Entities)

These blocks store persistent data via a tile entity and are returned by `Chunk.getTileEntities()`. They can be added to `blockEntities.maskMaterials` and `blockEntities.placeholders` in `config.yml`.

| Block | Notes |
|---|---|
| `CHEST` | Double-chest pairing stored in tile entity |
| `TRAPPED_CHEST` | Same as chest, emits redstone |
| `BARREL` | 27-slot container |
| `ENDER_CHEST` | Per-player inventory, tile entity for particle effects |
| `HOPPER` | Stores items + transfer cooldown |
| `FURNACE` | Stores items, smelt progress, recipes |
| `BLAST_FURNACE` | Faster furnace variant |
| `SMOKER` | Food-only furnace variant |
| `BREWING_STAND` | Stores potions + brewing progress |
| `LECTERN` | Stores book contents + current page |
| `SPAWNER` | Stores mob type, spawn rules, delays |
| `JUKEBOX` | Stores inserted disc |
| `CAMPFIRE` | Stores up to 4 cooking items |
| `SOUL_CAMPFIRE` | Same as campfire |
| `BEE_NEST` | Stores bee data + honey level |
| `BEEHIVE` | Same as bee nest (crafted variant) |
| `ENCHANTING_TABLE` | Tile entity for book animation |
| `BELL` | Tile entity for ringing animation |
| `CHISELED_BOOKSHELF` | Stores up to 6 books (1.20+) |
| `SHULKER_BOX` | All 16 colors + undyed variant |
| `SIGN` / `HANGING_SIGN` | All wood variants, stores text |
| `BANNER` | All 16 colors, stores pattern layers |
| `SKULL` / `PLAYER_HEAD` | Stores skin data |
| `DECORATED_POT` | Stores sherds + item (1.20+) |
| `DISPENSER` | 9-slot container |
| `DROPPER` | 9-slot container |
| `CRAFTER` | Stores items + disabled slots (1.21+) |
| `TRIAL_SPAWNER` | Stores mob type + trial state (1.21+) |
| `VAULT` | Stores loot table + unlock state (1.21+) |
| `CREAKING_HEART` | Stores creaking state (1.21.4+) |

## Non-Maskable Blocks (NOT Block Entities)

These blocks have GUIs, right-click behavior, or other special interactions but are **not** block entities. They will never appear in `Chunk.getTileEntities()` and **cannot** be masked by BlindSpot.

### Blocks with Crafting / Processing GUIs

| Block | Why Not a Block Entity |
|---|---|
| `CRAFTING_TABLE` | Stateless — no persistent data, inventory is player-side |
| `CARTOGRAPHY_TABLE` | Stateless — UI is player-side |
| `SMITHING_TABLE` | Stateless — UI is player-side |
| `STONECUTTER` | Stateless — UI is player-side |
| `GRINDSTONE` | Stateless — UI is player-side |
| `LOOM` | Stateless — UI is player-side |
| `ANVIL` | Stateless — damage level is a separate block ID (`CHIPPED_ANVIL`, `DAMAGED_ANVIL`) |

### Blocks with Other Interactions

| Block | Why Not a Block Entity |
|---|---|
| `COMPOSTER` | Fill level stored as block state (0–8), no persistent inventory |
| `BOOKSHELF` | No interaction — only provides enchanting power. (`CHISELED_BOOKSHELF` *is* a block entity) |
| `NOTE_BLOCK` | Pitch/instrument stored as block state |
| `FLETCHING_TABLE` | No function implemented (as of 1.21) |
| `CAKE` | Bites remaining stored as block state |
| `RESPAWN_ANCHOR` | Charge level stored as block state (0–4) |
| `FLOWER_POT` | Each flower type is a separate block ID (`POTTED_OAK_SAPLING`, etc.) |

## Impact on Default Config

Non-block-entity blocks (crafting tables, grindstones, anvils, looms, composters,
etc.) are now handled by the `scanBlocks` system, which uses NMS palette scanning
to locate them in chunks. See the `scanBlocks` section in `config.yml`.
