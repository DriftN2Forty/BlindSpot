package dev.driftn2forty.blindspot.proximity;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockVector;

public interface VisibilityChecker {

    boolean isBlockVisible(Player viewer, BlockVector blockPos);

    boolean isEntityVisible(Player viewer, Entity e);

    boolean withinBeReveal(Player viewer, Location target);

    boolean withinEntityReveal(Player viewer, Location target);

    boolean hasLineOfSightToBlock(Player viewer, BlockVector blockPos);

    boolean hasLineOfSightToEntity(Player viewer, Entity e);

    boolean isScanBlockVisible(Player viewer, BlockVector blockPos);
}
