# Ideas & Future Enhancements

## LOS Passthrough Materials

Allow certain block types (fences, glass panes, iron bars, etc.) to not block line-of-sight checks.

**Approach:** Keep the native `rayTraceBlocks()` for performance. When a ray hits a block whose material is in a configurable passthrough set, fire a second shorter raytrace from just past that block. Repeat up to a bounded depth. This avoids replacing the optimized NMS raytrace with a slower block-by-block walk — extra cost is only paid when a passthrough block is actually hit.

**Config example:**
```yaml
losPassthrough:
  - OAK_FENCE
  - SPRUCE_FENCE
  - IRON_BARS
  - GLASS_PANE
```

**Notes:**
- Applies to both block entity and entity LOS checks.
- Entity checks currently use `Player.hasLineOfSight()` (vanilla NMS) which can't be customized — would need to be replaced with the same `rayTraceBlocks()` + passthrough approach.
- Should cap the number of passthrough re-traces (e.g., max 5) to prevent runaway cost when looking through many consecutive passthrough blocks.
