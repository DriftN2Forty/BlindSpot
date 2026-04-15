package dev.driftn2forty.blindspot.proximity;

import dev.driftn2forty.blindspot.config.PluginConfig;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockVector;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class ProximityService implements VisibilityChecker {

    private final PluginConfig config;
    private final RaycastCache raycastCache;
    private final double beRevealRadiusSq;
    private final double entityRevealRadiusSq;

    public ProximityService(PluginConfig config, RaycastCache raycastCache) {
        this.config = config;
        this.raycastCache = raycastCache;
        this.beRevealRadiusSq = (double) config.beRevealRadius * config.beRevealRadius;
        this.entityRevealRadiusSq = (double) config.entityRevealRadius * config.entityRevealRadius;
    }

    public boolean withinBeReveal(Player viewer, Location target) {
        if (viewer == null || target == null) return false;
        if (!config.isWorldEnabled(target.getWorld())) return false;
        if (!viewer.getWorld().equals(target.getWorld())) return false;
        return viewer.getLocation().distanceSquared(target) <= beRevealRadiusSq;
    }

    public boolean withinEntityReveal(Player viewer, Location target) {
        if (viewer == null || target == null) return false;
        if (!config.isWorldEnabled(target.getWorld())) return false;
        if (!viewer.getWorld().equals(target.getWorld())) return false;
        return viewer.getLocation().distanceSquared(target) <= entityRevealRadiusSq;
    }

    public boolean hasLineOfSightToBlock(Player viewer, BlockVector blockPos) {
        return hasLosToBlock(viewer, blockPos, config.beLosMaxRevealDistance,
                config.beBlockTraceMode, config.beTraceModeFallbackDistance,
                config.beTraceModeFallbackDistanceSq);
    }

    private boolean hasLosToBlock(Player viewer, BlockVector blockPos,
                                  int maxDist, int traceMode,
                                  int fallbackDist, double fallbackDistSq) {
        if (viewer == null || blockPos == null) return false;
        if (!config.isWorldEnabled(viewer.getWorld())) return false;

        Location eye = viewer.getEyeLocation();
        boolean passthrough = config.ptBlocksEnabled && !config.ptMaterials.isEmpty();
        int maxRetrace = passthrough ? config.ptMaxRetrace : 0;

        double distSqToCenter = eye.toVector().distanceSquared(
                new Vector(blockPos.getBlockX() + 0.5, blockPos.getBlockY() + 0.5, blockPos.getBlockZ() + 0.5));

        for (Vector point : blockTracePoints(blockPos, distSqToCenter, traceMode, fallbackDist, fallbackDistSq)) {
            double dist = eye.toVector().distance(point);
            if (dist > maxDist) continue;
            if (rayReaches(viewer.getWorld(), eye, point, dist, blockPos, maxRetrace)) {
                return true;
            }
        }
        return false;
    }

    private Vector[] blockTracePoints(BlockVector bp, double distSqToCenter,
                                      int traceMode, int fallbackDist, double fallbackDistSq) {
        double bx = bp.getBlockX(), by = bp.getBlockY(), bz = bp.getBlockZ();
        double cx = bx + 0.5, cy = by + 0.5, cz = bz + 0.5;
        double inset = 0.05;

        int mode = (fallbackDist > 0 && distSqToCenter > fallbackDistSq)
                ? 1 : traceMode;

        switch (mode) {
            case 2:
                return new Vector[] {
                    new Vector(cx, by + 1 - inset, cz), // top
                    new Vector(cx, by + inset, cz),      // bottom
                    new Vector(cx, cy, bz + inset),      // north
                    new Vector(cx, cy, bz + 1 - inset),  // south
                    new Vector(bx + 1 - inset, cy, cz),  // east
                    new Vector(bx + inset, cy, cz)        // west
                };
            case 3:
                return new Vector[] {
                    new Vector(bx + inset, by + 1 - inset, bz + inset),
                    new Vector(bx + 1 - inset, by + 1 - inset, bz + inset),
                    new Vector(bx + inset, by + 1 - inset, bz + 1 - inset),
                    new Vector(bx + 1 - inset, by + 1 - inset, bz + 1 - inset),
                    new Vector(bx + inset, by + inset, bz + inset),
                    new Vector(bx + 1 - inset, by + inset, bz + inset),
                    new Vector(bx + inset, by + inset, bz + 1 - inset),
                    new Vector(bx + 1 - inset, by + inset, bz + 1 - inset)
                };
            case 4:
                return new Vector[] {
                    // face centers
                    new Vector(cx, by + 1 - inset, cz), // top
                    new Vector(cx, by + inset, cz),      // bottom
                    new Vector(cx, cy, bz + inset),      // north
                    new Vector(cx, cy, bz + 1 - inset),  // south
                    new Vector(bx + 1 - inset, cy, cz),  // east
                    new Vector(bx + inset, cy, cz),       // west
                    // corners
                    new Vector(bx + inset, by + 1 - inset, bz + inset),
                    new Vector(bx + 1 - inset, by + 1 - inset, bz + inset),
                    new Vector(bx + inset, by + 1 - inset, bz + 1 - inset),
                    new Vector(bx + 1 - inset, by + 1 - inset, bz + 1 - inset),
                    new Vector(bx + inset, by + inset, bz + inset),
                    new Vector(bx + 1 - inset, by + inset, bz + inset),
                    new Vector(bx + inset, by + inset, bz + 1 - inset),
                    new Vector(bx + 1 - inset, by + inset, bz + 1 - inset)
                };
            default: // mode 1
                return new Vector[] { new Vector(cx, cy, cz) };
        }
    }

    public boolean hasLineOfSightToEntity(Player viewer, Entity e) {
        if (viewer == null || e == null) return false;
        if (!config.isWorldEnabled(viewer.getWorld())) return false;
        if (!viewer.getWorld().equals(e.getWorld())) return false;

        Location eye = viewer.getEyeLocation();
        double dist = eye.distance(e.getLocation());
        if (dist > config.entityLosMaxRevealDistance) return false;

        boolean passthrough = config.ptEntitiesEnabled && !config.ptMaterials.isEmpty();
        int maxRetrace = passthrough ? config.ptMaxRetrace : 0;

        for (Vector point : entityTracePoints(e, dist * dist)) {
            if (rayReaches(viewer.getWorld(), eye, point, eye.toVector().distance(point),
                    null, maxRetrace)) {
                return true;
            }
        }
        return false;
    }

    private Vector[] entityTracePoints(Entity e, double distSq) {
        BoundingBox bb = e.getBoundingBox();
        double cx = bb.getCenterX(), cy = bb.getCenterY(), cz = bb.getCenterZ();
        double minX = bb.getMinX(), maxX = bb.getMaxX();
        double minY = bb.getMinY(), maxY = bb.getMaxY();
        double minZ = bb.getMinZ(), maxZ = bb.getMaxZ();
        double inset = 0.1;

        int mode = (config.entityTraceModeFallbackDistance > 0
                && distSq > config.entityTraceModeFallbackDistanceSq)
                ? 1 : config.ptEntityTraceMode;

        switch (mode) {
            case 2:
                return new Vector[] {
                    new Vector(cx, maxY - inset, cz),  // top
                    new Vector(cx, minY + inset, cz),  // bottom
                    new Vector(cx, cy, minZ + inset),  // north
                    new Vector(cx, cy, maxZ - inset),  // south
                    new Vector(maxX - inset, cy, cz),  // east
                    new Vector(minX + inset, cy, cz)   // west
                };
            case 3:
                return new Vector[] {
                    new Vector(minX + inset, maxY - inset, minZ + inset),
                    new Vector(maxX - inset, maxY - inset, minZ + inset),
                    new Vector(minX + inset, maxY - inset, maxZ - inset),
                    new Vector(maxX - inset, maxY - inset, maxZ - inset),
                    new Vector(minX + inset, minY + inset, minZ + inset),
                    new Vector(maxX - inset, minY + inset, minZ + inset),
                    new Vector(minX + inset, minY + inset, maxZ - inset),
                    new Vector(maxX - inset, minY + inset, maxZ - inset)
                };
            case 4:
                return new Vector[] {
                    // face centers
                    new Vector(cx, maxY - inset, cz),  // top
                    new Vector(cx, minY + inset, cz),  // bottom
                    new Vector(cx, cy, minZ + inset),  // north
                    new Vector(cx, cy, maxZ - inset),  // south
                    new Vector(maxX - inset, cy, cz),  // east
                    new Vector(minX + inset, cy, cz),  // west
                    // corners
                    new Vector(minX + inset, maxY - inset, minZ + inset),
                    new Vector(maxX - inset, maxY - inset, minZ + inset),
                    new Vector(minX + inset, maxY - inset, maxZ - inset),
                    new Vector(maxX - inset, maxY - inset, maxZ - inset),
                    new Vector(minX + inset, minY + inset, minZ + inset),
                    new Vector(maxX - inset, minY + inset, minZ + inset),
                    new Vector(minX + inset, minY + inset, maxZ - inset),
                    new Vector(maxX - inset, minY + inset, maxZ - inset)
                };
            default: // mode 1
                return new Vector[] { new Vector(cx, cy, cz) };
        }
    }

    private boolean rayReaches(World world, Location eye, Vector target, double maxDist,
                               BlockVector expectedBlock, int retriesLeft) {
        Vector origin = eye.toVector();
        Vector dir = target.clone().subtract(origin);
        double remaining = dir.length();
        if (remaining < 0.01) return true;
        dir.normalize();

        Location start = eye.clone();

        for (int i = 0; i <= retriesLeft; i++) {
            RayTraceResult hit = world.rayTraceBlocks(start, dir, remaining,
                    FluidCollisionMode.NEVER, true);

            if (hit == null) return true;
            Block hitBlock = hit.getHitBlock();
            if (hitBlock == null) return true;

            if (expectedBlock != null
                    && hitBlock.getX() == expectedBlock.getBlockX()
                    && hitBlock.getY() == expectedBlock.getBlockY()
                    && hitBlock.getZ() == expectedBlock.getBlockZ()) {
                return true;
            }

            Material mat = hitBlock.getType();
            if (!config.ptMaterials.contains(mat)) return false;

            // advance past the far edge of the passthrough block (full 1×1×1 AABB)
            double tExit = blockExitDistance(start.toVector(), dir,
                    hitBlock.getX(), hitBlock.getY(), hitBlock.getZ());
            Vector advanced = start.toVector().add(dir.clone().multiply(tExit + 0.01));
            start = advanced.toLocation(world);
            remaining = target.distance(advanced);
            if (remaining < 0.01) return true;
        }
        return false;
    }

    /**
     * Returns the distance along the ray from {@code origin} (in direction
     * {@code dir}, which must be normalised) to the exit face of the 1×1×1
     * block whose min-corner is (bx, by, bz).
     */
    static double blockExitDistance(Vector origin, Vector dir, int bx, int by, int bz) {
        double tExit = Double.MAX_VALUE;

        double dx = dir.getX();
        if (dx > 1e-9) {
            tExit = Math.min(tExit, (bx + 1 - origin.getX()) / dx);
        } else if (dx < -1e-9) {
            tExit = Math.min(tExit, (bx - origin.getX()) / dx);
        }

        double dy = dir.getY();
        if (dy > 1e-9) {
            tExit = Math.min(tExit, (by + 1 - origin.getY()) / dy);
        } else if (dy < -1e-9) {
            tExit = Math.min(tExit, (by - origin.getY()) / dy);
        }

        double dz = dir.getZ();
        if (dz > 1e-9) {
            tExit = Math.min(tExit, (bz + 1 - origin.getZ()) / dz);
        } else if (dz < -1e-9) {
            tExit = Math.min(tExit, (bz - origin.getZ()) / dz);
        }

        return Math.max(tExit, 0.0);
    }

    public boolean isBlockVisible(Player viewer, BlockVector blockPos) {
        Location eye = viewer.getEyeLocation();
        Boolean cached = raycastCache.get(viewer.getUniqueId(),
                eye.getX(), eye.getY(), eye.getZ(),
                eye.getYaw(), eye.getPitch(), blockPos);
        if (cached != null) return cached;

        Location target = new Location(viewer.getWorld(),
                blockPos.getBlockX(), blockPos.getBlockY(), blockPos.getBlockZ());
        boolean result;
        switch (config.beMode) {
            case 1:
                result = withinBeReveal(viewer, target);
                break;
            case 2:
                result = hasLineOfSightToBlock(viewer, blockPos);
                break;
            case 3:
                result = withinBeReveal(viewer, target) || hasLineOfSightToBlock(viewer, blockPos);
                break;
            default:
                result = hasLineOfSightToBlock(viewer, blockPos);
                break;
        }

        raycastCache.put(viewer.getUniqueId(),
                eye.getX(), eye.getY(), eye.getZ(),
                eye.getYaw(), eye.getPitch(), blockPos, result);
        return result;
    }

    public boolean isScanBlockVisible(Player viewer, BlockVector blockPos) {
        Location eye = viewer.getEyeLocation();
        Boolean cached = raycastCache.get(viewer.getUniqueId(),
                eye.getX(), eye.getY(), eye.getZ(),
                eye.getYaw(), eye.getPitch(), blockPos);
        if (cached != null) return cached;

        Location target = new Location(viewer.getWorld(),
                blockPos.getBlockX(), blockPos.getBlockY(), blockPos.getBlockZ());
        double scanRevealRadiusSq = (double) config.scanRevealRadius * config.scanRevealRadius;
        boolean result;
        switch (config.scanMode) {
            case 1:
                result = withinReveal(viewer, target, scanRevealRadiusSq);
                break;
            case 2:
                result = hasLosToBlock(viewer, blockPos, config.scanLosMaxRevealDistance,
                        config.scanBlockTraceMode, config.scanTraceModeFallbackDistance,
                        config.scanTraceModeFallbackDistanceSq);
                break;
            case 3:
                result = withinReveal(viewer, target, scanRevealRadiusSq)
                        || hasLosToBlock(viewer, blockPos, config.scanLosMaxRevealDistance,
                                config.scanBlockTraceMode, config.scanTraceModeFallbackDistance,
                                config.scanTraceModeFallbackDistanceSq);
                break;
            default:
                result = hasLosToBlock(viewer, blockPos, config.scanLosMaxRevealDistance,
                        config.scanBlockTraceMode, config.scanTraceModeFallbackDistance,
                        config.scanTraceModeFallbackDistanceSq);
                break;
        }

        raycastCache.put(viewer.getUniqueId(),
                eye.getX(), eye.getY(), eye.getZ(),
                eye.getYaw(), eye.getPitch(), blockPos, result);
        return result;
    }

    private boolean withinReveal(Player viewer, Location target, double radiusSq) {
        if (viewer == null || target == null) return false;
        if (!config.isWorldEnabled(target.getWorld())) return false;
        if (!viewer.getWorld().equals(target.getWorld())) return false;
        return viewer.getLocation().distanceSquared(target) <= radiusSq;
    }

    public boolean isEntityVisible(Player viewer, Entity e) {
        switch (config.entityMode) {
            case 1:
                return withinEntityReveal(viewer, e.getLocation());
            case 2:
                return hasLineOfSightToEntity(viewer, e);
            case 3:
                return withinEntityReveal(viewer, e.getLocation()) || hasLineOfSightToEntity(viewer, e);
            default:
                return hasLineOfSightToEntity(viewer, e);
        }
    }
}
