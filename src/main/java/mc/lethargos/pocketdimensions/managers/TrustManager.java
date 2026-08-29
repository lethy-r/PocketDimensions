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

    /**
     * @return the tier the owner granted the target, or null if untrusted.
     *         A backend read failure also returns null (fail closed) without
     *         caching anything.
     */
    public TrustTier getTier(UUID owner, UUID target) {
        Map<UUID, TrustTier> trusts = cache.get(owner);
        if (trusts == null) {
            trusts = storage.getTrusts(owner);
            if (trusts == null) {
                return null; // backend failure - fail closed, do not cache
            }
            cache.put(owner, trusts);
        }
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

    /** @return the owner's trust map; failures return an empty uncached map. */
    public Map<UUID, TrustTier> getTrusts(UUID owner) {
        Map<UUID, TrustTier> trusts = cache.get(owner);
        if (trusts == null) {
            trusts = storage.getTrusts(owner);
            if (trusts == null) {
                return new HashMap<>(); // backend failure - do not cache
            }
            cache.put(owner, trusts);
        }
        return trusts;
    }

    /** Drops all cached entries (used after a purge). */
    public void invalidateAll() {
        cache.clear();
    }
}
