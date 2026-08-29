package mc.lethargos.pocketdimensions.storage;

import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JDBC Storage backend for SQLite (file) and MySQL. Drivers are shaded into
 * the plugin jar because newer Paper releases no longer bundle MySQL
 * Connector/J and no server bundles SQLite.
 *
 * Uses a single kept connection with a validity check and one reconnect
 * retry per operation - adequate for this plugin's write frequency without
 * pulling in a connection pool.
 */
public class SqlStorage implements Storage {

    public enum Backend { SQLITE, MYSQL }

    private final Backend backend;
    private final String jdbcUrl;
    private final String user;
    private final String password;
    private final String tablePrefix;
    private final Logger logger;

    private Connection connection;

    public SqlStorage(Backend backend, ConfigurationSection config, File dataFolder, Logger logger) {
        this.backend = backend;
        this.logger = logger;
        this.tablePrefix = config.getString("table-prefix", "pd_");

        if (backend == Backend.SQLITE) {
            String file = config.getString("sqlite.file", "database.db");
            this.jdbcUrl = "jdbc:sqlite:" + new File(dataFolder, file).getAbsolutePath();
            this.user = null;
            this.password = null;
        } else {
            String host = config.getString("mysql.host", "localhost");
            int port = config.getInt("mysql.port", 3306);
            String database = config.getString("mysql.database", "pocketdimensions");
            boolean useSsl = config.getBoolean("mysql.use-ssl", false);
            this.jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useSSL=" + useSsl
                    + "&allowPublicKeyRetrieval=true"
                    + "&characterEncoding=utf8&useUnicode=true";
            this.user = config.getString("mysql.username", "root");
            this.password = config.getString("mysql.password", "");
        }
    }

