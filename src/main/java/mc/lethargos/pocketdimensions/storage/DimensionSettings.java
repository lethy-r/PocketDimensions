package mc.lethargos.pocketdimensions.storage;

import org.json.simple.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-dimension player preferences. Nullable fields fall back to the global
 * config; only explicitly set values are persisted.
 */
public class DimensionSettings {

    /** FLAT / VOID / NORMAL; null = use dimension.default-preset. */
    private String preset;

    /** NORMAL / NETHER / THE_END; null = use dimension.default-environment. */
    private String environment;

    /** null = default true. */
    private Boolean notifyOnEntry;

    /** Per-dimension gamerule overrides applied over the config defaults. */
    private final Map<String, String> gameruleOverrides = new LinkedHashMap<>();

    public String getPreset() {
        return preset;
    }

    public void setPreset(String preset) {
        this.preset = normalizeOrNull(preset);
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = normalizeOrNull(environment);
    }

    public Boolean getNotifyOnEntry() {
        return notifyOnEntry;
    }

    public void setNotifyOnEntry(Boolean notifyOnEntry) {
        this.notifyOnEntry = notifyOnEntry;
    }

    public boolean notifyOnEntryOrDefault() {
        return notifyOnEntry == null || notifyOnEntry;
    }

    public Map<String, String> getGameruleOverrides() {
        return gameruleOverrides;
    }

    public void setGameruleOverride(String rule, String value) {
        gameruleOverrides.put(rule, value);
    }

    public void clearGameruleOverride(String rule) {
        gameruleOverrides.remove(rule);
    }

    private static String normalizeOrNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim().toUpperCase();
    }

    // --- JSON (de)serialization shared by both backends ---

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        if (preset != null) json.put("preset", preset);
        if (environment != null) json.put("environment", environment);
        if (notifyOnEntry != null) json.put("notifyOnEntry", notifyOnEntry);
        if (!gameruleOverrides.isEmpty()) {
            JSONObject rules = new JSONObject();
            rules.putAll(gameruleOverrides);
            json.put("gamerules", rules);
        }
        return json;
    }

    @SuppressWarnings("unchecked")
    public static DimensionSettings fromJson(JSONObject json) {
        DimensionSettings settings = new DimensionSettings();
        if (json == null) {
            return settings;
        }
        Object preset = json.get("preset");
        if (preset != null) settings.preset = String.valueOf(preset);
        Object environment = json.get("environment");
        if (environment != null) settings.environment = String.valueOf(environment);
        Object notify = json.get("notifyOnEntry");
        if (notify instanceof Boolean) {
            settings.notifyOnEntry = (Boolean) notify;
        }
        Object rules = json.get("gamerules");
        if (rules instanceof JSONObject) {
            for (Object key : ((JSONObject) rules).keySet()) {
                Object value = ((JSONObject) rules).get(key);
                if (key != null && value != null) {
                    settings.gameruleOverrides.put(String.valueOf(key), String.valueOf(value));
                }
            }
        }
        return settings;
    }
}
