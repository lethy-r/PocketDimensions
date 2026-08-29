package mc.lethargos.pocketdimensions.managers;

import mc.lethargos.pocketdimensions.PocketDimensions;
import mc.lethargos.pocketdimensions.utils.MessageUtils;
import mc.lethargos.pocketdimensions.utils.WorldUtils;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Optional Vault economy hook. Disabled unless economy.enabled is true AND a
 * Vault economy provider is registered; every feature degrades to free when
 * it is not active.
 */
public class EconomyManager {

    public record UpgradeTier(int size, double cost) {
    }

    private final PocketDimensions plugin;
    private final BorderManager borderManager;
    private Economy economy;

    public EconomyManager(PocketDimensions plugin, BorderManager borderManager) {
        this.plugin = plugin;
        this.borderManager = borderManager;
    }

    /** (Re)resolves the Vault economy provider. Safe to call on reload. */
    public void setup() {
        economy = null;
        if (!plugin.getConfig().getBoolean("economy.enabled", false)) {
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            warnOnce("economy.enabled is true but Vault is not installed; economy features are disabled.");
            return;
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            warnOnce("economy.enabled is true but no economy plugin is registered with Vault; economy features are disabled.");
            return;
        }
        economy = rsp.getProvider();
        plugin.getLogger().info("Vault economy hooked: " + economy.getName());
    }

    public boolean isEnabled() {
        return economy != null;
    }

    /** Charges the one-time dimension creation cost. @return false when the player cannot pay. */
    public boolean chargeCreation(Player player) {
        return charge(player, plugin.getConfig().getDouble("economy.creation-cost", 0.0),
                "economy.insufficient-funds-creation", null);
    }

    /** Deposits the creation cost back when world creation failed after charging. */
    public void refundCreation(Player player) {
        double cost = plugin.getConfig().getDouble("economy.creation-cost", 0.0);
        if (cost <= 0 || !isEnabled()) {
            return;
        }
        economy.depositPlayer(player, cost);
        plugin.getLogger().info("Refunded " + economy.format(cost) + " creation cost to "
                + player.getName() + " (world creation failed).");
    }

    /**
     * Moves the player's border to the next configured tier, charging when the
     * economy is active. Always sends the player a response message.
     */
    public void upgrade(Player player) {
        int current = currentBorderSize(player);
        UpgradeTier next = nextTier(current);
        if (next == null) {
            player.sendMessage(MessageUtils.getMessage("pd.upgrade.max"));
            return;
        }
        if (!charge(player, next.cost(), "economy.insufficient-funds-upgrade", "pd.upgrade.charged")) {
            return;
        }
        borderManager.setBorderSize(player.getUniqueId(), next.size());
        World world = Bukkit.getWorld(WorldUtils.pocketWorldName(player.getUniqueId()));
        if (world != null) {
            world.getWorldBorder().setSize(next.size());
        }
        player.sendMessage(MessageUtils.getMessage("pd.upgrade.success")
                .replace("%size%", String.valueOf(next.size())));
    }

    public UpgradeTier nextTier(int currentSize) {
        List<UpgradeTier> tiers = tiers();
        for (UpgradeTier tier : tiers) {
            if (tier.size() > currentSize) {
                return tier;
            }
        }
        return null;
    }

    private int currentBorderSize(Player player) {
        Integer stored = borderManager.getBorderSize(player.getUniqueId());
        return stored != null ? stored : plugin.getConfig().getInt("default-world-border-size", 10000);
    }

    private List<UpgradeTier> tiers() {
        List<UpgradeTier> result = new ArrayList<>();
        for (Map<?, ?> entry : plugin.getConfig().getMapList("economy.upgrades")) {
            Object size = entry.get("size");
            Object cost = entry.get("cost");
            if (size instanceof Number) {
                result.add(new UpgradeTier(((Number) size).intValue(),
                        cost instanceof Number ? ((Number) cost).doubleValue() : 0.0));
            }
        }
        result.sort((a, b) -> Integer.compare(a.size(), b.size()));
        return result;
    }

    /** @return false (and messages the player) when the player cannot pay. */
    private boolean charge(Player player, double amount, String insufficientKey, String chargedKey) {
        if (amount <= 0) {
            return true;
        }
        if (!isEnabled()) {
            return true; // Economy off = everything is free.
        }
        if (economy.getBalance(player) < amount) {
            player.sendMessage(MessageUtils.getMessage(insufficientKey).replace("%cost%", economy.format(amount)));
            return false;
        }
        if (!economy.withdrawPlayer(player, amount).transactionSuccess()) {
            player.sendMessage(MessageUtils.getMessage("economy.transaction-failed"));
            return false;
        }
        if (chargedKey != null) {
            player.sendMessage(MessageUtils.getMessage(chargedKey).replace("%cost%", economy.format(amount)));
        }
        return true;
    }

    private void warnOnce(String message) {
        plugin.getLogger().warning(message);
    }
}
