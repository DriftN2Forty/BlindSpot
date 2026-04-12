package dev.driftn2forty.blindspot.mask;

import org.bukkit.Chunk;
import org.bukkit.util.BlockVector;

import java.util.List;

public interface BlockEntityCache {

    List<BlockVector> getBlockEntityPositions(Chunk chunk);

    void invalidate(Chunk chunk);

    void clear();
}
