package mc.lethargos.pocketdimensions.storage;

import org.bukkit.plugin.java.JavaPlugin;
import org.json.simple.JSONObject;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * Owns the active {@link Storage} backend, selected from config.yml
 * (storage.type: JSON | SQLITE | MYSQL) and handles the one-time import of
 * legacy JSON files when an admin switches to SQL.
 */
public class StorageManager {

    private final JavaPlugin plugin;
    private final Storage storage;

    public StorageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.storage = createStorage();
    }

    private Storage createStorage() {
        String type = plugin.getConfig().getString("storage.type", "JSON").trim().toUpperCase();
        Logger logger = plugin.getLogger();
        switch (type) {
            case "SQLITE":
                return new SqlStorage(SqlStorage.Backend.SQLITE,
                        plugin.getConfig().getConfigurationSection("storage"), plugin.getDataFolder(), logger);
            case "MYSQL":
                return new SqlStorage(SqlStorage.Backend.MYSQL,
                        plugin.getConfig().getConfigurationSection("storage"), plugin.getDataFolder(), logger);
            case "JSON":
                return new JsonStorage(plugin.getDataFolder(), logger);
            default:
                logger.warning("Unknown storage.type '" + type + "', falling back to JSON.");
                return new JsonStorage(plugin.getDataFolder(), logger);
        }
    }

    public void init() {
        storage.init();
        if (storage instanceof SqlStorage && storage.isEmpty()) {
            importFromJsonFiles();
        }
    }

    public Storage getStorage() {
        return storage;
    }

    /**
     * Imports lastlocs.json (locations + meta), pdborders.json and
     * dimensions.json (trusts + settings) into SQL and renames the source
     * files *.imported - but only when every import step succeeded, so a
     * failed migration is retried on the next start instead of losing data.
     */
    private void importFromJsonFiles() {
        JsonStorage legacy = new JsonStorage(plugin.getDataFolder(), plugin.getLogger());
        if (legacy.isEmpty()) {
            return;
        }
        plugin.getLogger().info("Migrating existing JSON data into SQL storage...");
        boolean failed = false;

        JSONObject locations = legacy.readLocationsFile();
        for (Object keyObj : new java.util.ArrayList<>(locations.keySet())) {
            String key = String.valueOf(keyObj);
            if (key.startsWith("meta|")) {
                continue; // metadata handled below
            }
            Object raw = locations.get(keyObj);
            if (!(raw instanceof JSONObject)) {
                continue;
            }
            JSONObject loc = (JSONObject) raw;
            Object worldObj = loc.get("world");
            if (worldObj == null) {
                continue;
            }
            try {
                StoredLocation stored = new StoredLocation(
                        String.valueOf(worldObj),
                        asDouble(loc.get("x")), asDouble(loc.get("y")), asDouble(loc.get("z")),
                        (float) asDouble(loc.get("yaw")), (float) asDouble(loc.get("pitch")));
                if (key.endsWith("_pd")) {
                    storage.saveLastLocation(UUID.fromString(key.substring(0, key.length() - 3)), stored, true);
                } else if (key.length() >= 36) {
                    storage.saveLastLocation(UUID.fromString(key.substring(0, 36)), stored, false);
                }
            } catch (IllegalArgumentException e) {
                failed = true;
                plugin.getLogger().warning("Migration: skipping unrecognised location key '" + key + "'.");
            }
        }

        for (Object keyObj : locations.keySet()) {
            String key = String.valueOf(keyObj);
            if (!key.startsWith("meta|")) {
                continue;
            }
            Object raw = locations.get(keyObj);
            if (!(raw instanceof String)) {
                continue;
            }
            String rest = key.substring("meta|".length());
            int separator = rest.indexOf('|');
            if (separator <= 0) {
                continue;
            }
            try {
                storage.setMeta(UUID.fromString(rest.substring(0, separator)),
                        rest.substring(separator + 1), (String) raw);
            } catch (IllegalArgumentException e) {
                failed = true;
                plugin.getLogger().warning("Migration: skipping unrecognised meta key '" + key + "'.");
            }
        }

        JSONObject borders = legacy.readBordersFile();
        for (Object keyObj : borders.keySet()) {
            Object value = borders.get(keyObj);
            if (value instanceof Number) {
                try {
                    storage.setBorderSize(UUID.fromString(String.valueOf(keyObj)), ((Number) value).intValue());
                } catch (IllegalArgumentException e) {
                    failed = true;
                    plugin.getLogger().warning("Migration: skipping unrecognised border key '" + keyObj + "'.");
                }
            }
        }

        JSONObject dimensions = legacy.readDimensionsFile();
        for (Object ownerKey : dimensions.keySet()) {
            UUID owner;
            try {
                owner = UUID.fromString(String.valueOf(ownerKey));
            } catch (IllegalArgumentException e) {
                failed = true;
                plugin.getLogger().warning("Migration: skipping unrecognised dimension owner '" + ownerKey + "'.");
                continue;
            }
            Object raw = dimensions.get(ownerKey);
            if (!(raw instanceof JSONObject)) {
                continue;
            }
            JSONObject ownerData = (JSONObject) raw;
            Object trustsObj = ownerData.get("trusts");
            if (trustsObj instanceof JSONObject) {
                for (Object targetKey : ((JSONObject) trustsObj).keySet()) {
                    TrustTier tier = TrustTier.fromString(String.valueOf(((JSONObject) trustsObj).get(targetKey)));
                    if (tier == null) {
                        continue;
                    }
                    try {
                        storage.setTrust(owner, UUID.fromString(String.valueOf(targetKey)), tier);
                    } catch (IllegalArgumentException e) {
                        failed = true;
                        plugin.getLogger().warning("Migration: skipping unrecognised trust target '" + targetKey + "'.");
                    }
                }
            }
            Object settingsObj = ownerData.get("settings");
            if (settingsObj instanceof JSONObject) {
                storage.saveSettings(owner, DimensionSettings.fromJson((JSONObject) settingsObj));
            }
        }

        if (failed) {
            plugin.getLogger().severe("Migration finished with errors - the JSON files were NOT renamed."
                    + " Fix the reported problems and restart to retry, so no data is lost.");
            return;
        }
        legacy.renameFilesAsImported();
        plugin.getLogger().info("Migration complete.");
    }

    private static double asDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0;
    }

    public void close() {
        storage.close();
    }
}
