package dev.driftn2forty.blindspot.nms;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.util.BlockVector;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * NMS-based chunk section scanner that uses palette queries to efficiently
 * locate blocks by material type. Reflection is used to access server internals
 * so that the plugin compiles against paper-api alone.
 *
 * <h2>Why NMS?</h2>
 * Bukkit exposes no API for querying which block types exist in a chunk section.
 * A brute-force scan of all ~98K blocks per chunk is expensive. NMS provides
 * {@code PalettedContainer.maybeHas(Predicate)} which checks the section's
 * palette in O(1) — if the material isn't in the palette, we skip all 4096
 * blocks in that section.
 *
 * <h2>Targeted NMS classes (Paper 1.20.5+ / Mojang mappings)</h2>
 * <ul>
 *   <li>{@code org.bukkit.craftbukkit.CraftChunk}           — {@code getHandle()} or {@code getHandle(ChunkStatus)}</li>
 *   <li>{@code net.minecraft.world.level.chunk.LevelChunk}  — inherited {@code getSections()}</li>
 *   <li>{@code net.minecraft.world.level.chunk.LevelChunkSection}
 *       — {@code hasOnlyAir()}, field {@code states} (PalettedContainer)</li>
 *   <li>{@code net.minecraft.world.level.chunk.PalettedContainer}
 *       — {@code maybeHas(Predicate)}, {@code get(int,int,int)}</li>
 *   <li>{@code net.minecraft.world.level.block.state.BlockState}
 *       — {@code getBukkitMaterial()} (Paper addition)</li>
 * </ul>
 *
 * <h2>Version maintenance</h2>
 * When updating to a new Minecraft major version, check:
 * <ol>
 *   <li>Class renames: {@code LevelChunkSection}, {@code PalettedContainer}</li>
 *   <li>Method renames: {@code getSections}, {@code maybeHas}, {@code hasOnlyAir}</li>
 *   <li>Field name changes: {@code states} in LevelChunkSection</li>
 *   <li>{@code getBukkitMaterial()} availability — if removed, fall back to
 *       {@code CraftBlockType.minecraftToBukkit(blockState.getBlock())}</li>
 *   <li>Section Y index calculation: {@code chunk.getMinSectionY()}</li>
 * </ol>
 * All reflected handles are resolved once at construction time; if any fail
 * the scanner logs a warning and {@link #isAvailable()} returns {@code false}.
 */
public final class NmsChunkScanner {

    private final Logger logger;
    private final boolean available;

    // --- reflected handles (resolved once) ---
    private MethodHandle craftChunkGetHandle;   // CraftChunk.getHandle(ChunkStatus) -> LevelChunk
    private MethodHandle getSections;           // ChunkAccess.getSections() -> LevelChunkSection[]
    private MethodHandle hasOnlyAir;            // LevelChunkSection.hasOnlyAir() -> boolean
    private MethodHandle maybeHas;              // PalettedContainer.maybeHas(Predicate) -> boolean
    private MethodHandle paletteGet;            // PalettedContainer.get(int,int,int) -> BlockState
    private MethodHandle getBukkitMaterial;      // BlockState.getBukkitMaterial() -> Material
    private MethodHandle getMinSectionY;        // LevelChunk.getMinSectionY() -> int (inherited from ChunkAccess)
    private Field statesField;                  // LevelChunkSection.states

    public NmsChunkScanner(Logger logger) {
        this.logger = logger;
        this.available = resolveHandles();
    }

    /**
     * Whether NMS reflection resolved successfully.
     * If {@code false}, {@link #scan} will always return an empty list.
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * Scan a chunk for blocks whose Bukkit Material is in {@code targets}.
     * Uses palette-level filtering to skip sections that contain none of the
     * target materials (O(1) per section).
     *
     * @param chunk   the Bukkit chunk to scan
     * @param targets the set of materials to look for
     * @return an unmodifiable list of world-space block positions, or empty if
     *         NMS is unavailable or an error occurs
     */
    public List<BlockVector> scan(Chunk chunk, Set<Material> targets) {
        if (!available || targets.isEmpty()) return Collections.emptyList();
        try {
            return doScan(chunk, targets);
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[BlindSpot] NMS chunk scan failed for chunk "
                    + chunk.getX() + "," + chunk.getZ() + " — disabling scan for this invocation", t);
            return Collections.emptyList();
        }
    }

    // -----------------------------------------------------------------------
    //  Internal
    // -----------------------------------------------------------------------

    private List<BlockVector> doScan(Chunk chunk, Set<Material> targets) throws Throwable {
        // Step 1: CraftChunk -> LevelChunk (NMS)
        Object nmsChunk = craftChunkGetHandle.invoke(chunk);

        // Step 2: LevelChunkSection[] from the NMS chunk
        Object[] sections = (Object[]) getSections.invoke(nmsChunk);
        if (sections == null || sections.length == 0) return Collections.emptyList();

        // Step 3: base section Y index (e.g. -4 for overworld = y -64)
        int minSectionY = (int) getMinSectionY.invoke(nmsChunk);
        int chunkWorldX = chunk.getX() << 4;  // chunk block origin
        int chunkWorldZ = chunk.getZ() << 4;

        List<BlockVector> found = new ArrayList<>();

        for (int i = 0; i < sections.length; i++) {
            Object section = sections[i];
            if (section == null) continue;

            // Skip sections that are entirely air
            if ((boolean) hasOnlyAir.invoke(section)) continue;

            // Step 4: get the PalettedContainer<BlockState> from the section
            Object palette = statesField.get(section);
            if (palette == null) continue;

            // Step 5: palette-level check — does this section's palette
            //         contain ANY of the target materials?
            Predicate<Object> paletteFilter = nmsState -> {
                try {
                    Material mat = (Material) getBukkitMaterial.invoke(nmsState);
                    return targets.contains(mat);
                } catch (Throwable t) {
                    return false;
                }
            };
            if (!(boolean) maybeHas.invoke(palette, paletteFilter)) continue;

            // Step 6: the palette matched — scan 4096 blocks in this section
            int sectionBaseY = (minSectionY + i) << 4;

            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        Object nmsState = paletteGet.invoke(palette, x, y, z);
                        Material mat = (Material) getBukkitMaterial.invoke(nmsState);
                        if (targets.contains(mat)) {
                            found.add(new BlockVector(
                                    chunkWorldX + x,
                                    sectionBaseY + y,
                                    chunkWorldZ + z));
                        }
                    }
                }
            }
        }

        return found.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(found);
    }

    /**
     * Resolve all NMS method/field handles via reflection.
     * Called once at construction. Returns {@code true} if all handles
     * resolved successfully.
     */
    private boolean resolveHandles() {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            // --- CraftChunk.getHandle(...) -> ChunkAccess/LevelChunk ---
            // Older Paper: no-arg getHandle() returning LevelChunk
            // Paper 1.21+:  getHandle(ChunkStatus) returning ChunkAccess
            Class<?> craftChunkClass = Class.forName("org.bukkit.craftbukkit.CraftChunk");
            Method getHandleMethod;
            try {
                getHandleMethod = craftChunkClass.getMethod("getHandle");
                this.craftChunkGetHandle = lookup.unreflect(getHandleMethod);
            } catch (NoSuchMethodException ignored) {
                // Paper 1.21+ removed no-arg getHandle; find getHandle(ChunkStatus)
                Class<?> chunkStatusClass = findChunkStatusClass();
                Object chunkStatusFull = chunkStatusClass.getField("FULL").get(null);
                getHandleMethod = craftChunkClass.getMethod("getHandle", chunkStatusClass);
                // Bind ChunkStatus.FULL as the constant second argument so the
                // handle can be invoked with just the CraftChunk instance.
                this.craftChunkGetHandle = MethodHandles.insertArguments(
                        lookup.unreflect(getHandleMethod), 1, chunkStatusFull);
            }

            Class<?> nmsChunkClass = getHandleMethod.getReturnType();

            // --- ChunkAccess.getSections() -> LevelChunkSection[] ---
            Method getSectionsMethod = nmsChunkClass.getMethod("getSections");
            this.getSections = lookup.unreflect(getSectionsMethod);

            Class<?> sectionClass = getSectionsMethod.getReturnType().getComponentType();

            // --- LevelChunkSection.hasOnlyAir() -> boolean ---
            this.hasOnlyAir = lookup.unreflect(sectionClass.getMethod("hasOnlyAir"));

            // --- LevelChunkSection.states (PalettedContainer field) ---
            // The field is named "states" in Mojang mappings (Paper 1.20.5+)
            this.statesField = findField(sectionClass, "states");
            this.statesField.setAccessible(true);
            Class<?> paletteClass = this.statesField.getType();

            // --- PalettedContainer.maybeHas(Predicate) -> boolean ---
            this.maybeHas = lookup.unreflect(paletteClass.getMethod("maybeHas", Predicate.class));

            // --- PalettedContainer.get(int, int, int) -> BlockState ---
            this.paletteGet = lookup.unreflect(paletteClass.getMethod("get", int.class, int.class, int.class));

            // --- ChunkAccess.getMinSectionY() -> int ---
            // Inherited from LevelHeightAccessor
            this.getMinSectionY = lookup.unreflect(nmsChunkClass.getMethod("getMinSectionY"));

            // --- BlockState.getBukkitMaterial() -> Material ---
            // This is a Paper addition on NMS BlockState
            Class<?> nmsBlockStateClass = paletteClass.getMethod("get", int.class, int.class, int.class)
                    .getReturnType();
            this.getBukkitMaterial = lookup.unreflect(
                    nmsBlockStateClass.getMethod("getBukkitMaterial"));

            logger.info("[BlindSpot] NMS chunk scanner initialized successfully "
                    + "(section class: " + sectionClass.getSimpleName()
                    + ", palette class: " + paletteClass.getSimpleName() + ")");
            return true;

        } catch (Throwable t) {
            logger.log(Level.WARNING,
                    "[BlindSpot] NMS chunk scanner unavailable — scan-block masking disabled. "
                    + "This may happen on unsupported server versions. "
                    + "Error: " + t.getMessage());
            return false;
        }
    }

    /**
     * Finds a field by name in the class or its superclasses.
     */
    private static Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        Class<?> c = clazz;
        while (c != null) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name + " in " + clazz.getName() + " (including supers)");
    }

    /**
     * Locates the NMS {@code ChunkStatus} class. The package moved between
     * Minecraft versions:
     * <ul>
     *   <li>1.20.5–1.20.6: {@code net.minecraft.world.level.chunk.ChunkStatus}</li>
     *   <li>1.21+: {@code net.minecraft.world.level.chunk.status.ChunkStatus}</li>
     * </ul>
     */
    private static Class<?> findChunkStatusClass() throws ClassNotFoundException {
        try {
            return Class.forName("net.minecraft.world.level.chunk.status.ChunkStatus");
        } catch (ClassNotFoundException ignored) {
            return Class.forName("net.minecraft.world.level.chunk.ChunkStatus");
        }
    }
}
