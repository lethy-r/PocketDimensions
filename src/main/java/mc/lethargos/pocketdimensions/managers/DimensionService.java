package mc.lethargos.pocketdimensions.managers;

import mc.lethargos.pocketdimensions.PocketDimensions;
import mc.lethargos.pocketdimensions.api.event.PlayerEnterDimensionEvent;
import mc.lethargos.pocketdimensions.api.event.PlayerLeaveDimensionEvent;
import mc.lethargos.pocketdimensions.api.event.PocketDimensionCreateEvent;
import mc.lethargos.pocketdimensions.generators.VoidChunkGenerator;
import mc.lethargos.pocketdimensions.storage.DimensionSettings;
import mc.lethargos.pocketdimensions.utils.Feedback;
import mc.lethargos.pocketdimensions.utils.GameRuleUtils;
import mc.lethargos.pocketdimensions.utils.MessageUtils;
import mc.lethargos.pocketdimensions.utils.WorldUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Map;
import java.util.UUID;

/**
 * Owns entering and leaving a player's own pocket dimension, plus the world
 * lifecycle: loading existing worlds (with the right generator for their
 * preset) and creating brand-new ones (economy charge + create event fire
 * only when the world genuinely does not exist yet).
 */
public class DimensionService {

    private final PocketDimensions plugin;
    private final LocationManager locationManager;
    private final BorderManager borderManager;
    private final SettingsManager settingsManager;
    private final EconomyManager economyManager;

    public DimensionService(PocketDimensions plugin, LocationManager locationManager, BorderManager borderManager,
                            SettingsManager settingsManager, EconomyManager economyManager) {
        this.plugin = plugin;
        this.locationManager = locationManager;
        this.borderManager = borderManager;
        this.settingsManager = settingsManager;
        this.economyManager = economyManager;
    }

    /** @return false when the entry was cancelled or the world could not be created. */
    public boolean enterOwnDimension(Player player) {
        UUID owner = player.getUniqueId();
        World world = ensureDimension(player);
        if (world == null) {
            return false; // ensureDimension already reported the failure.
        }

        PlayerEnterDimensionEvent event = new PlayerEnterDimensionEvent(player, owner, world);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }

        // Save the "return to" location only when leaving a non-PD world;
        // saving inside someone else's dimension would corrupt the return point.
        if (!WorldUtils.isPocketWorld(player.getWorld())) {
            locationManager.saveLastLocation(player, false);
        }

        applyBorderAndRules(world, owner);

        player.sendMessage(MessageUtils.getMessage("dimension.teleporting"));

