# Ideas

## Player staggering / round-robin scheduling
Currently all three scheduled services (EntityVisibilityService, ItemFrameVisibilityService, BlockEntityVisibilityService) iterate every online player in a single tick with no batching or spreading. The only throttle is TpsGuard, which skips the entire tick when server TPS is low.

A round-robin approach would split players into N buckets and rotate which bucket is processed each tick, spreading CPU cost across multiple ticks instead of spiking on one. This would smooth out per-tick load on busy servers with many players.

## Trace mode distance fallback
At long range, multi-point trace modes (2/3/4) add many raycasts for minimal accuracy benefit — targets far away subtend a small angle, so the extra sample points rarely change the outcome. A configurable distance threshold could automatically fall back to mode 1 (center only) for targets beyond that range, reserving full-accuracy multi-point traces for nearby targets where corner/face visibility actually matters. This could significantly reduce raycast volume on servers with large reveal distances.

## Proximity-based tick priority
EntityVisibilityService and ItemFrameVisibilityService run on a fixed 10-tick (0.5 s) interval regardless of how close targets are to the player. This means an entity 5 blocks away updates at the same rate as one 100 blocks away, even though the nearby entity is the one the player actually notices "popping in."

A two-tier (or N-tier) tick schedule could recheck nearby entities more frequently — for example, entities within 24 blocks every 5 ticks (0.25 s) and everything else at the normal 10-tick rate. The same approach could apply to BlockEntityVisibilityService (currently 8-tick interval). This would make reveals/hides feel snappier for things the player is walking toward without increasing total work significantly, since nearby entities are a small fraction of the full scan volume. The distance threshold(s) could be configurable or derived from the existing `revealRadius`.