    @Override
    public void init() {
        try {
            // Register explicitly; DriverManager service loading is unreliable
            // inside plugin classloaders.
            Class.forName(backend == Backend.SQLITE ? "org.sqlite.JDBC" : "com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("JDBC driver class not found for " + backend, e);
        }
        connect(true);
        createTables();
        logger.info("Using " + backend + " storage (" + (backend == Backend.SQLITE ? "file" : jdbcUrl.replaceAll("\\?.*", ""))
                + ") with table prefix '" + tablePrefix + "'.");
    }

    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
            connection = null;
        }
    }

    private void connect(boolean failHard) {
        try {
            if (user != null) {
                connection = DriverManager.getConnection(jdbcUrl, user, password);
            } else {
                connection = DriverManager.getConnection(jdbcUrl);
            }
        } catch (SQLException e) {
            connection = null;
            if (failHard) {
                throw new IllegalStateException("Could not open " + backend + " connection: " + e.getMessage(), e);
            }
            logger.log(Level.SEVERE, "Could not (re)open " + backend + " connection: " + e.getMessage());
        }
    }

    private Connection connection() throws SQLException {
        if (connection == null || connection.isClosed() || !connection.isValid(2)) {
            close();
            connect(false);
            if (connection == null) {
                throw new SQLException("No " + backend + " connection available");
            }
        }
        return connection;
    }

    private void createTables() {
        String locations = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "locations ("
                + "uuid VARCHAR(36) NOT NULL,"
                + "kind VARCHAR(4) NOT NULL,"
                + "world VARCHAR(128) NOT NULL,"
                + "x DOUBLE NOT NULL DEFAULT 0,"
                + "y DOUBLE NOT NULL DEFAULT 0,"
                + "z DOUBLE NOT NULL DEFAULT 0,"
                + "yaw DOUBLE NOT NULL DEFAULT 0,"
                + "pitch DOUBLE NOT NULL DEFAULT 0,"
                + "PRIMARY KEY (uuid, kind))";
        String borders = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "borders ("
                + "uuid VARCHAR(36) NOT NULL PRIMARY KEY,"
                + "border_size INT NOT NULL)";
        String meta = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "meta ("
                + "uuid VARCHAR(36) NOT NULL,"
                + "meta_key VARCHAR(64) NOT NULL,"
                + "meta_value VARCHAR(255),"
                + "PRIMARY KEY (uuid, meta_key))";
        String trusts = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "trusts ("
                + "owner VARCHAR(36) NOT NULL,"
                + "target VARCHAR(36) NOT NULL,"
                + "tier VARCHAR(16) NOT NULL,"
                + "PRIMARY KEY (owner, target))";
        String settings = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "settings ("
                + "uuid VARCHAR(36) NOT NULL PRIMARY KEY,"
                + "preset VARCHAR(16),"
                + "environment VARCHAR(16),"
                + "notify_entry INT,"
                + "gamerules TEXT)";
        try {
            Connection con = connection();
            try (PreparedStatement s1 = con.prepareStatement(locations);
                 PreparedStatement s2 = con.prepareStatement(borders);
                 PreparedStatement s3 = con.prepareStatement(meta);
                 PreparedStatement s4 = con.prepareStatement(trusts);
                 PreparedStatement s5 = con.prepareStatement(settings)) {
                s1.executeUpdate();
                s2.executeUpdate();
                s3.executeUpdate();
                s4.executeUpdate();
                s5.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create storage tables: " + e.getMessage(), e);
        }
    }

    /** Runs the work with a single reconnect retry on connection-level failures. */
    private <T> T withRetry(SqlOperation<T> operation, String errorContext, T fallback) {
        try {
            return operation.run(connection());
        } catch (SQLException first) {
            logger.warning(backend + " storage error (" + errorContext + "), retrying once: " + first.getMessage());
            close();
            try {
                connect(false);
                if (connection == null) {
                    throw first;
                }
                return operation.run(connection);
            } catch (SQLException second) {
                logger.log(Level.SEVERE, backend + " storage failed (" + errorContext + "): " + second.getMessage());
                return fallback;
            }
        }
    }

    private interface SqlOperation<T> {
        T run(Connection connection) throws SQLException;
    }

    @Override
    public StoredLocation getLastLocation(UUID player, boolean pocketDimension) {
        return withRetry(con -> {
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT world, x, y, z, yaw, pitch FROM " + tablePrefix + "locations WHERE uuid = ? AND kind = ?")) {
                ps.setString(1, player.toString());
                ps.setString(2, pocketDimension ? "pd" : "main");
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new StoredLocation(rs.getString("world"),
                                rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                                rs.getFloat("yaw"), rs.getFloat("pitch"));
                    }
                    return null;
                }
            }
        }, "getLastLocation", null);
    }

    @Override
    public void saveLastLocation(UUID player, StoredLocation location, boolean pocketDimension) {
        String kind = pocketDimension ? "pd" : "main";
        withRetry(con -> {
            int updated;
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE " + tablePrefix + "locations SET world = ?, x = ?, y = ?, z = ?, yaw = ?, pitch = ? "
                            + "WHERE uuid = ? AND kind = ?")) {
                ps.setString(1, location.getWorld());
                ps.setDouble(2, location.getX());
                ps.setDouble(3, location.getY());
                ps.setDouble(4, location.getZ());
                ps.setDouble(5, location.getYaw());
                ps.setDouble(6, location.getPitch());
                ps.setString(7, player.toString());
                ps.setString(8, kind);
                updated = ps.executeUpdate();
            }
            if (updated == 0) {
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO " + tablePrefix + "locations (uuid, kind, world, x, y, z, yaw, pitch) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, player.toString());
                    ps.setString(2, kind);
                    ps.setString(3, location.getWorld());
                    ps.setDouble(4, location.getX());
                    ps.setDouble(5, location.getY());
                    ps.setDouble(6, location.getZ());
                    ps.setDouble(7, location.getYaw());
                    ps.setDouble(8, location.getPitch());
                    ps.executeUpdate();
                }
            }
            return null;
        }, "saveLastLocation", null);
    }

    @Override
    public Integer getBorderSize(UUID player) {
        return withRetry(con -> {
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT border_size FROM " + tablePrefix + "borders WHERE uuid = ?")) {
                ps.setString(1, player.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : null;
                }
            }
        }, "getBorderSize", null);
    }

    @Override
    public void setBorderSize(UUID player, int size) {
        withRetry(con -> {
            int updated;
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE " + tablePrefix + "borders SET border_size = ? WHERE uuid = ?")) {
                ps.setInt(1, size);
                ps.setString(2, player.toString());
                updated = ps.executeUpdate();
            }
            if (updated == 0) {
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO " + tablePrefix + "borders (uuid, border_size) VALUES (?, ?)")) {
                    ps.setString(1, player.toString());
                    ps.setInt(2, size);
                    ps.executeUpdate();
                }
            }
            return null;
        }, "setBorderSize", null);
    }

    @Override
    public String getMeta(UUID player, String key) {
        return withRetry(con -> {
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT meta_value FROM " + tablePrefix + "meta WHERE uuid = ? AND meta_key = ?")) {
                ps.setString(1, player.toString());
                ps.setString(2, key);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString(1) : null;
                }
            }
        }, "getMeta", null);
    }

    @Override
    public void setMeta(UUID player, String key, String value) {
        withRetry(con -> {
            int updated;
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE " + tablePrefix + "meta SET meta_value = ? WHERE uuid = ? AND meta_key = ?")) {
                ps.setString(1, value);
                ps.setString(2, player.toString());
                ps.setString(3, key);
                updated = ps.executeUpdate();
            }
            if (updated == 0 && value != null) {
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO " + tablePrefix + "meta (uuid, meta_key, meta_value) VALUES (?, ?, ?)")) {
                    ps.setString(1, player.toString());
                    ps.setString(2, key);
                    ps.setString(3, value);
                    ps.executeUpdate();
                }
            }
            return null;
        }, "setMeta", null);
    }

    @Override
    public void clearMeta(UUID player, String key) {
        withRetry(con -> {
            try (PreparedStatement ps = con.prepareStatement(
                    "DELETE FROM " + tablePrefix + "meta WHERE uuid = ? AND meta_key = ?")) {
                ps.setString(1, player.toString());
                ps.setString(2, key);
                ps.executeUpdate();
            }
            return null;
        }, "clearMeta", null);
    }

    @Override
    public TrustTier getTrust(UUID owner, UUID target) {
        return withRetry(con -> {
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT tier FROM " + tablePrefix + "trusts WHERE owner = ? AND target = ?")) {
                ps.setString(1, owner.toString());
                ps.setString(2, target.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? TrustTier.fromString(rs.getString(1)) : null;
                }
            }
        }, "getTrust", null);
    }

    @Override
    public void setTrust(UUID owner, UUID target, TrustTier tier) {
        withRetry(con -> {
            int updated;
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE " + tablePrefix + "trusts SET tier = ? WHERE owner = ? AND target = ?")) {
                ps.setString(1, tier.name());
                ps.setString(2, owner.toString());
                ps.setString(3, target.toString());
                updated = ps.executeUpdate();
            }
            if (updated == 0) {
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO " + tablePrefix + "trusts (owner, target, tier) VALUES (?, ?, ?)")) {
                    ps.setString(1, owner.toString());
                    ps.setString(2, target.toString());
                    ps.setString(3, tier.name());
                    ps.executeUpdate();
                }
            }
            return null;
        }, "setTrust", null);
    }

    @Override
    public void clearTrust(UUID owner, UUID target) {
        withRetry(con -> {
            try (PreparedStatement ps = con.prepareStatement(
                    "DELETE FROM " + tablePrefix + "trusts WHERE owner = ? AND target = ?")) {
                ps.setString(1, owner.toString());
                ps.setString(2, target.toString());
                ps.executeUpdate();
            }
            return null;
        }, "clearTrust", null);
    }

    @Override
    public Map<UUID, TrustTier> getTrusts(UUID owner) {
        Map<UUID, TrustTier> result = new HashMap<>();
        withRetry(con -> {
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT target, tier FROM " + tablePrefix + "trusts WHERE owner = ?")) {
                ps.setString(1, owner.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        try {
                            TrustTier tier = TrustTier.fromString(rs.getString(2));
                            if (tier != null) {
                                result.put(UUID.fromString(rs.getString(1)), tier);
                            }
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
            }
            return null;
        }, "getTrusts", null);
        return result;
    }

    @Override
    public DimensionSettings getSettings(UUID owner) {
        return withRetry(con -> {
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT preset, environment, notify_entry, gamerules FROM " + tablePrefix + "settings WHERE uuid = ?")) {
                ps.setString(1, owner.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return new DimensionSettings();
                    }
                    DimensionSettings settings = new DimensionSettings();
                    String preset = rs.getString(1);
                    if (preset != null) settings.setPreset(preset);
                    String environment = rs.getString(2);
                    if (environment != null) settings.setEnvironment(environment);
                    int notify = rs.getInt(3);
                    if (!rs.wasNull()) {
                        settings.setNotifyOnEntry(notify != 0);
                    }
                    String gamerules = rs.getString(4);
                    if (gamerules != null) {
                        try {
                            Object parsed = new org.json.simple.parser.JSONParser().parse(gamerules);
                            if (parsed instanceof org.json.simple.JSONObject) {
                                for (Object key : ((org.json.simple.JSONObject) parsed).keySet()) {
                                    Object value = ((org.json.simple.JSONObject) parsed).get(key);
                                    if (key != null && value != null) {
                                        settings.setGameruleOverride(String.valueOf(key), String.valueOf(value));
                                    }
                                }
                            }
                        } catch (org.json.simple.parser.ParseException ignored) {
                        }
                    }
                    return settings;
                }
            }
        }, "getSettings", new DimensionSettings());
    }

    @Override
    public void saveSettings(UUID owner, DimensionSettings settings) {
        String gamerulesJson = settings.getGameruleOverrides().isEmpty()
                ? null : settings.toJson().get("gamerules").toString();
        withRetry(con -> {
            int updated;
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE " + tablePrefix + "settings SET preset = ?, environment = ?, notify_entry = ?, gamerules = ? WHERE uuid = ?")) {
                ps.setString(1, settings.getPreset());
                ps.setString(2, settings.getEnvironment());
                if (settings.getNotifyOnEntry() == null) {
                    ps.setNull(3, java.sql.Types.INTEGER);
                } else {
                    ps.setInt(3, settings.getNotifyOnEntry() ? 1 : 0);
                }
                ps.setString(4, gamerulesJson);
                ps.setString(5, owner.toString());
                updated = ps.executeUpdate();
            }
            if (updated == 0) {
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO " + tablePrefix + "settings (uuid, preset, environment, notify_entry, gamerules) VALUES (?, ?, ?, ?, ?)")) {
                    ps.setString(1, owner.toString());
                    ps.setString(2, settings.getPreset());
                    ps.setString(3, settings.getEnvironment());
                    if (settings.getNotifyOnEntry() == null) {
                        ps.setNull(4, java.sql.Types.INTEGER);
                    } else {
                        ps.setInt(4, settings.getNotifyOnEntry() ? 1 : 0);
                    }
                    ps.setString(5, gamerulesJson);
                    ps.executeUpdate();
                }
            }
            return null;
        }, "saveSettings", null);
    }

    @Override
    public void purgePlayer(UUID player) {
        String[] statements = {
                "DELETE FROM " + tablePrefix + "locations WHERE uuid = ?",
                "DELETE FROM " + tablePrefix + "borders WHERE uuid = ?",
                "DELETE FROM " + tablePrefix + "meta WHERE uuid = ?",
                "DELETE FROM " + tablePrefix + "settings WHERE uuid = ?",
                "DELETE FROM " + tablePrefix + "trusts WHERE owner = ? OR target = ?"
        };
        withRetry(con -> {
            for (String sql : statements) {
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setString(1, player.toString());
                    if (sql.contains("OR target")) {
                        ps.setString(2, player.toString());
                    }
                    ps.executeUpdate();
                }
            }
            return null;
        }, "purgePlayer", null);
    }

    @Override
    public boolean isEmpty() {
        Integer count = withRetry(con -> {
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT COUNT(*) FROM " + tablePrefix + "locations")) {
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        }, "isEmpty", null);
        return count == null || count == 0;
    }
}
