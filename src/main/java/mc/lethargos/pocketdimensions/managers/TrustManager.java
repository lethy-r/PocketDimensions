package mc.lethargos.pocketdimensions.managers;

import mc.lethargos.pocketdimensions.storage.Storage;
import mc.lethargos.pocketdimensions.storage.TrustTier;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Read-through cache over {@link Storage} trust data. The protection
 * listener checks trust on every block break/place, so all reads must be
 * memory-only; writes go through to storage immediately.
 */
public class TrustManager {

    private final Storage storage;
    private final Map<UUID, Map<UUID, TrustTier>> cache = new HashMap<>();

    public TrustManager(Storage storage) {
        this.storage = storage;
    }

    /** @return the tier the owner granted the target, or null if untrusted. */
    public TrustTier getTier(UUID owner, UUID target) {
        Map<UUID, TrustTier> trusts = cache.computeIfAbsent(owner, storage::getTrusts);
        return trusts.get(target);
    }

    public void setTrust(UUID owner, UUID target, TrustTier tier) {
        storage.setTrust(owner, target, tier);
        cache.computeIfAbsent(owner, storage::getTrusts).put(target, tier);
    }

    public void clearTrust(UUID owner, UUID target) {
        storage.clearTrust(owner, target);
        Map<UUID, TrustTier> trusts = cache.get(owner);
        if (trusts != null) {
            trusts.remove(target);
        }
    }

    public Map<UUID, TrustTier> getTrusts(UUID owner) {
        return cache.computeIfAbsent(owner, storage::getTrusts);
    }

    /** Drops all cached entries (used after a purge). */
    public void invalidateAll() {
        cache.clear();
    }
}
