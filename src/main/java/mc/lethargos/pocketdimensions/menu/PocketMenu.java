package mc.lethargos.pocketdimensions.menu;

import mc.lethargos.pocketdimensions.PocketDimensions;
import mc.lethargos.pocketdimensions.managers.BorderManager;
import mc.lethargos.pocketdimensions.managers.DimensionService;
import mc.lethargos.pocketdimensions.managers.EconomyManager;
import mc.lethargos.pocketdimensions.managers.SettingsManager;
import mc.lethargos.pocketdimensions.managers.TrustManager;
import mc.lethargos.pocketdimensions.storage.DimensionSettings;
import mc.lethargos.pocketdimensions.storage.TrustTier;
import mc.lethargos.pocketdimensions.utils.MessageUtils;
import mc.lethargos.pocketdimensions.utils.WorldUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /pd menu - a two-view inventory GUI (main + trust list). Pure presentation
 * over DimensionService / TrustManager / SettingsManager / EconomyManager;
 * every section is permission-gated (pocketdimensions.commands.player.pd.menu.*).
 */
public class PocketMenu implements Listener {

    private static final String PERM_BASE = "pocketdimensions.commands.player.pd.menu";
    private static final String[] PRESETS = {"FLAT", "VOID", "NORMAL"};
    private static final String[] ENVIRONMENTS = {"NORMAL", "NETHER", "THE_END"};

    // Main view slots
    private static final int SLOT_TELEPORT = 10;
    private static final int SLOT_TRUST = 12;
    private static final int SLOT_PRESET = 13;
    private static final int SLOT_ENVIRONMENT = 14;
    private static final int SLOT_BORDER = 15;
    private static final int SLOT_NOTIFY = 16;
    private static final int SLOT_BACK = 22;
    // Trust view slots
    private static final int TRUST_FIRST_SKULL = 0;
    private static final int TRUST_MAX_SKULL = 25;

    private static class Session implements InventoryHolder {
        final Player viewer;
        Inventory inventory;
        boolean trustView;

