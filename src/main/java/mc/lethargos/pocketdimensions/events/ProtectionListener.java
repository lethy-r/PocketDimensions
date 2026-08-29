package mc.lethargos.pocketdimensions.events;

import mc.lethargos.pocketdimensions.PocketDimensions;
import mc.lethargos.pocketdimensions.managers.TrustManager;
import mc.lethargos.pocketdimensions.storage.TrustTier;
import mc.lethargos.pocketdimensions.utils.MessageUtils;
import mc.lethargos.pocketdimensions.utils.WorldUtils;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerUnleashEntityEvent;
import org.bukkit.entity.ChestedHorse;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Stops untrusted players from griefing someone else's pocket dimension.
 * Owners and players with pocketdimensions.bypass.protection are exempt;
 * everyone else needs at least BUILDER trust to build, open containers,
 * ignite fire, manipulate entities or harm the owner's mobs.
 *
 * Unparseable pocket-dimension worlds (name matches but no valid owner UUID)
 * fail closed: everyone but bypass holders is treated as a visitor.
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
        extraContainers = new ArrayList<>();
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

    private boolean active(World world) {
        return featureEnabled() && WorldUtils.isPocketWorld(world);
    }

    /**
     * Resolves a player's effective access inside a pocket world. A null owner
     * (malformed world name) deliberately resolves to VISITOR for everyone so
     * protection fails closed instead of wide open.
     */
    private TrustTier effectiveTier(Player player, UUID owner) {
        if (player.hasPermission("pocketdimensions.bypass.protection")) {
            return TrustTier.TRUSTED;
        }
        if (owner != null && player.getUniqueId().equals(owner)) {
            return TrustTier.TRUSTED;
        }
        if (owner == null) {
            return TrustTier.VISITOR;
        }
        TrustTier tier = trustManager.getTier(owner, player.getUniqueId());
        return tier != null ? tier : TrustTier.VISITOR;
    }

    private static boolean canBuild(TrustTier tier) {
        return tier == TrustTier.BUILDER || tier == TrustTier.TRUSTED;
    }

    /** Resolves the player behind a damage source (direct hit or projectile shooter). */
    private Player resolvingDamager(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!active(player.getWorld())) {
            return;
        }
        if (canBuild(effectiveTier(player, WorldUtils.ownerUuidOf(player.getWorld())))) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage(MessageUtils.getMessage("protection.denied-build"));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!active(player.getWorld())) {
            return;
        }
        if (canBuild(effectiveTier(player, WorldUtils.ownerUuidOf(player.getWorld())))) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage(MessageUtils.getMessage("protection.denied-build"));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        if (!active(player.getWorld())) {
            return;
        }
        if (canBuild(effectiveTier(player, WorldUtils.ownerUuidOf(player.getWorld())))) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage(MessageUtils.getMessage("protection.denied-build"));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        if (!active(player.getWorld())) {
            return;
        }
        if (canBuild(effectiveTier(player, WorldUtils.ownerUuidOf(player.getWorld())))) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage(MessageUtils.getMessage("protection.denied-build"));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        if (!active(player.getWorld())) {
            return;
        }
        UUID owner = WorldUtils.ownerUuidOf(player.getWorld());
        TrustTier tier = effectiveTier(player, owner);
        if (canBuild(tier)) {
            return;
        }
        // Farmland trampling
        if (action == Action.PHYSICAL && block != null && block.getType() == Material.FARMLAND) {
            event.setCancelled(true);
            return;
        }
        if (action != Action.RIGHT_CLICK_BLOCK || block == null) {
            return;
        }
        if (!isContainer(block.getType())) {
            return; // Doors, buttons, crafting tables etc. stay usable.
        }
        event.setCancelled(true);
        player.sendMessage(MessageUtils.getMessage("protection.denied-container"));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!plugin.getConfig().getBoolean("protection.protect-mobs", true)) {
            return;
        }
        if (event.getEntity() instanceof Player) {
            return;
        }
        Player damager = resolvingDamager(event);
        if (damager == null || !active(damager.getWorld())) {
            return;
        }
        TrustTier tier = effectiveTier(damager, WorldUtils.ownerUuidOf(damager.getWorld()));
        if (tier != TrustTier.VISITOR) {
            return; // Builders and above may defend themselves against spawned mobs.
        }
        event.setCancelled(true);
        damager.sendMessage(MessageUtils.getMessage("protection.denied-mobs"));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (!plugin.getConfig().getBoolean("protection.block-fire", true) || !active(event.getBlock().getWorld())) {
            return;
        }
        Player igniter = event.getPlayer();
        if (igniter != null && canBuild(effectiveTier(igniter, WorldUtils.ownerUuidOf(event.getBlock().getWorld())))) {
            return;
        }
        // Player-caused fire by visitors and all non-player ignition (spread,
        // lava, lightning, fireballs) never starts inside a pocket dimension.
        event.setCancelled(true);
        if (igniter != null) {
            igniter.sendMessage(MessageUtils.getMessage("protection.denied-build"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (!plugin.getConfig().getBoolean("protection.block-fire", true) || !active(event.getBlock().getWorld())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        Player player = event.getPlayer();
        if (player == null || !active(player.getWorld())) {
            return;
        }
        if (canBuild(effectiveTier(player, WorldUtils.ownerUuidOf(player.getWorld())))) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage(MessageUtils.getMessage("protection.denied-build"));
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        Player remover = null;
        if (event.getRemover() instanceof Player player) {
            remover = player;
        } else if (event.getRemover() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            remover = shooter;
        }
        if (remover == null || !active(remover.getWorld())) {
            return;
        }
        if (canBuild(effectiveTier(remover, WorldUtils.ownerUuidOf(remover.getWorld())))) {
            return;
        }
        event.setCancelled(true);
        remover.sendMessage(MessageUtils.getMessage("protection.denied-build"));
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeash(PlayerLeashEntityEvent event) {
        Player player = event.getPlayer();
        if (!active(player.getWorld())) {
            return;
        }
        if (canBuild(effectiveTier(player, WorldUtils.ownerUuidOf(player.getWorld())))) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onUnleash(PlayerUnleashEntityEvent event) {
        Player player = event.getPlayer();
        if (player == null || !active(player.getWorld())) {
            return;
        }
        if (canBuild(effectiveTier(player, WorldUtils.ownerUuidOf(player.getWorld())))) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent event) {
        Player player = event.getPlayer();
        if (!active(player.getWorld())) {
            return;
        }
        if (canBuild(effectiveTier(player, WorldUtils.ownerUuidOf(player.getWorld())))) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage(MessageUtils.getMessage("protection.denied-build"));
    }

    /**
     * Blocks item theft and entity manipulation by visitors: item frames and
     * paintings (item removal/rotation), armor stands (equipment, breaking),
     * leashed knots, and portable inventories (chested horses, chest/
     * hopper minecarts, chest boats).
     */
    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!active(player.getWorld())) {
            return;
        }
        if (!isProtectedEntity(event.getRightClicked())) {
            return;
        }
        if (canBuild(effectiveTier(player, WorldUtils.ownerUuidOf(player.getWorld())))) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage(MessageUtils.getMessage("protection.denied-container"));
    }

    private boolean isProtectedEntity(org.bukkit.entity.Entity entity) {
        String type = entity.getType().name();
        switch (type) {
            case "ITEM_FRAME", "GLOW_ITEM_FRAME", "PAINTING", "ARMOR_STAND", "LEASH_KNOT",
                 "CHEST_MINECART", "HOPPER_MINECART", "CHEST_BOAT" -> {
                return true;
            }
            default -> {
                return entity instanceof ChestedHorse horse && horse.isCarryingChest();
            }
        }
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
