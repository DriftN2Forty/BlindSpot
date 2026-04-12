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
    private final double beRevealRadiusSq;
    private final double entityRevealRadiusSq;

    public ProximityService(PluginConfig config) {
        this.config = config;
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
        if (viewer == null || blockPos == null) return false;
        if (!config.isWorldEnabled(viewer.getWorld())) return false;

        Location eye = viewer.getEyeLocation();
        boolean passthrough = config.ptBlocksEnabled && !config.ptMaterials.isEmpty();
        int maxRetrace = passthrough ? config.ptMaxRetrace : 0;

        for (Vector point : blockTracePoints(blockPos)) {
            double dist = eye.toVector().distance(point);
            if (dist > config.beLosMaxRevealDistance) continue;
            if (rayReaches(viewer.getWorld(), eye, point, dist, blockPos, maxRetrace)) {
                return true;
            }
        }
        return false;
    }

    private Vector[] blockTracePoints(BlockVector bp) {
        double bx = bp.getBlockX(), by = bp.getBlockY(), bz = bp.getBlockZ();
        double cx = bx + 0.5, cy = by + 0.5, cz = bz + 0.5;
        double inset = 0.05;

        switch (config.beBlockTraceMode) {
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

        for (Vector point : entityTracePoints(e)) {
            if (rayReaches(viewer.getWorld(), eye, point, eye.toVector().distance(point),
                    null, maxRetrace)) {
                return true;
            }
        }
        return false;
    }

    private Vector[] entityTracePoints(Entity e) {
        BoundingBox bb = e.getBoundingBox();
        double cx = bb.getCenterX(), cy = bb.getCenterY(), cz = bb.getCenterZ();
        double minX = bb.getMinX(), maxX = bb.getMaxX();
        double minY = bb.getMinY(), maxY = bb.getMaxY();
        double minZ = bb.getMinZ(), maxZ = bb.getMaxZ();
        double inset = 0.1;

        switch (config.ptEntityTraceMode) {
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

            // advance past the passthrough block
            Vector hitPos = hit.getHitPosition();
            Vector step = dir.clone().multiply(0.05);
            Vector advanced = hitPos.add(step);
            start = advanced.toLocation(world);
            remaining = target.distance(advanced);
            if (remaining < 0.01) return true;
        }
        return false;
    }

    public boolean isBlockVisible(Player viewer, BlockVector blockPos) {
        Location target = new Location(viewer.getWorld(),
                blockPos.getBlockX(), blockPos.getBlockY(), blockPos.getBlockZ());
        switch (config.beMode) {
            case 1:
                return withinBeReveal(viewer, target);
            case 2:
                return hasLineOfSightToBlock(viewer, blockPos);
            case 3:
                return withinBeReveal(viewer, target) || hasLineOfSightToBlock(viewer, blockPos);
            default:
                return hasLineOfSightToBlock(viewer, blockPos);
        }
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
