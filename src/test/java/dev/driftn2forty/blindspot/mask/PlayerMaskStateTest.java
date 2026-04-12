package dev.driftn2forty.blindspot.mask;

import org.bukkit.util.BlockVector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerMaskStateTest {

    private PlayerMaskState state;
    private final UUID player1 = UUID.randomUUID();
    private final UUID player2 = UUID.randomUUID();
    private final BlockVector pos1 = new BlockVector(10, 64, 20);
    private final BlockVector pos2 = new BlockVector(30, 70, 40);

    @BeforeEach
    void setUp() {
        state = new PlayerMaskState();
    }

    @Test
    void initiallyNotMasked() {
        assertFalse(state.isMaskedFor(player1, pos1));
    }

    @Test
    void setMaskedTrueThenQuery() {
        state.setMasked(player1, pos1, true);
        assertTrue(state.isMaskedFor(player1, pos1));
    }

    @Test
    void setMaskedFalseRemoves() {
        state.setMasked(player1, pos1, true);
        state.setMasked(player1, pos1, false);
        assertFalse(state.isMaskedFor(player1, pos1));
    }

    @Test
    void differentPlayersIndependent() {
        state.setMasked(player1, pos1, true);
        assertFalse(state.isMaskedFor(player2, pos1));
    }

    @Test
    void differentPositionsIndependent() {
        state.setMasked(player1, pos1, true);
        assertFalse(state.isMaskedFor(player1, pos2));
    }

    @Test
    void clearAllRemovesEverything() {
        state.setMasked(player1, pos1, true);
        state.setMasked(player2, pos2, true);
        state.clearAll();
        assertFalse(state.isMaskedFor(player1, pos1));
        assertFalse(state.isMaskedFor(player2, pos2));
    }

    @Test
    void multiplePositionsPerPlayer() {
        state.setMasked(player1, pos1, true);
        state.setMasked(player1, pos2, true);
        assertTrue(state.isMaskedFor(player1, pos1));
        assertTrue(state.isMaskedFor(player1, pos2));

        state.setMasked(player1, pos1, false);
        assertFalse(state.isMaskedFor(player1, pos1));
        assertTrue(state.isMaskedFor(player1, pos2));
    }

    @Test
    void equivalentBlockVectorsMatch() {
        state.setMasked(player1, new BlockVector(5, 10, 15), true);
        assertTrue(state.isMaskedFor(player1, new BlockVector(5, 10, 15)));
    }
}
