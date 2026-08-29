package mc.lethargos.pocketdimensions.managers;

import mc.lethargos.pocketdimensions.storage.StoredLocation;
import mc.lethargos.pocketdimensions.storage.Storage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Thin facade over the active Storage backend. Preserves the pre-0.0.6
 * call signature so command and event code is unchanged.
 */
public class LocationManager {

    private final Storage storage;

    public LocationManager(Storage storage) {
        this.storage = storage;
    }

    public void saveLastLocation(Player player, boolean isPocketDimension) {
        Location loc = player.getLocation();
        storage.saveLastLocation(player.getUniqueId(),
                new StoredLocation(loc.getWorld().getName(),
                        loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch()),
                isPocketDimension);
    }

    /** @return the stored location resolved against loaded worlds, or null if absent/unresolvable. */
    public Location getLastLocation(Player player, boolean isPocketDimension) {
        StoredLocation stored = storage.getLastLocation(player.getUniqueId(), isPocketDimension);
        if (stored == null) {
            return null;
        }
        World world = Bukkit.getWorld(stored.getWorld());
        if (world == null) {
            return null;
        }
        return new Location(world, stored.getX(), stored.getY(), stored.getZ(), stored.getYaw(), stored.getPitch());
    }

    /** Used by DimensionWorldManager to restore players who logged out inside a pocket dimension. */
    public Location getLastStoredPdLocation(Player player) {
        StoredLocation stored = storage.getLastLocation(player.getUniqueId(), true);
        if (stored == null) {
            return null;
        }
        World world = Bukkit.getWorld(stored.getWorld());
        if (world == null) {
            return null;
        }
        return new Location(world, stored.getX(), stored.getY(), stored.getZ(), stored.getYaw(), stored.getPitch());
    }

    public String getLastStoredPdWorldName(java.util.UUID playerId) {
        StoredLocation stored = storage.getLastLocation(playerId, true);
        return stored != null ? stored.getWorld() : null;
    }
}
