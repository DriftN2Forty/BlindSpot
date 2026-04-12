package dev.driftn2forty.blindspot.mask;

import org.bukkit.util.BlockVector;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerMaskState implements MaskStateTracker {

    private final Map<UUID, Set<BlockVector>> masked = new ConcurrentHashMap<>();

    public void clearAll() {
        masked.clear();
    }

    public boolean isMaskedFor(UUID playerId, BlockVector pos) {
        return masked.getOrDefault(playerId, Collections.emptySet()).contains(pos);
    }

    public void setMasked(UUID playerId, BlockVector pos, boolean value) {
        masked.compute(playerId, (k, set) -> {
            if (set == null) set = ConcurrentHashMap.newKeySet();
            if (value) {
                set.add(pos);
            } else {
                set.remove(pos);
            }
            return set;
        });
    }

}
