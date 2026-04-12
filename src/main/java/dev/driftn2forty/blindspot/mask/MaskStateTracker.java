package dev.driftn2forty.blindspot.mask;

import org.bukkit.util.BlockVector;

import java.util.UUID;

public interface MaskStateTracker {

    boolean isMaskedFor(UUID playerId, BlockVector pos);

    void setMasked(UUID playerId, BlockVector pos, boolean value);

    void clearAll();
}
