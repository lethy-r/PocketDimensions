package mc.lethargos.pocketdimensions.events;

import mc.lethargos.pocketdimensions.PocketDimensions;
import mc.lethargos.pocketdimensions.managers.TrustManager;
import mc.lethargos.pocketdimensions.storage.TrustTier;
import mc.lethargos.pocketdimensions.utils.MessageUtils;
import mc.lethargos.pocketdimensions.utils.WorldUtils;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.List;
import java.util.UUID;

/**
 * Stops untrusted players from griefing someone else's pocket dimension.
 * Owners and players with pocketdimensions.bypass.protection are exempt;
 * everyone else needs at least BUILDER trust to build or open containers.
 */
public class ProtectionListener implements Listener {

    private final PocketDimensions plugin;
    private final TrustManager trustManager;
    private List<Material> extraContainers;

    public ProtectionListener(PocketDimensions plugin, TrustManager trustManager) {
        this.plugin = plugin;
        this.trustManager = trustManager;
    }

    /** Re-reads the configured extra container materials (called on /pdreload). */
    public void reloadConfig() {
        extraContainers = new java.util.ArrayList<>();
        for (String name : plugin.getConfig().getStringList("protection.extra-containers")) {
            Material material = Material.matchMaterial(name);
            if (material != null) {
                extraContainers.add(material);
            }
        }
    }

    private boolean featureEnabled() {
        return plugin.getConfig().getBoolean("features.trust-protection", true);
    }

    private TrustTier effectiveTier(Player player, UUID owner) {
        if (player.getUniqueId().equals(owner) || player.hasPermission("pocketdimensions.bypass.protection")) {
            return TrustTier.TRUSTED;
        }
        TrustTier tier = trustManager.getTier(owner, player.getUniqueId());
        return tier != null ? tier : TrustTier.VISITOR;
    }

    private boolean canBuild(TrustTier tier) {
        return tier == TrustTier.BUILDER || tier == TrustTier.TRUSTED;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!featureEnabled() || !WorldUtils.isPocketWorld(player.getWorld())) {
            return;
        }
        UUID owner = WorldUtils.ownerUuidOf(player.getWorld());
        if (owner == null || canBuild(effectiveTier(player, owner))) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage(MessageUtils.getMessage("protection.denied-build"));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!featureEnabled() || !WorldUtils.isPocketWorld(player.getWorld())) {
            return;
        }
        UUID owner = WorldUtils.ownerUuidOf(player.getWorld());
        if (owner == null || canBuild(effectiveTier(player, owner))) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage(MessageUtils.getMessage("protection.denied-build"));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        if (!featureEnabled() || !WorldUtils.isPocketWorld(player.getWorld())) {
            return;
        }
        UUID owner = WorldUtils.ownerUuidOf(player.getWorld());
        if (owner == null || canBuild(effectiveTier(player, owner))) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage(MessageUtils.getMessage("protection.denied-build"));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        if (!featureEnabled() || !WorldUtils.isPocketWorld(player.getWorld())) {
            return;
        }
        UUID owner = WorldUtils.ownerUuidOf(player.getWorld());
        if (owner == null || canBuild(effectiveTier(player, owner))) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage(MessageUtils.getMessage("protection.denied-build"));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        if (!featureEnabled() || !WorldUtils.isPocketWorld(player.getWorld())) {
            return;
        }
        Block block = event.getClickedBlock();
        if (!isContainer(block.getType())) {
            return; // Doors, buttons, crafting tables etc. stay usable.
        }
        UUID owner = WorldUtils.ownerUuidOf(player.getWorld());
        if (owner == null || canBuild(effectiveTier(player, owner))) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage(MessageUtils.getMessage("protection.denied-container"));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!plugin.getConfig().getBoolean("protection.protect-mobs", true)) {
            return;
        }
        if (!(event.getDamager() instanceof Player player) || event.getEntity() instanceof Player) {
            return;
        }
        if (!featureEnabled() || !WorldUtils.isPocketWorld(player.getWorld())) {
            return;
        }
        UUID owner = WorldUtils.ownerUuidOf(player.getWorld());
        if (owner == null) {
            return;
        }
        TrustTier tier = effectiveTier(player, owner);
        if (tier != TrustTier.VISITOR) {
            return; // Builders and above may defend themselves against spawned mobs.
        }
        event.setCancelled(true);
        player.sendMessage(MessageUtils.getMessage("protection.denied-mobs"));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.getEntity() == null
                || !plugin.getConfig().getBoolean("protection.block-explosions", true)
                || !WorldUtils.isPocketWorld(event.getEntity().getWorld())) {
            return;
        }
        event.blockList().clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!plugin.getConfig().getBoolean("protection.block-explosions", true)
                || !WorldUtils.isPocketWorld(event.getBlock().getWorld())) {
            return;
        }
        event.blockList().clear();
    }

    private boolean isContainer(Material material) {
        String name = material.name();
        if (name.equals("ENDER_CHEST")) {
            return false; // Personal storage, always allowed.
        }
        if (extraContainers != null && extraContainers.contains(material)) {
            return true;
        }
        return name.contains("CHEST") || name.contains("BARREL") || name.contains("HOPPER")
                || name.contains("DISPENSER") || name.contains("DROPPER") || name.contains("FURNACE")
                || name.contains("SHULKER") || name.contains("BREWING") || name.equals("LECTERN")
                || name.equals("JUKEBOX") || name.equals("DECORATED_POT") || name.equals("COMPOSTER");
    }
}
