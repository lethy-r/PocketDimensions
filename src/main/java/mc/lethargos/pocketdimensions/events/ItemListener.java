package mc.lethargos.pocketdimensions.events;

import mc.lethargos.pocketdimensions.PocketDimensions;
import mc.lethargos.pocketdimensions.storage.Storage;
import mc.lethargos.pocketdimensions.utils.DimensionKeyItem;
import mc.lethargos.pocketdimensions.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;

/**
 * Item lifecycle features: first-join key handout and crafting a key
 * (the crafted blank key binds to the crafter).
 */
public class ItemListener implements Listener {

    private static final String KEY_GIVEN_FLAG = "key_given";

    private final PocketDimensions plugin;
    private final Storage storage;

    public ItemListener(PocketDimensions plugin, Storage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getConfig().getBoolean("key-item.give-on-first-join", false)) {
            return;
        }
        if (!player.hasPermission("pocketdimensions.receive.key")) {
            return;
        }
        if (storage.getMeta(player.getUniqueId(), KEY_GIVEN_FLAG) != null) {
            return;
        }
        storage.setMeta(player.getUniqueId(), KEY_GIVEN_FLAG, "true");
        var overflow = player.getInventory().addItem(DimensionKeyItem.createDimensionKey(plugin, player.getUniqueId(), player.getName()));
        // Never lose the key to a full inventory: drop whatever did not fit.
        overflow.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        player.sendMessage(MessageUtils.getMessage("givepd.item-given").replace("%player%", player.getName()));
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result == null || !result.hasItemMeta()) {
            return;
        }
        ItemMeta meta = result.getItemMeta();
        if (meta == null
                || !meta.getPersistentDataContainer().has(DimensionKeyItem.TYPE_KEY, PersistentDataType.STRING)
                || !"dimension_blank".equals(meta.getPersistentDataContainer()
                        .get(DimensionKeyItem.TYPE_KEY, PersistentDataType.STRING))) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player crafter)) {
            return;
        }
        // Bind the blank key to the crafter.
        event.getInventory().setResult(DimensionKeyItem.createDimensionKey(plugin, crafter.getUniqueId(), crafter.getName()));
    }

    /** Registers the shaped crafting recipe when enabled in config. */
    public static void registerRecipe(PocketDimensions plugin) {
        if (!plugin.getConfig().getBoolean("key-item.craftable", false)) {
            return;
        }
        java.util.List<String> shape = plugin.getConfig().getStringList("key-item.recipe.shape");
        if (shape.size() != 3) {
            plugin.getLogger().warning("key-item.recipe.shape must have exactly 3 rows; recipe not registered.");
            return;
        }
        int width = shape.get(0).length();
        for (String row : shape) {
            if (row.isEmpty() || row.length() != width || row.length() > 3) {
                plugin.getLogger().warning("key-item.recipe.shape rows must be equal length (max 3 characters); recipe not registered.");
                return;
            }
        }
        ShapedRecipe recipe = new ShapedRecipe(new org.bukkit.NamespacedKey(plugin, "dimension_key"),
                DimensionKeyItem.createBlankDimensionKey(plugin));
        try {
            recipe.shape(shape.get(0), shape.get(1), shape.get(2));
            Map<Character, org.bukkit.Material> seen = new HashMap<>();
            for (Map<?, ?> entry : plugin.getConfig().getMapList("key-item.recipe.ingredients")) {
                Object key = entry.get("key");
                Object material = entry.get("material");
                if (key == null || material == null || String.valueOf(key).length() != 1) {
                    continue;
                }
                char c = String.valueOf(key).charAt(0);
                org.bukkit.Material mat = org.bukkit.Material.matchMaterial(String.valueOf(material));
                if (mat == null || seen.containsKey(c)) {
                    plugin.getLogger().warning("Invalid recipe ingredient: " + material);
                    continue;
                }
                seen.put(c, mat);
                recipe.setIngredient(c, mat);
            }
            // Every non-space character in the shape needs a configured ingredient.
            String joined = String.join("", shape);
            for (char c : joined.toCharArray()) {
                if (c != ' ' && !seen.containsKey(c)) {
                    plugin.getLogger().warning("Recipe shape uses character '" + c + "' with no configured ingredient; recipe not registered.");
                    return;
                }
            }
            Bukkit.addRecipe(recipe);
        } catch (IllegalArgumentException | IllegalStateException e) {
            plugin.getLogger().warning("Could not register key recipe: " + e.getMessage());
        }
    }
}
