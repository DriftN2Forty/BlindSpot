# Ideas

## Player staggering / round-robin scheduling
Currently all three scheduled services (EntityVisibilityService, ItemFrameVisibilityService, BlockEntityVisibilityService) iterate every online player in a single tick with no batching or spreading. The only throttle is TpsGuard, which skips the entire tick when server TPS is low.

A round-robin approach would split players into N buckets and rotate which bucket is processed each tick, spreading CPU cost across multiple ticks instead of spiking on one. This would smooth out per-tick load on busy servers with many players.
