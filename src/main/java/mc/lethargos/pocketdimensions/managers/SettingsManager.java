package mc.lethargos.pocketdimensions.managers;

import mc.lethargos.pocketdimensions.storage.DimensionSettings;
import mc.lethargos.pocketdimensions.storage.Storage;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Read-through cache over {@link Storage} per-dimension settings.
 */
public class SettingsManager {

    private final Storage storage;
    private final Map<UUID, DimensionSettings> cache = new HashMap<>();

    public SettingsManager(Storage storage) {
        this.storage = storage;
    }

    /** @return the owner's settings; never null. Mutating the returned object does not persist. */
    public DimensionSettings get(UUID owner) {
        return cache.computeIfAbsent(owner, storage::getSettings);
    }

    /** Persists the settings and updates the cache. */
    public void save(UUID owner, DimensionSettings settings) {
        storage.saveSettings(owner, settings);
        cache.put(owner, settings);
    }

    public void invalidateAll() {
        cache.clear();
    }
}
