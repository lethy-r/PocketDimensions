package mc.lethargos.pocketdimensions.managers;

import mc.lethargos.pocketdimensions.storage.DimensionSettings;
import mc.lethargos.pocketdimensions.storage.Storage;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Read-through cache over {@link Storage} per-dimension settings. A backend
 * read failure is never cached: get() hands out a throwaway empty object and
 * remembers the failure, and save() refuses to persist over an unread owner -
 * otherwise a single DB blip would let a mutated empty object wipe the
 * player's real settings on the next save.
 */
public class SettingsManager {

    private final Storage storage;
    private final Map<UUID, DimensionSettings> cache = new HashMap<>();
    private final Set<UUID> failedLoads = new HashSet<>();
    private final Logger logger = Logger.getLogger("PocketDimensions");

    public SettingsManager(Storage storage) {
        this.storage = storage;
    }

    /**
     * @return the owner's settings; never null. When the backend read fails,
     *         the returned object is empty, NOT cached, and any later save()
     *         for it is refused.
     */
    public DimensionSettings get(UUID owner) {
        DimensionSettings settings = cache.get(owner);
        if (settings != null) {
            return settings;
        }
        settings = storage.getSettings(owner);
        if (settings == null) {
            failedLoads.add(owner);
            logger.warning("Could not read settings for " + owner + " from storage; using temporary defaults"
                    + " (changes will not be saved until the backend is reachable).");
            return new DimensionSettings();
        }
        failedLoads.remove(owner);
        cache.put(owner, settings);
        return settings;
    }

    /** Persists the settings and updates the cache, refusing saves over unread data. */
    public void save(UUID owner, DimensionSettings settings) {
        if (failedLoads.contains(owner) && !cache.containsKey(owner)) {
            logger.severe("Refusing to save settings for " + owner + ": the stored settings could not be read."
                    + " Saving would overwrite them with empty data.");
            return;
        }
        storage.saveSettings(owner, settings);
        failedLoads.remove(owner);
        cache.put(owner, settings);
    }

    public void invalidateAll() {
        cache.clear();
        failedLoads.clear();
    }
}
