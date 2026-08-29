package mc.lethargos.pocketdimensions.storage;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * File-backed Storage implementation. Keeps the 0.0.x on-disk format
 * (lastlocs.json / pdborders.json) so existing installs migrate without
 * any action, and adds yaw/pitch + metadata keys on top.
 *
 * All operations are serialized and writes are atomic (temp file + move),
 * fixing the lost-update and torn-file issues of the pre-0.0.6 version.
 */
public class JsonStorage implements Storage {

    private static final String META_KEY_PREFIX = "meta|";

    private final File locationsFile;
    private final File bordersFile;
    private final File dimensionsFile;
    private final Logger logger;
    private final Object lock = new Object();
    /** Files that could not be read or quarantined; writes to them are refused so a
     *  corrupt read can never be written back over the remaining data. */
    private final java.util.Set<String> poisonedFiles = new java.util.HashSet<>();

    public JsonStorage(File dataFolder, Logger logger) {
        this.locationsFile = new File(dataFolder, "lastlocs.json");
        this.bordersFile = new File(dataFolder, "pdborders.json");
        this.dimensionsFile = new File(dataFolder, "dimensions.json");
        this.logger = logger;
    }

    @Override
    public void init() {
        // Files are created lazily on first write.
    }

    @Override
    public void close() {
        // Nothing persistent to release.
    }

    @Override
    public StoredLocation getLastLocation(UUID player, boolean pocketDimension) {
        synchronized (lock) {
            JSONObject locations = readJson(locationsFile);
            String key = locationKey(player, pocketDimension);
            Object raw = locations.get(key);
            if (!(raw instanceof JSONObject)) {
                return null;
            }
            JSONObject loc = (JSONObject) raw;
            String world = asString(loc.get("world"));
            if (world == null) {
                return null;
            }
            return new StoredLocation(world,
                    asDouble(loc.get("x"), 0),
                    asDouble(loc.get("y"), 0),
                    asDouble(loc.get("z"), 0),
                    (float) asDouble(loc.get("yaw"), 0),
                    (float) asDouble(loc.get("pitch"), 0));
        }
    }

    @Override
    public void saveLastLocation(UUID player, StoredLocation location, boolean pocketDimension) {
        synchronized (lock) {
            JSONObject locations = readJson(locationsFile);
            JSONObject loc = new JSONObject();
            loc.put("world", location.getWorld());
            loc.put("x", location.getX());
            loc.put("y", location.getY());
            loc.put("z", location.getZ());
            loc.put("yaw", location.getYaw());
            loc.put("pitch", location.getPitch());
            locations.put(locationKey(player, pocketDimension), loc);
            writeJson(locationsFile, locations);
        }
    }

    @Override
    public Integer getBorderSize(UUID player) {
        synchronized (lock) {
            JSONObject borders = readJson(bordersFile);
            Object value = borders.get(player.toString());
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            return null;
        }
    }

    @Override
    public void setBorderSize(UUID player, int size) {
        synchronized (lock) {
            JSONObject borders = readJson(bordersFile);
            borders.put(player.toString(), size);
            writeJson(bordersFile, borders);
        }
    }

    @Override
    public String getMeta(UUID player, String key) {
        synchronized (lock) {
            JSONObject locations = readJson(locationsFile);
            return asString(locations.get(META_KEY_PREFIX + player + "|" + key));
        }
    }

    @Override
    public void setMeta(UUID player, String key, String value) {
        synchronized (lock) {
            JSONObject locations = readJson(locationsFile);
            if (value == null) {
                locations.remove(META_KEY_PREFIX + player + "|" + key);
            } else {
                locations.put(META_KEY_PREFIX + player + "|" + key, value);
            }
            writeJson(locationsFile, locations);
        }
    }

    @Override
    public void clearMeta(UUID player, String key) {
        setMeta(player, key, null);
    }

    @Override
    public TrustTier getTrust(UUID owner, UUID target) {
        Map<UUID, TrustTier> trusts = getTrusts(owner);
        return trusts.get(target);
    }

