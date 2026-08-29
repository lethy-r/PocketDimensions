package mc.lethargos.pocketdimensions.events;

import mc.lethargos.pocketdimensions.PocketDimensions;
import mc.lethargos.pocketdimensions.managers.DimensionService;
import mc.lethargos.pocketdimensions.managers.MobTeleportManager;
import mc.lethargos.pocketdimensions.utils.WorldUtils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerInteract implements Listener {
    private final PocketDimensions plugin;
    private final MobTeleportManager mobTeleportManager;
    private final DimensionService dimensionService;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private static final NamespacedKey PD_KEY = new NamespacedKey("lgppd", "pocketdimension");
    private static final NamespacedKey TYPE_KEY = new NamespacedKey("lgppd", "item_type");

    public PlayerInteract(PocketDimensions plugin, MobTeleportManager mobTeleportManager, DimensionService dimensionService) {
        this.plugin = plugin;
        this.mobTeleportManager = mobTeleportManager;
        this.dimensionService = dimensionService;
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Entity clickedEntity = event.getRightClicked();

        if (event.getHand() != EquipmentSlot.HAND) return;
        if (isOnCooldown(player.getUniqueId())) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) return;

        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.getPersistentDataContainer().has(PD_KEY, PersistentDataType.STRING)) {
            String pdValue = meta.getPersistentDataContainer().get(PD_KEY, PersistentDataType.STRING);

            // Verify this is the player's own PD item
            if (pdValue != null && pdValue.equals(player.getUniqueId().toString())) {

                // Check if separate tool is required
                if (mobTeleportManager.isSeparateToolRequired()) {
                    String type = null;
                    if (meta.getPersistentDataContainer().has(TYPE_KEY, PersistentDataType.STRING)) {
                        type = meta.getPersistentDataContainer().get(TYPE_KEY, PersistentDataType.STRING);
                    }
                    // Only allow if type is strictly "mob_teleporter"
                    if (!"mob_teleporter".equals(type)) {
                        return; // Ignore if it's the standard item
                    }
                }

                event.setCancelled(true); // Prevent other interactions (like mounting)
                cooldowns.put(player.getUniqueId(), System.currentTimeMillis()); // Prevent double-firing
                mobTeleportManager.tryTeleportMob(player, clickedEntity);
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        UUID playerUUID = p.getUniqueId();

        if (isOnCooldown(playerUUID)) {
            return;
        }

        Action a = event.getAction();
        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (a != Action.RIGHT_CLICK_BLOCK && a != Action.RIGHT_CLICK_AIR) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(PD_KEY, PersistentDataType.STRING)) {
            return;
        }
        String pdValue = meta.getPersistentDataContainer().get(PD_KEY, PersistentDataType.STRING);
        if (pdValue == null || !pdValue.equals(playerUUID.toString())) {
            return; // Items are personal: only the bound owner can use theirs.
        }

        // The mob teleporter tool never opens the dimension
        if (meta.getPersistentDataContainer().has(TYPE_KEY, PersistentDataType.STRING)) {
            String type = meta.getPersistentDataContainer().get(TYPE_KEY, PersistentDataType.STRING);
            if ("mob_teleporter".equals(type)) {
                return;
            }
        }

        cooldowns.put(playerUUID, System.currentTimeMillis());

        UUID worldOwner = WorldUtils.ownerUuidOf(p.getWorld());
        if (p.getUniqueId().equals(worldOwner)) {
            dimensionService.leaveOwnDimension(p);
        } else {
            dimensionService.enterOwnDimension(p);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cooldowns.remove(event.getPlayer().getUniqueId());
    }

    private boolean isOnCooldown(UUID uuid) {
        Long last = cooldowns.get(uuid);
        long cooldownMillis = plugin.getConfig().getLong("key-item.interact-cooldown-ms", 1000);
        return last != null && System.currentTimeMillis() - last < cooldownMillis;
    }
}