        Session(Player viewer) {
            this.viewer = viewer;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    private final PocketDimensions plugin;
    private final BorderManager borderManager;
    private final DimensionService dimensionService;
    private final TrustManager trustManager;
    private final SettingsManager settingsManager;
    private final EconomyManager economyManager;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public PocketMenu(PocketDimensions plugin, BorderManager borderManager, DimensionService dimensionService,
                      TrustManager trustManager, SettingsManager settingsManager, EconomyManager economyManager) {
        this.plugin = plugin;
        this.borderManager = borderManager;
        this.dimensionService = dimensionService;
        this.trustManager = trustManager;
        this.settingsManager = settingsManager;
        this.economyManager = economyManager;
    }

    public void open(Player player) {
        Session session = new Session(player);
        session.inventory = Bukkit.createInventory(session, 27,
                MessageUtils.legacy(MessageUtils.getMessage("menu.title")));
        sessions.put(player.getUniqueId(), session);
        rebuild(session);
        player.openInventory(session.inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Session session)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return; // Clicked the player's own inventory.
        }
        if (!event.getWhoClicked().equals(session.viewer)) {
            return;
        }
        handle(session, event.getSlot(), event.isRightClick());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Session) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Session && event.getPlayer() instanceof Player player) {
            sessions.remove(player.getUniqueId());
        }
    }

    private void handle(Session session, int slot, boolean rightClick) {
        Player player = session.viewer;
        if (session.trustView) {
            if (slot == SLOT_BACK) {
                session.trustView = false;
                rebuild(session);
                return;
            }
            handleTrustClick(session, slot, rightClick);
            return;
        }
        switch (slot) {
            case SLOT_TELEPORT -> {
                if (!player.hasPermission(PERM_BASE + ".teleport")) return;
                boolean inOwn = player.getUniqueId().equals(WorldUtils.ownerUuidOf(player.getWorld()));
                player.closeInventory();
                // Teleport on the next tick so the inventory is fully closed.
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (inOwn) {
                        dimensionService.leaveOwnDimension(player);
                    } else {
                        dimensionService.enterOwnDimension(player);
                    }
                });
            }
            case SLOT_TRUST -> {
                if (!player.hasPermission(PERM_BASE + ".trust")) return;
                session.trustView = true;
                rebuild(session);
            }
            case SLOT_PRESET -> cycleSetting(session, true);
            case SLOT_ENVIRONMENT -> cycleSetting(session, false);
            case SLOT_BORDER -> {
                if (!player.hasPermission(PERM_BASE + ".border")) return;
                economyManager.upgrade(player);
                rebuild(session);
            }
            case SLOT_NOTIFY -> {
                if (!player.hasPermission(PERM_BASE + ".settings")) return;
                DimensionSettings settings = settingsManager.get(player.getUniqueId());
                settings.setNotifyOnEntry(!settings.notifyOnEntryOrDefault());
                settingsManager.save(player.getUniqueId(), settings);
                rebuild(session);
            }
            case SLOT_BACK -> player.closeInventory();
            default -> {
            }
        }
    }

    private void handleTrustClick(Session session, int slot, boolean rightClick) {
        if (slot < TRUST_FIRST_SKULL || slot > TRUST_MAX_SKULL) {
            return;
        }
        List<Map.Entry<UUID, TrustTier>> trusts =
                new ArrayList<>(trustManager.getTrusts(session.viewer.getUniqueId()).entrySet());
        int index = slot - TRUST_FIRST_SKULL;
        if (index >= trusts.size()) {
            return;
        }
        UUID target = trusts.get(index).getKey();
        if (rightClick) {
            trustManager.clearTrust(session.viewer.getUniqueId(), target);
        } else {
            // Cycle BUILDER <-> TRUSTED.
            TrustTier current = trusts.get(index).getValue();
            trustManager.setTrust(session.viewer.getUniqueId(), target,
                    current == TrustTier.TRUSTED ? TrustTier.BUILDER : TrustTier.TRUSTED);
        }
        rebuild(session);
    }

    private void cycleSetting(Session session, boolean preset) {
        Player player = session.viewer;
        if (!player.hasPermission(PERM_BASE + ".settings")
                || !plugin.getConfig().getBoolean("features.dimension-presets", true)) {
            return;
        }
        DimensionSettings settings = settingsManager.get(player.getUniqueId());
        if (preset) {
            settings.setPreset(nextValue(PRESETS, settings.getPreset() != null ? settings.getPreset() : "FLAT"));
        } else {
            settings.setEnvironment(nextValue(ENVIRONMENTS,
                    settings.getEnvironment() != null ? settings.getEnvironment() : "NORMAL"));
        }
        settingsManager.save(player.getUniqueId(), settings);
        rebuild(session);
    }

    private static String nextValue(String[] values, String current) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                return values[(i + 1) % values.length];
            }
        }
        return values[0];
    }

    private void rebuild(Session session) {
        if (session.trustView) {
            buildTrustView(session);
        } else {
            buildMainView(session);
        }
    }

    private void buildMainView(Session session) {
        Player player = session.viewer;
        DimensionSettings settings = settingsManager.get(player.getUniqueId());
        String preset = settings.getPreset() != null ? settings.getPreset() : "FLAT";
        String environment = settings.getEnvironment() != null ? settings.getEnvironment() : "NORMAL";
        boolean inOwn = player.getUniqueId().equals(WorldUtils.ownerUuidOf(player.getWorld()));

        session.inventory.setItem(SLOT_TELEPORT, item(Material.ENDER_PEARL, "menu.teleport.name",
                inOwn ? "menu.teleport.lore-leave" : "menu.teleport.lore-enter",
                player.hasPermission(PERM_BASE + ".teleport")));
        session.inventory.setItem(SLOT_TRUST, item(Material.PLAYER_HEAD, "menu.trust.name",
                "menu.trust.lore", player.hasPermission(PERM_BASE + ".trust")));
        session.inventory.setItem(SLOT_PRESET, item(Material.GRASS_BLOCK, "menu.preset.name", "menu.preset.lore",
                player.hasPermission(PERM_BASE + ".settings"), "%value%", preset));
        session.inventory.setItem(SLOT_ENVIRONMENT, item(Material.OBSIDIAN, "menu.environment.name",
                "menu.environment.lore", player.hasPermission(PERM_BASE + ".settings"), "%value%", environment));

        int currentBorder = currentBorderSize(player);
        EconomyManager.UpgradeTier next = economyManager.nextTier(currentBorder);
        session.inventory.setItem(SLOT_BORDER, item(Material.WHITE_STAINED_GLASS, "menu.border.name",
                "menu.border.lore", player.hasPermission(PERM_BASE + ".border"),
                "%size%", String.valueOf(currentBorder),
                "%next%", String.valueOf(next != null ? next.size() : currentBorder)));

        session.inventory.setItem(SLOT_NOTIFY, item(Material.BELL, "menu.notify.name", "menu.notify.lore",
                player.hasPermission(PERM_BASE + ".settings"),
                "%state%", settings.notifyOnEntryOrDefault() ? "On" : "Off"));
        session.inventory.setItem(SLOT_BACK, item(Material.BARRIER, "menu.back.name", null, true));
    }

    private int currentBorderSize(Player player) {
        Integer size = borderManager.getBorderSize(player.getUniqueId());
        return size != null ? size : plugin.getConfig().getInt("default-world-border-size", 10000);
    }

    private void buildTrustView(Session session) {
        Player player = session.viewer;
        List<Map.Entry<UUID, TrustTier>> trusts =
                new ArrayList<>(trustManager.getTrusts(player.getUniqueId()).entrySet());
        int slot = TRUST_FIRST_SKULL;
        for (Map.Entry<UUID, TrustTier> entry : trusts) {
            if (slot > TRUST_MAX_SKULL) {
                break;
            }
            OfflinePlayer offline = Bukkit.getOfflinePlayer(entry.getKey());
            String name = offline.getName() != null ? offline.getName() : entry.getKey().toString().substring(0, 8);
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(offline);
                meta.displayName(MessageUtils.legacy(MessageUtils.getMessage("menu.trust.skull-name")
                        .replace("%player%", name)));
                List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                for (String line : MessageUtils.getMessages("menu.trust.skull-lore")) {
                    lore.add(MessageUtils.legacy(line.replace("%tier%", entry.getValue().display())));
                }
                meta.lore(lore);
                skull.setItemMeta(meta);
            }
            session.inventory.setItem(slot++, skull);
        }
        if (trusts.isEmpty()) {
            session.inventory.setItem(TRUST_FIRST_SKULL + 4,
                    item(Material.PAPER, "menu.trust.empty", null, true));
        }
        session.inventory.setItem(SLOT_BACK, item(Material.BARRIER, "menu.back.name", null, true));
    }

    private ItemStack item(Material material, String nameKey, String loreKey, boolean allowed, String... replacements) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        String name = apply(MessageUtils.getMessage(nameKey), replacements);
        meta.displayName(MessageUtils.legacy(name));
        if (loreKey != null) {
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            for (String line : MessageUtils.getMessages(loreKey)) {
                lore.add(MessageUtils.legacy(apply(line, replacements)));
            }
            meta.lore(lore);
        }
        if (!allowed) {
            meta.displayName(MessageUtils.legacy(MessageUtils.getMessage("menu.locked")));
        }
        item.setItemMeta(meta);
        return item;
    }

    private static String apply(String line, String... replacements) {
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            line = line.replace(replacements[i], replacements[i + 1]);
        }
        return line;
    }
}
