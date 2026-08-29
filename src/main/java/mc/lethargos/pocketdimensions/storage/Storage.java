package mc.lethargos.pocketdimensions.storage;

import java.util.UUID;

/**
 * Backend-agnostic persistence for last-known locations, world border sizes
 * and small per-player metadata. Implementations: JSON files (default) and
 * SQL via JDBC (SQLite / MySQL).
 */
public interface Storage {

    void init();

    void close();

    /** @return the stored location, or null if none is recorded. */
    StoredLocation getLastLocation(UUID player, boolean pocketDimension);

    void saveLastLocation(UUID player, StoredLocation location, boolean pocketDimension);

    /** @return the stored border size, or null if none is recorded. */
    Integer getBorderSize(UUID player);

    void setBorderSize(UUID player, int size);

    /** @return the metadata value, or null if absent. */
    String getMeta(UUID player, String key);

    void setMeta(UUID player, String key, String value);

    void clearMeta(UUID player, String key);

    // --- Trust system ---

    /** @return the granted tier, or null if the target is not trusted. */
    TrustTier getTrust(UUID owner, UUID target);

    void setTrust(UUID owner, UUID target, TrustTier tier);

    void clearTrust(UUID owner, UUID target);

    /**
     * @return all trust entries granted by the owner (target uuid -> tier),
     *         or null when the backend read failed (callers must not cache null).
     */
    java.util.Map<UUID, TrustTier> getTrusts(UUID owner);

    // --- Per-dimension settings ---

    /**
     * @return the owner's settings (empty when none stored), or null when the
     *         backend read failed (callers must not cache or save over null).
     */
    DimensionSettings getSettings(UUID owner);

    void saveSettings(UUID owner, DimensionSettings settings);

    /**
     * Removes every trace of the player: locations, meta, border, settings and
     * trust entries (granted by them and granted to them).
     */
    void purgePlayer(UUID player);

    /** Used by StorageManager to decide whether a JSON -> SQL migration is needed. */
    boolean isEmpty();
}
