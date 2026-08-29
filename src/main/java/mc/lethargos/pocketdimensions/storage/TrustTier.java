package mc.lethargos.pocketdimensions.storage;

/**
 * Trust levels a dimension owner can grant. Stored by name in both backends.
 */
public enum TrustTier {
    VISITOR,
    BUILDER,
    TRUSTED;

    /**
     * Lenient parse; returns null for unknown names so callers can report an
     * invalid tier instead of guessing.
     */
    public static TrustTier fromString(String name) {
        if (name == null) {
            return null;
        }
        for (TrustTier tier : values()) {
            if (tier.name().equalsIgnoreCase(name)) {
                return tier;
            }
        }
        return null;
    }

    public String display() {
        char first = name().charAt(0);
        return first + name().substring(1).toLowerCase();
    }
}
