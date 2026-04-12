# Ideas

## Player staggering / round-robin scheduling
Currently all three scheduled services (EntityVisibilityService, ItemFrameVisibilityService, BlockEntityVisibilityService) iterate every online player in a single tick with no batching or spreading. The only throttle is TpsGuard, which skips the entire tick when server TPS is low.

A round-robin approach would split players into N buckets and rotate which bucket is processed each tick, spreading CPU cost across multiple ticks instead of spiking on one. This would smooth out per-tick load on busy servers with many players.

## Trace mode distance fallback
At long range, multi-point trace modes (2/3/4) add many raycasts for minimal accuracy benefit — targets far away subtend a small angle, so the extra sample points rarely change the outcome. A configurable distance threshold could automatically fall back to mode 1 (center only) for targets beyond that range, reserving full-accuracy multi-point traces for nearby targets where corner/face visibility actually matters. This could significantly reduce raycast volume on servers with large reveal distances.
