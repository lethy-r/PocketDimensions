package mc.lethargos.pocketdimensions.utils;

import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.UUID;

public class WorldUtils {

    public static final String WORLDS_FOLDER = "pocketdimensionworlds";
    public static final String PD_NAME_MARKER = "pocketdimension-";

    private WorldUtils() {
    }

    public static boolean isPocketWorld(World world) {
        return world != null && world.getName().contains(PD_NAME_MARKER);
    }

    /**
     * Resolves the server's main world without assuming the level name is
     * "world": prefer the exact name, then the default world, then the first
     * world that is not a pocket dimension.
     */
    public static World getMainWorld() {
        World world = Bukkit.getWorld("world");
        if (world != null) {
            return world;
        }
        for (World candidate : Bukkit.getWorlds()) {
            if (!isPocketWorld(candidate)) {
                return candidate;
            }
        }
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
    }

    /** "pocketdimensionworlds/pocketdimension-<uuid>" - the full world name used everywhere. */
    public static String pocketWorldName(UUID ownerUuid) {
        return WORLDS_FOLDER + "/" + PD_NAME_MARKER + ownerUuid;
    }

    /** "pocketdimension-<uuid>" - the world name some server wrappers flatten to. */
    public static String simpleWorldName(String fullWorldName) {
        int slash = fullWorldName.indexOf('/');
        return slash >= 0 ? fullWorldName.substring(slash + 1) : fullWorldName;
    }

    /** @return the owner UUID parsed from a pocket dimension world name, or null. */
    public static UUID ownerUuidOf(World world) {
        if (world == null) {
            return null;
        }
        String name = simpleWorldName(world.getName());
        int marker = name.indexOf(PD_NAME_MARKER);
        if (marker < 0) {
            return null;
        }
        try {
            return UUID.fromString(name.substring(marker + PD_NAME_MARKER.length()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
