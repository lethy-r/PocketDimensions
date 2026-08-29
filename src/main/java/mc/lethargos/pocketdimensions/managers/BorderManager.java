package mc.lethargos.pocketdimensions.managers;

import mc.lethargos.pocketdimensions.storage.Storage;

import java.util.UUID;

/**
 * Thin facade over the active Storage backend. Preserves the pre-0.0.6
 * call signature so command and event code is unchanged.
 */
public class BorderManager {

    private final Storage storage;

    public BorderManager(Storage storage) {
        this.storage = storage;
    }

    public void setBorderSize(UUID playerUUID, int size) {
        storage.setBorderSize(playerUUID, size);
    }

    public Integer getBorderSize(UUID playerUUID) {
        return storage.getBorderSize(playerUUID);
    }
}
