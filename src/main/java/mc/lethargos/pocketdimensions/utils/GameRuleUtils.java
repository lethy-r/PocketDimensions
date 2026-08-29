package mc.lethargos.pocketdimensions.utils;

import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashSet;
import java.util.Set;

/**
 * Version-tolerant gamerule access. Minecraft 1.21.11 removed the legacy
 * gamerule names (breaking static {@link GameRule} constant references) and
 * 26.x reintroduced them as deprecated aliases; the safest cross-version
 * approach is to never touch the static constants and resolve rules by name
 * at runtime, trying the new name first and the legacy name as fallback.
 */
public final class GameRuleUtils {

    /** Pairs of [new name, legacy name] for rules whose names changed. */
    private static final String[][] ALIAS_PAIRS = {
            {"spawnMobs", "doMobSpawning"},
            {"spawnPatrols", "doPatrolSpawning"},
            {"spawnWanderingTraders", "doTraderSpawning"},
            {"spawnWardens", "doWardenSpawning"},
            {"advanceWeather", "doWeatherCycle"},
            {"immediateRespawn", "doImmediateRespawn"},
    };

    private static final Set<String> WARNED_RULES = new HashSet<>();

    private GameRuleUtils() {
    }

    /**
     * Applies the configured default gamerules (default-gamerules section) to a
     * world. Values must be booleans or integers; unresolvable names are
     * reported once instead of throwing, so a future rename degrades to a log
     * line rather than a crash.
     */
    public static void applyDefaultRules(World world, ConfigurationSection section) {
        if (world == null || section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (value instanceof Boolean) {
                setRule(world, key, (Boolean) value);
            } else if (value instanceof Number) {
                setRule(world, key, ((Number) value).intValue());
            } else {
                warnOnce(key, "unsupported value type " + value.getClass().getSimpleName() + " (use true/false or an integer)");
            }
        }
    }

    public static boolean setRule(World world, String name, boolean value) {
        return setRule(world, name, Boolean.class, value);
    }

    public static boolean setRule(World world, String name, int value) {
        return setRule(world, name, Integer.class, value);
    }

    /**
     * Resolves a gamerule by config name with legacy alias fallback, so
     * commands accept both naming generations on any server version.
     *
     * @return the resolved rule, or null when no candidate exists on this server.
     */
    public static GameRule<?> resolve(String name) {
        for (String candidate : candidateNames(name)) {
            GameRule<?> rule = GameRule.getByName(candidate);
            if (rule != null) {
                return rule;
            }
        }
        return null;
    }

    private static boolean setRule(World world, String name, Class<?> expectedType, Object value) {
        for (String candidate : candidateNames(name)) {
            GameRule<?> rule = GameRule.getByName(candidate);
            if (rule == null || !expectedType.equals(rule.getType())) {
                continue;
            }
            try {
                if (value instanceof Boolean) {
                    // The cast is safe: the rule's type was checked above.
                    @SuppressWarnings("unchecked")
                    GameRule<Boolean> booleanRule = (GameRule<Boolean>) rule;
                    world.setGameRule(booleanRule, (Boolean) value);
                } else {
                    @SuppressWarnings("unchecked")
                    GameRule<Integer> intRule = (GameRule<Integer>) rule;
                    world.setGameRule(intRule, (Integer) value);
                }
                return true;
            } catch (RuntimeException e) {
                // Server rejected the rule despite resolving it; try the next candidate.
            }
        }
        warnOnce(name, "no matching gamerule on this server version");
        return false;
    }

    /** The name itself plus any alias partners, so config may use either naming generation. */
    private static Set<String> candidateNames(String name) {
        Set<String> candidates = new HashSet<>();
        candidates.add(name);
        for (String[] pair : ALIAS_PAIRS) {
            if (pair[0].equalsIgnoreCase(name)) {
                candidates.add(pair[1]);
            } else if (pair[1].equalsIgnoreCase(name)) {
                candidates.add(pair[0]);
            }
        }
        return candidates;
    }

    private static void warnOnce(String rule, String problem) {
        if (WARNED_RULES.add(rule)) {
            System.out.println("[PocketDimensions] Could not apply gamerule '" + rule + "': " + problem + ".");
        }
    }
}