        Location target = locationManager.getLastLocation(player, true);
        if (target == null || target.getWorld() != world) {
            target = world.getSpawnLocation();
        }
        if (!player.teleport(target)) {
            player.sendMessage(MessageUtils.getMessage("dimension.teleport-failed"));
            return false;
        }
        Feedback.enter(plugin, player, player.getName());
        return true;
    }

    public boolean leaveOwnDimension(Player player) {
        World current = player.getWorld();
        if (!WorldUtils.isPocketWorld(current)) {
            return false;
        }
        UUID owner = WorldUtils.ownerUuidOf(current);
        if (owner == null) {
            return false;
        }

        PlayerLeaveDimensionEvent event = new PlayerLeaveDimensionEvent(player, owner, current);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }

        locationManager.saveLastLocation(player, true);
        player.sendMessage(MessageUtils.getMessage("dimension.returning"));

        Location last = locationManager.getLastLocation(player, false);
        World main = WorldUtils.getMainWorld();
        Location target = last != null ? last : (main != null ? main.getSpawnLocation() : null);
        if (target == null) {
            return false;
        }
        if (!player.teleport(target)) {
            player.sendMessage(MessageUtils.getMessage("dimension.teleport-failed"));
            return false;
        }
        Feedback.leave(plugin, player);
        return true;
    }

    /**
     * Loads the player's dimension from disk if it exists, or creates a brand
     * new one. Only a genuinely new world (no folder on disk) triggers the
     * economy charge and the create event; reloading an unloaded world is
     * free and silent.
     *
     * @return null (with a message sent to the player) when creation failed or was cancelled.
     */
    public World ensureDimension(Player player) {
        UUID owner = player.getUniqueId();
        World world = WorldUtils.findLoadedPocketWorld(owner);
        if (world != null) {
            return world;
        }

        boolean freshCreation = !WorldUtils.existingWorldFolder(owner);
        if (freshCreation) {
            PocketDimensionCreateEvent event = new PocketDimensionCreateEvent(owner, player.getName(),
                    WorldUtils.pocketWorldName(owner), resolvePreset(owner), resolveEnvironment(owner).name());
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                player.sendMessage(MessageUtils.getMessage("dimension.creation-failed"));
                return null;
            }
            if (!economyManager.chargeCreation(player)) {
                return null;
            }
        }

        player.sendMessage(MessageUtils.getMessage("dimension.loading"));

        WorldCreator creator = freshCreation ? newCreator(owner) : reloadCreator(owner);
        world = Bukkit.createWorld(creator);
        if (world == null) {
            plugin.getLogger().severe("Failed to create the pocket dimension at " + WorldUtils.pocketWorldName(owner));
            if (freshCreation) {
                economyManager.refundCreation(player);
            }
            player.sendMessage(MessageUtils.getMessage("dimension.creation-failed"));
            return null;
        }
        if (freshCreation) {
            String preset = resolvePreset(owner);
            switch (preset) {
                case "VOID" -> world.setSpawnLocation(8, VoidChunkGenerator.PLATFORM_Y + 1, 8);
                case "NORMAL" -> world.setSpawnLocation(0, world.getHighestBlockYAt(0, 0) + 1, 0);
                default -> world.setSpawnLocation(0, -60, 0);
            }
            plugin.getLogger().info("Created pocket dimension '" + world.getName() + "' (preset " + preset
                    + ", environment " + resolveEnvironment(owner).name() + ").");
        } else {
            plugin.getLogger().info("Loaded pocket dimension '" + world.getName() + "'.");
        }
        return world;
    }

    /**
     * Loads an existing dimension world from disk if present, applying the
     * owner's stored preset generator. Crucially for VOID worlds, Bukkit does
     * not persist plugin ChunkGenerators, so the generator must be re-attached
     * on every load - otherwise new chunks come out as vanilla terrain.
     *
     * @return the loaded world, or null when it is not loaded and has no folder.
     */
    public World loadWorld(UUID owner) {
        World world = WorldUtils.findLoadedPocketWorld(owner);
        if (world != null) {
            return world;
        }
        if (!WorldUtils.existingWorldFolder(owner)) {
            return null;
        }
        WorldCreator creator = new WorldCreator(WorldUtils.pocketWorldName(owner));
        if ("VOID".equals(resolvePreset(owner))) {
            creator.generator(new VoidChunkGenerator());
        }
        world = Bukkit.createWorld(creator);
        if (world != null) {
            plugin.getLogger().info("Loaded pocket dimension '" + world.getName() + "'.");
        }
        return world;
    }

    /** Full creator for a brand-new world: type, seed, environment and generator from settings/config. */
    private WorldCreator newCreator(UUID owner) {
        WorldCreator creator = new WorldCreator(WorldUtils.pocketWorldName(owner))
                .generateStructures(false)
                .environment(resolveEnvironment(owner));
        switch (resolvePreset(owner)) {
            case "VOID" -> creator.generator(new VoidChunkGenerator());
            case "NORMAL" -> creator.type(WorldType.NORMAL); // random seed
            default -> creator.type(WorldType.FLAT).seed(1);
        }
        return creator;
    }

    /** Creator for loading an existing folder: only the plugin generator is re-attached; the rest comes from level.dat. */
    private WorldCreator reloadCreator(UUID owner) {
        WorldCreator creator = new WorldCreator(WorldUtils.pocketWorldName(owner));
        if ("VOID".equals(resolvePreset(owner))) {
            creator.generator(new VoidChunkGenerator());
        }
        return creator;
    }

    /** Config default gamerules, then the owner's stored per-dimension overrides. */
    public void applyBorderAndRules(World world, UUID owner) {
        WorldBorder worldBorder = world.getWorldBorder();
        worldBorder.setCenter(0, 0);
        Integer customSize = borderManager.getBorderSize(owner);
        int size = (customSize != null) ? customSize : plugin.getConfig().getInt("default-world-border-size", 10000);
        worldBorder.setSize(size);

        GameRuleUtils.applyDefaultRules(world, plugin.getConfig().getConfigurationSection("default-gamerules"));
        DimensionSettings settings = settingsManager.get(owner);
        for (Map.Entry<String, String> entry : settings.getGameruleOverrides().entrySet()) {
            String value = entry.getValue();
            if (value.equalsIgnoreCase("true")) {
                GameRuleUtils.setRule(world, entry.getKey(), true);
            } else if (value.equalsIgnoreCase("false")) {
                GameRuleUtils.setRule(world, entry.getKey(), false);
            } else {
                try {
                    GameRuleUtils.setRule(world, entry.getKey(), Integer.parseInt(value));
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    private String resolvePreset(UUID owner) {
        DimensionSettings settings = settingsManager.get(owner);
        String value = settings.getPreset() != null
                ? settings.getPreset()
                : plugin.getConfig().getString("dimension.default-preset", "FLAT");
        return value == null ? "FLAT" : value.trim().toUpperCase();
    }

    private World.Environment resolveEnvironment(UUID owner) {
        DimensionSettings settings = settingsManager.get(owner);
        String value = settings.getEnvironment() != null
                ? settings.getEnvironment()
                : plugin.getConfig().getString("dimension.default-environment", "NORMAL");
        if (value != null) {
            try {
                return World.Environment.valueOf(value.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }
        return World.Environment.NORMAL;
    }
}
