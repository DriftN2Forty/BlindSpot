package dev.driftn2forty.blindspot.util;

import org.bukkit.Material;

public final class MoreMaterials {

    private MoreMaterials() {}

    public static Material match(String name) {
        if (name == null) return null;
        try {
            return Material.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
