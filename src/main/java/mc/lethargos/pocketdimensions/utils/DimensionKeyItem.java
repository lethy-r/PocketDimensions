package mc.lethargos.pocketdimensions.utils;

import mc.lethargos.pocketdimensions.PocketDimensions;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;

/**
 * Builds the plugin's custom items (dimension key, mob teleporter) with
 * config-driven material, name, lore, custom model data and glow. All items
 * carry the owner's UUID in persistent data so ownership checks work.
 */
public final class DimensionKeyItem {

    public static final NamespacedKey PD_KEY = new NamespacedKey("lgppd", "pocketdimension");
    public static final NamespacedKey TYPE_KEY = new NamespacedKey("lgppd", "item_type");

    private DimensionKeyItem() {
    }

    /** The personal dimension key bound to ownerUUID. */
    public static ItemStack createDimensionKey(PocketDimensions plugin, UUID ownerUUID, String ownerName) {
        return build(plugin, "key-item.material", Material.ECHO_SHARD,
                plugin.getConfig().getString("key-item.name", "&d%player%'s Pocket Dimension"),
                plugin.getConfig().getStringList("key-item.lore"),
                plugin.getConfig().getInt("key-item.custom-model-data", 0),
                plugin.getConfig().getBoolean("key-item.glow", false),
                ownerUUID, ownerName, "dimension");
    }

    /** Blank key used as the crafting recipe result; binds to the crafter via ItemListener. */
    public static ItemStack createBlankDimensionKey(PocketDimensions plugin) {
        return build(plugin, "key-item.material", Material.ECHO_SHARD,
                plugin.getConfig().getString("key-item.name", "&d%player%'s Pocket Dimension")
                        .replace("%player%", "Pocket Dimension"),
                plugin.getConfig().getStringList("key-item.lore"),
                plugin.getConfig().getInt("key-item.custom-model-data", 0),
                plugin.getConfig().getBoolean("key-item.glow", false),
                null, null, "dimension_blank");
    }

    public static ItemStack createMobTool(PocketDimensions plugin, UUID ownerUUID, String ownerName) {
        return build(plugin, "tools.mob-teleporter.material", Material.BLAZE_ROD,
                plugin.getConfig().getString("tools.mob-teleporter.name", "&6%player%'s Mob Teleporter"),
                plugin.getConfig().getStringList("tools.mob-teleporter.lore"),
                plugin.getConfig().getInt("tools.mob-teleporter.custom-model-data", 0),
                plugin.getConfig().getBoolean("tools.mob-teleporter.glow", false),
                ownerUUID, ownerName, "mob_teleporter");
    }

    private static ItemStack build(PocketDimensions plugin, String materialKey, Material fallback,
                                   String nameTemplate, List<String> lore, int customModelData, boolean glow,
                                   UUID ownerUUID, String ownerName, String type) {
        Material material = Material.matchMaterial(plugin.getConfig().getString(materialKey, fallback.name()));
        if (material == null) {
            material = fallback;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
                nameTemplate.replace("%player%", ownerName != null ? ownerName : "Pocket Dimension")));
        List<String> coloredLore = new java.util.ArrayList<>();
        for (String line : lore) {
            coloredLore.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        meta.setLore(coloredLore);
        if (customModelData > 0) {
            meta.setCustomModelData(customModelData);
        }
        if (glow) {
            Enchantment glowEnchant = Enchantment.getByName("LUCK");
            if (glowEnchant != null) {
                meta.addEnchant(glowEnchant, 1, true);
            }
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        if (ownerUUID != null) {
            meta.getPersistentDataContainer().set(PD_KEY, PersistentDataType.STRING, ownerUUID.toString());
        }
        meta.getPersistentDataContainer().set(TYPE_KEY, PersistentDataType.STRING, type);
        item.setItemMeta(meta);
        return item;
    }
}
