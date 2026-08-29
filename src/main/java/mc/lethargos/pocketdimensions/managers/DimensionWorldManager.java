package mc.lethargos.pocketdimensions.managers;

import mc.lethargos.pocketdimensions.PocketDimensions;
import mc.lethargos.pocketdimensions.storage.Storage;
import mc.lethargos.pocketdimensions.utils.MessageUtils;
import mc.lethargos.pocketdimensions.utils.WorldUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps loaded pocket dimensions in check: empty dimensions are unloaded
 * (saving first) after a grace period so memory does not grow with the
 * number of players who ever opened a dimension.
 *
 * Players who log out inside a dimension get a meta flag; on rejoin the
 * dimension is reloaded and they are returned to their spot, so an unload
 * under a logged-out player never dumps them at main-world spawn.
 */
public class DimensionWorldManager implements Listener {

    private static final String LOGOUT_FLAG = "logged_out_pd";

    private final PocketDimensions plugin;
    private final Storage storage;
    private final LocationManager locationManager;
    private final Map<String, Long> emptySince = new HashMap<>();

    public DimensionWorldManager(PocketDimensions plugin, Storage storage, LocationManager locationManager) {
        this.plugin = plugin;
        this.storage = storage;
        this.locationManager = locationManager;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("world-management.auto-unload", true)) {
            return;
        }
        long intervalSeconds = Math.max(30, plugin.getConfig().getInt("world-management.sweep-interval-seconds", 300));
        long intervalTicks = intervalSeconds * 20L;
        Bukkit.getScheduler().runTaskTimer(plugin, this::sweep, intervalTicks, intervalTicks);
    }

    private void sweep() {
        long graceMillis = Math.max(0, plugin.getConfig().getInt("world-management.unload-delay-seconds", 300)) * 1000L;
        int entityCap = plugin.getConfig().getInt("world-management.entity-cap", 0);
        for (World world : Bukkit.getWorlds()) {
            if (!WorldUtils.isPocketWorld(world)) {
                continue;
            }
            if (!world.getPlayers().isEmpty()) {
                emptySince.remove(world.getName());
                checkEntityCap(world, entityCap);
                continue;
            }
            long now = System.currentTimeMillis();
            long since = emptySince.computeIfAbsent(world.getName(), key -> now);
            if (now - since < graceMillis) {
                continue;
            }
            emptySince.remove(world.getName());
            if (Bukkit.unloadWorld(world, true)) {
                plugin.getLogger().info("Unloaded empty pocket dimension '" + world.getName() + "'.");
            } else {
                plugin.getLogger().warning("Could not unload pocket dimension '" + world.getName() + "'; will retry.");
            }
        }
    }

    private void checkEntityCap(World world, int cap) {
        if (cap <= 0 || world.getEntities().size() <= cap) {
            return;
        }
        UUID owner = WorldUtils.ownerUuidOf(world);
        Player ownerOnline = owner != null ? Bukkit.getPlayer(owner) : null;
        if (ownerOnline != null) {
            ownerOnline.sendMessage(MessageUtils.getMessage("world-management.entity-cap")
                    .replace("%count%", String.valueOf(world.getEntities().size()))
                    .replace("%cap%", String.valueOf(cap)));
        }
        plugin.getLogger().warning("Pocket dimension '" + world.getName() + "' has "
                + world.getEntities().size() + " entities (cap: " + cap + ").");
    }

    /** Unloads a dimension immediately for admin operations. @return false when players are inside. */
    public boolean unloadIfEmpty(World world) {
        if (!world.getPlayers().isEmpty()) {
            return false;
        }
        emptySince.remove(world.getName());
        return Bukkit.unloadWorld(world, true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (WorldUtils.isPocketWorld(player.getWorld())) {
            storage.setMeta(player.getUniqueId(), LOGOUT_FLAG, player.getWorld().getName());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String worldName = storage.getMeta(player.getUniqueId(), LOGOUT_FLAG);
        if (worldName == null) {
            return;
        }
        storage.clearMeta(player.getUniqueId(), LOGOUT_FLAG);

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
            if (!worldFolder.isDirectory()) {
                return; // Dimension is gone; the player stays in the main world.
            }
            world = Bukkit.createWorld(new WorldCreator(worldName));
        }
        if (world == null) {
            plugin.getLogger().warning("Could not reload pocket dimension '" + worldName + "' for " + player.getName() + ".");
            return;
        }

        Location pdLocation = locationManager.getLastStoredPdLocation(player);
        if (pdLocation != null && pdLocation.getWorld() == world) {
            player.teleport(pdLocation);
        } else {
            player.teleport(world.getSpawnLocation());
        }
        plugin.getLogger().info("Returned " + player.getName() + " to their pocket dimension after rejoin.");
    }
}
