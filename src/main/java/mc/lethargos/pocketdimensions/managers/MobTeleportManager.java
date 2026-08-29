package mc.lethargos.pocketdimensions.managers;

import mc.lethargos.pocketdimensions.PocketDimensions;
import mc.lethargos.pocketdimensions.api.event.MobTeleportEvent;
import mc.lethargos.pocketdimensions.utils.MessageUtils;
import mc.lethargos.pocketdimensions.utils.WorldUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.logging.Level;

public class MobTeleportManager {

    private final PocketDimensions plugin;
    private final LocationManager locationManager;
    private boolean enabled;
    private String mode;
    private Set<EntityType> entityList;
    private Set<String> excludedCategories;
    private boolean allowNamedMobs;
    private boolean separateToolRequired;

    public MobTeleportManager(PocketDimensions plugin, LocationManager locationManager) {
        this.plugin = plugin;
        this.locationManager = locationManager;
        reloadConfig();
    }

    public boolean isSeparateToolRequired() {
        return separateToolRequired;
    }

    public void reloadConfig() {
        ConfigurationSection config = plugin.getConfig().getConfigurationSection("mob-teleport");
        if (config == null) {
            enabled = false;
            return;
        }

        enabled = config.getBoolean("enabled", false);
        mode = config.getString("mode", "BLACKLIST").toUpperCase();
        allowNamedMobs = config.getBoolean("allow-named-mobs", true);
        separateToolRequired = plugin.getConfig().getBoolean("tools.mob-teleporter.required", false);

        entityList = new HashSet<>();
        List<String> entities = config.getStringList("entities");
        for (String entityName : entities) {
            try {
                EntityType type = EntityType.valueOf(entityName.toUpperCase());
                entityList.add(type);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().log(Level.WARNING, "Invalid entity type in mob-teleport config: " + entityName);
            }
        }

        excludedCategories = new HashSet<>();
        List<String> categories = config.getStringList("exclude.categories");
        for (String category : categories) {
            excludedCategories.add(category.toUpperCase());
        }
    }

    public boolean canTeleport(Entity entity) {
        if (!enabled) return false;
        if (!(entity instanceof LivingEntity)) return false;
        if (entity instanceof Player) return false;

        // Check excluded categories first
        if (isExcludedCategory(entity)) return false;

        // Check if named mobs are allowed
        if (!allowNamedMobs && entity.getCustomName() != null) {
             return false;
        }

        EntityType type = entity.getType();

        switch (mode) {
            case "ALL":
                return true;
            case "WHITELIST":
                return entityList.contains(type);
            case "BLACKLIST":
                return !entityList.contains(type);
            default:
                return false;
        }
    }

    private boolean isExcludedCategory(Entity entity) {
        // Simple category checks based on entity properties
        if (excludedCategories.contains("BOSS") && isBoss(entity)) return true;
        if (excludedCategories.contains("PLAYER") && entity instanceof Player) return true; // Redundant but safe
        // Add more categories if needed
        return false;
    }

    private boolean isBoss(Entity entity) {
        // A simple heuristic for bosses. Can be expanded.
        EntityType type = entity.getType();
        return type == EntityType.ENDER_DRAGON || type == EntityType.WITHER || type == EntityType.WARDEN;
    }

    public void tryTeleportMob(Player player, Entity entity) {
        if (!player.hasPermission("pocketdimensions.mobteleport.use")) {
            player.sendMessage(MessageUtils.getMessage("no-permission"));
            return;
        }

        if (!canTeleport(entity)) {
            player.sendMessage(MessageUtils.getMessage("mob-teleport.denied"));
            return;
        }

        World pdWorld = Bukkit.getWorld(WorldUtils.pocketWorldName(player.getUniqueId()));
        if (pdWorld == null) {
            pdWorld = Bukkit.getWorld(WorldUtils.PD_NAME_MARKER + player.getUniqueId().toString());
        }

        if (pdWorld == null) {
            player.sendMessage(MessageUtils.getMessage("mob-teleport.dimension-error"));
            return;
        }
        
        boolean inPD = entity.getWorld().equals(pdWorld);

        Location targetLoc;
        boolean extracting = false;

        if (inPD) {
            if (!player.hasPermission("pocketdimensions.mobteleport.retrieve")) {
                player.sendMessage(MessageUtils.getMessage("no-permission"));
                return;
            }
            // Extracting mob from PD
            targetLoc = locationManager.getLastLocation(player, false);
            if (targetLoc == null) {
                 // Fallback to main world spawn
                 World main = WorldUtils.getMainWorld();
                 if (main != null) targetLoc = main.getSpawnLocation();
            }
            
            if (targetLoc == null) {
                player.sendMessage(MessageUtils.getMessage("mob-teleport.no-return-location"));
                return;
            }
            extracting = true;
        } else {
            if (!player.hasPermission("pocketdimensions.mobteleport.store")) {
                player.sendMessage(MessageUtils.getMessage("no-permission"));
                return;
            }
            // Sending mob TO PD
            targetLoc = locationManager.getLastLocation(player, true);
            if (targetLoc == null) {
                targetLoc = pdWorld.getSpawnLocation();
            }
        }
        
        MobTeleportEvent teleportEvent = new MobTeleportEvent(player, entity,
                extracting ? MobTeleportEvent.Direction.RETRIEVE : MobTeleportEvent.Direction.STORE);
        Bukkit.getPluginManager().callEvent(teleportEvent);
        if (teleportEvent.isCancelled()) {
            return;
        }

        if (entity.teleport(targetLoc)) {
            String entityName = (entity.getCustomName() != null) ? entity.getCustomName() : entity.getName();
            String key = extracting ? "mob-teleport.extract-success" : "mob-teleport.success";
            String msg = MessageUtils.getMessage(key).replace("%entity%", entityName);
            player.sendMessage(msg);
        } else {
             // Teleport failed (maybe event cancelled)
        }
    }
}