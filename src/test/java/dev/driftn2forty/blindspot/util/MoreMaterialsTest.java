package dev.driftn2forty.blindspot.util;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MoreMaterialsTest {

    @Test
    void matchValidUpperCase() {
        assertEquals(Material.STONE, MoreMaterials.match("STONE"));
    }

    @Test
    void matchValidLowerCase() {
        assertEquals(Material.CHEST, MoreMaterials.match("chest"));
    }

    @Test
    void matchValidMixedCase() {
        assertEquals(Material.DIAMOND_BLOCK, MoreMaterials.match("Diamond_Block"));
    }

    @Test
    void matchWithWhitespace() {
        assertEquals(Material.STONE, MoreMaterials.match("  STONE  "));
    }

    @Test
    void matchInvalidReturnsNull() {
        assertNull(MoreMaterials.match("NOT_A_REAL_MATERIAL_XYZ"));
    }

    @Test
    void matchNullReturnsNull() {
        assertNull(MoreMaterials.match(null));
    }

    @Test
    void matchEmptyReturnsNull() {
        assertNull(MoreMaterials.match(""));
    }
}