    @Override
    public void setTrust(UUID owner, UUID target, TrustTier tier) {
        synchronized (lock) {
            JSONObject dimensions = readJson(dimensionsFile);
            JSONObject ownerData = ownerData(dimensions, owner);
            JSONObject trusts = objectOrNew(ownerData, "trusts");
            trusts.put(target.toString(), tier.name());
            writeJson(dimensionsFile, dimensions);
        }
    }

    @Override
    public void clearTrust(UUID owner, UUID target) {
        synchronized (lock) {
            JSONObject dimensions = readJson(dimensionsFile);
            Object ownerRaw = dimensions.get(owner.toString());
            if (!(ownerRaw instanceof JSONObject)) {
                return;
            }
            Object trustsObj = ((JSONObject) ownerRaw).get("trusts");
            if (trustsObj instanceof JSONObject) {
                ((JSONObject) trustsObj).remove(target.toString());
            }
            writeJson(dimensionsFile, dimensions);
        }
    }

    @Override
    public Map<UUID, TrustTier> getTrusts(UUID owner) {
        synchronized (lock) {
            Map<UUID, TrustTier> result = new HashMap<>();
            JSONObject dimensions = readJson(dimensionsFile);
            Object ownerRaw = dimensions.get(owner.toString());
            if (!(ownerRaw instanceof JSONObject)) {
                return result;
            }
            Object trustsObj = ((JSONObject) ownerRaw).get("trusts");
            if (!(trustsObj instanceof JSONObject)) {
                return result;
            }
            for (Object key : ((JSONObject) trustsObj).keySet()) {
                try {
                    TrustTier tier = TrustTier.fromString(String.valueOf(((JSONObject) trustsObj).get(key)));
                    if (tier != null) {
                        result.put(UUID.fromString(String.valueOf(key)), tier);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
            return result;
        }
    }

    @Override
    public DimensionSettings getSettings(UUID owner) {
        synchronized (lock) {
            JSONObject dimensions = readJson(dimensionsFile);
            JSONObject ownerData = (JSONObject) dimensions.get(owner.toString());
            if (ownerData == null || !(ownerData.get("settings") instanceof JSONObject)) {
                return new DimensionSettings();
            }
            return DimensionSettings.fromJson((JSONObject) ownerData.get("settings"));
        }
    }

    @Override
    public void saveSettings(UUID owner, DimensionSettings settings) {
        synchronized (lock) {
            JSONObject dimensions = readJson(dimensionsFile);
            JSONObject ownerData = ownerData(dimensions, owner);
            ownerData.put("settings", settings.toJson());
            writeJson(dimensionsFile, dimensions);
        }
    }

    @Override
    public void purgePlayer(UUID player) {
        synchronized (lock) {
            // Own data: locations, meta, border, dimension entry.
            JSONObject locations = readJson(locationsFile);
            locations.keySet().removeIf(key -> {
                String k = String.valueOf(key);
                return k.equals(player.toString()) || k.equals(player + "_pd") || k.startsWith(META_KEY_PREFIX + player + "|");
            });
            writeJson(locationsFile, locations);

            JSONObject borders = readJson(bordersFile);
            borders.remove(player.toString());
            writeJson(bordersFile, borders);

            JSONObject dimensions = readJson(dimensionsFile);
            dimensions.remove(player.toString());
            // Trust entries other owners granted to this player.
            for (Object key : dimensions.keySet()) {
                Object raw = dimensions.get(key);
                if (raw instanceof JSONObject) {
                    Object trusts = ((JSONObject) raw).get("trusts");
                    if (trusts instanceof JSONObject) {
                        ((JSONObject) trusts).remove(player.toString());
                    }
                }
            }
            writeJson(dimensionsFile, dimensions);
        }
    }

    @SuppressWarnings("unchecked")
    private JSONObject ownerData(JSONObject dimensions, UUID owner) {
        Object raw = dimensions.get(owner.toString());
        if (raw instanceof JSONObject) {
            return (JSONObject) raw;
        }
        JSONObject data = new JSONObject();
        dimensions.put(owner.toString(), data);
        return data;
    }

    @SuppressWarnings("unchecked")
    private JSONObject objectOrNew(JSONObject parent, String key) {
        Object raw = parent.get(key);
        if (raw instanceof JSONObject) {
            return (JSONObject) raw;
        }
        JSONObject object = new JSONObject();
        parent.put(key, object);
        return object;
    }

    @Override
    public boolean isEmpty() {
        synchronized (lock) {
            return !locationsFile.exists() && !bordersFile.exists() && !dimensionsFile.exists();
        }
    }

    /** Used by StorageManager during JSON -> SQL migration. */
    JSONObject readLocationsFile() {
        synchronized (lock) {
            return readJson(locationsFile);
        }
    }

    JSONObject readBordersFile() {
        synchronized (lock) {
            return readJson(bordersFile);
        }
    }

    JSONObject readDimensionsFile() {
        synchronized (lock) {
            return readJson(dimensionsFile);
        }
    }

    void renameFilesAsImported() {
        renameAsImported(locationsFile);
        renameAsImported(bordersFile);
        renameAsImported(dimensionsFile);
    }

    private void renameAsImported(File file) {
        if (!file.exists()) {
            return;
        }
        File target = new File(file.getParentFile(), file.getName() + ".imported");
        try {
            Files.move(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            logger.info("Imported " + file.getName() + " into the new storage backend; renamed to " + target.getName());
        } catch (IOException e) {
            logger.severe("Could not rename " + file.getName() + " after import: " + e.getMessage());
        }
    }

    private static String locationKey(UUID player, boolean pocketDimension) {
        return player + (pocketDimension ? "_pd" : "");
    }

    private JSONObject readJson(File file) {
        if (!file.exists()) {
            return new JSONObject();
        }
        if (poisonedFiles.contains(file.getName())) {
            return new JSONObject();
        }
        try (FileReader reader = new FileReader(file)) {
            Object parsed = new JSONParser().parse(reader);
            if (parsed instanceof JSONObject) {
                return (JSONObject) parsed;
            }
        } catch (IOException | ParseException e) {
            logger.severe("Failed to read " + file.getName() + ": " + e.getMessage());
            quarantine(file);
        }
        return new JSONObject();
    }

    /**
     * Moves an unreadable file out of the way so the next write starts a fresh
     * file instead of erasing the store's remaining content. If the rename
     * fails (file locked), the file is marked poisoned and all writes to it
     * are refused for this session.
     */
    private void quarantine(File file) {
        File backup = new File(file.getParentFile(), file.getName() + ".corrupt-" + System.currentTimeMillis());
        try {
            Files.move(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            logger.severe(file.getName() + " was unreadable and has been quarantined as " + backup.getName()
                    + ". A fresh file will be created; recover any needed data from the backup.");
        } catch (IOException moveException) {
            poisonedFiles.add(file.getName());
            logger.severe(file.getName() + " is unreadable AND could not be quarantined (" + moveException.getMessage()
                    + "). All writes to it are refused this session to protect the remaining data - "
                    + "fix or remove the file and restart.");
        }
    }

    private void writeJson(File file, JSONObject json) {
        if (poisonedFiles.contains(file.getName())) {
            logger.severe("Refusing to write " + file.getName() + " while it is poisoned (unreadable and unremovable).");
            return;
        }
        File temp = new File(file.getParentFile(), file.getName() + ".tmp");
        try (FileWriter writer = new FileWriter(temp)) {
            writer.write(json.toJSONString());
        } catch (IOException e) {
            logger.severe("Failed to write " + file.getName() + ": " + e.getMessage());
            return;
        }
        try {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logger.severe("Failed to replace " + file.getName() + ": " + e.getMessage());
            try {
                Files.deleteIfExists(temp.toPath());
            } catch (IOException ignored) {
            }
        }
    }

    private static String asString(Object value) {
        return value != null ? value.toString() : null;
    }

    private static double asDouble(Object value, double fallback) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }
}
