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

    /** Imports lastlocs.json / pdborders.json into SQL and renames them *.imported. */
    private void importFromJsonFiles() {
        JsonStorage legacy = new JsonStorage(plugin.getDataFolder(), plugin.getLogger());
        if (legacy.isEmpty()) {
            return;
        }
        plugin.getLogger().info("Migrating existing JSON data into SQL storage...");

        JSONObject locations = legacy.readLocationsFile();
        for (Object keyObj : locations.keySet()) {
            String key = String.valueOf(keyObj);
            Object raw = locations.get(keyObj);
            if (!(raw instanceof JSONObject)) {
                continue;
            }
            JSONObject loc = (JSONObject) raw;
            Object worldObj = loc.get("world");
            if (worldObj == null) {
                continue;
            }
            StoredLocation stored = new StoredLocation(
                    String.valueOf(worldObj),
                    asDouble(loc.get("x")), asDouble(loc.get("y")), asDouble(loc.get("z")),
                    (float) asDouble(loc.get("yaw")), (float) asDouble(loc.get("pitch")));
            if (key.endsWith("_pd")) {
                storage.saveLastLocation(UUID.fromString(key.substring(0, key.length() - 3)), stored, true);
            } else if (key.length() >= 36) {
                storage.saveLastLocation(UUID.fromString(key.substring(0, 36)), stored, false);
            }
        }

        JSONObject borders = legacy.readBordersFile();
        for (Object keyObj : borders.keySet()) {
            Object value = borders.get(keyObj);
            if (value instanceof Number) {
                try {
                    storage.setBorderSize(UUID.fromString(String.valueOf(keyObj)), ((Number) value).intValue());
                } catch (IllegalArgumentException ignored) {
                    // Not a UUID key; skip.
                }
            }
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
