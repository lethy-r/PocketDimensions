package mc.lethargos.pocketdimensions.commands.player;

import mc.lethargos.pocketdimensions.PocketDimensions;
import mc.lethargos.pocketdimensions.managers.EconomyManager;
import mc.lethargos.pocketdimensions.managers.InviteManager;
import mc.lethargos.pocketdimensions.managers.PocketDimensionManager;
import mc.lethargos.pocketdimensions.managers.SettingsManager;
import mc.lethargos.pocketdimensions.managers.TrustManager;
import mc.lethargos.pocketdimensions.menu.PocketMenu;
import mc.lethargos.pocketdimensions.storage.DimensionSettings;
import mc.lethargos.pocketdimensions.storage.TrustTier;
import mc.lethargos.pocketdimensions.utils.MessageUtils;
import mc.lethargos.pocketdimensions.utils.WorldUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class pd implements CommandExecutor, TabCompleter {

    private static final String ACTIONS_BASE = "pocketdimensions.commands.player.pd";

    /** action -> permission node; actions a player lacks are hidden and rejected. */
    private static final Map<String, String> ACTIONS = new LinkedHashMap<>();
    static {
        ACTIONS.put("invite", ACTIONS_BASE + ".invite");
        ACTIONS.put("acceptinv", ACTIONS_BASE + ".invite"); // accepting uses the invite permission
        ACTIONS.put("kick", ACTIONS_BASE + ".kick");
        ACTIONS.put("trust", ACTIONS_BASE + ".trust");
        ACTIONS.put("untrust", ACTIONS_BASE + ".trust");
        ACTIONS.put("setspawn", ACTIONS_BASE + ".setspawn");
        ACTIONS.put("upgrade", ACTIONS_BASE + ".upgrade");
        ACTIONS.put("settings", ACTIONS_BASE + ".settings");
        ACTIONS.put("menu", ACTIONS_BASE + ".menu");
    }

    private final PocketDimensions plugin;
    private final InviteManager inviteManager;
    private final PocketDimensionManager pocketDimensionManager;
    private final TrustManager trustManager;
    private final SettingsManager settingsManager;
    private final EconomyManager economyManager;
    private final PocketMenu menu;

    public pd(PocketDimensions plugin, InviteManager inviteManager, PocketDimensionManager pocketDimensionManager,
              TrustManager trustManager, SettingsManager settingsManager, EconomyManager economyManager,
              PocketMenu menu) {
        this.plugin = plugin;
        this.inviteManager = inviteManager;
        this.pocketDimensionManager = pocketDimensionManager;
        this.trustManager = trustManager;
        this.settingsManager = settingsManager;
        this.economyManager = economyManager;
        this.menu = menu;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(MessageUtils.getMessage("only-players"));
            return true;
        }
        Player player = (Player) sender;
        if (args.length == 0 || args[0].isEmpty()) {
            player.sendMessage(MessageUtils.getMessage("pd.usage"));
            return true;
        }

        String action = args[0].toLowerCase();
        String permission = ACTIONS.get(action);
        if (permission == null) {
            player.sendMessage(MessageUtils.getMessage("pd.invalid-action")
                    .replace("%actions%", ACTIONS.keySet().toString().replace("[", "").replace("]", "")));
            return true;
        }
        if (!player.hasPermission(permission)) {
            player.sendMessage(MessageUtils.getMessage("no-permission"));
            return true;
        }

        switch (action) {
            case "invite" -> handleInvite(player, args);
            case "acceptinv" -> handleAcceptInvite(player, args);
            case "kick" -> handleKick(player, args);
            case "trust" -> handleTrust(player, args, true);
            case "untrust" -> handleTrust(player, args, false);
            case "setspawn" -> handleSetSpawn(player);
            case "upgrade" -> economyManager.upgrade(player);
            case "settings" -> handleSettings(player, args);
            case "menu" -> menu.open(player);
            default -> {
            }
        }
        return true;
    }

    private void handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(MessageUtils.getMessage("pd.invite.usage"));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(MessageUtils.getMessage("player-not-online"));
            return;
        }
        inviteManager.sendInvite(player, target);
    }

    private void handleAcceptInvite(Player player, String[] args) {
        inviteManager.acceptInvite(player, args.length >= 2 ? args[1] : null);
    }

    private void handleKick(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(MessageUtils.getMessage("pd.kick.usage"));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(MessageUtils.getMessage("player-not-online"));
            return;
        }
        pocketDimensionManager.kick(player, target);
    }

    private void handleTrust(Player player, String[] args, boolean add) {
        if (args.length < 2) {
            player.sendMessage(MessageUtils.getMessage(add ? "pd.trust.usage" : "pd.untrust.usage"));
            return;
        }
        if (args[1].equalsIgnoreCase(player.getName())) {
            player.sendMessage(MessageUtils.getMessage("pd.trust.self"));
            return;
        }
        OfflinePlayer target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            target = Bukkit.getOfflinePlayer(args[1]);
        }
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            player.sendMessage(MessageUtils.getMessage("pdworldborder.unknown-player"));
            return;
        }
        if (!add) {
            trustManager.clearTrust(player.getUniqueId(), target.getUniqueId());
            player.sendMessage(MessageUtils.getMessage("pd.trust.removed")
                    .replace("%player%", target.getName() != null ? target.getName() : args[1]));
            return;
        }
        TrustTier tier = TrustTier.BUILDER;
        if (args.length >= 3) {
            tier = TrustTier.fromString(args[2]);
            if (tier == null) {
                player.sendMessage(MessageUtils.getMessage("pd.trust.invalid-tier")
                        .replace("%tiers%", "builder, trusted"));
                return;
            }
            if (tier == TrustTier.TRUSTED && !player.hasPermission(ACTIONS_BASE + ".trust.trusted")) {
                player.sendMessage(MessageUtils.getMessage("no-permission"));
                return;
            }
        }
        trustManager.setTrust(player.getUniqueId(), target.getUniqueId(), tier);
        player.sendMessage(MessageUtils.getMessage("pd.trust.success")
                .replace("%player%", target.getName() != null ? target.getName() : args[1])
                .replace("%tier%", tier.display()));
        Player online = target.getPlayer();
        if (online != null && online.isOnline()) {
            online.sendMessage(MessageUtils.getMessage("pd.trust.granted")
                    .replace("%player%", player.getName())
                    .replace("%tier%", tier.display()));
        }
    }

    private void handleSetSpawn(Player player) {
        if (!plugin.getConfig().getBoolean("features.setspawn", true)) {
            player.sendMessage(MessageUtils.getMessage("feature-disabled"));
            return;
        }
        UUID worldOwner = WorldUtils.ownerUuidOf(player.getWorld());
        if (!player.getUniqueId().equals(worldOwner)) {
            player.sendMessage(MessageUtils.getMessage("pd.setspawn.not-in-dimension"));
            return;
        }
        player.getWorld().setSpawnLocation(player.getLocation());
        player.sendMessage(MessageUtils.getMessage("pd.setspawn.success"));
    }

    private void handleSettings(Player player, String[] args) {
        if (args.length < 3) {
            DimensionSettings settings =
                    settingsManager.get(player.getUniqueId());
            player.sendMessage(MessageUtils.getMessage("pd.settings.current")
                    .replace("%preset%", settings.getPreset() != null ? settings.getPreset() : "default")
                    .replace("%environment%", settings.getEnvironment() != null ? settings.getEnvironment() : "default")
                    .replace("%notify%", settings.notifyOnEntryOrDefault() ? "true" : "false"));
            return;
        }
        String sub = args[1].toLowerCase();
        String value = args[2];
        DimensionSettings settings =
                settingsManager.get(player.getUniqueId());
        if (sub.equals("preset")) {
            if (!plugin.getConfig().getBoolean("features.dimension-presets", true)) {
                player.sendMessage(MessageUtils.getMessage("feature-disabled"));
                return;
            }
            String normalized = value.trim().toUpperCase();
            if (!normalized.equals("FLAT") && !normalized.equals("VOID") && !normalized.equals("NORMAL")) {
                player.sendMessage(MessageUtils.getMessage("pd.settings.invalid-preset"));
                return;
            }
            settings.setPreset(normalized);
            settingsManager.save(player.getUniqueId(), settings);
            player.sendMessage(MessageUtils.getMessage("pd.settings.preset-set")
                    .replace("%value%", normalized));
        } else if (sub.equals("environment")) {
            if (!plugin.getConfig().getBoolean("features.dimension-presets", true)) {
                player.sendMessage(MessageUtils.getMessage("feature-disabled"));
                return;
            }
            String normalized = value.trim().toUpperCase();
            if (!normalized.equals("NORMAL") && !normalized.equals("NETHER") && !normalized.equals("THE_END")) {
                player.sendMessage(MessageUtils.getMessage("pd.settings.invalid-environment"));
                return;
            }
            settings.setEnvironment(normalized);
            settingsManager.save(player.getUniqueId(), settings);
            player.sendMessage(MessageUtils.getMessage("pd.settings.environment-set")
                    .replace("%value%", normalized));
        } else if (sub.equals("notify")) {
            if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                player.sendMessage(MessageUtils.getMessage("pd.settings.invalid-notify"));
                return;
            }
            settings.setNotifyOnEntry(Boolean.parseBoolean(value));
            settingsManager.save(player.getUniqueId(), settings);
            player.sendMessage(MessageUtils.getMessage("pd.settings.notify-set")
                    .replace("%value%", value.toLowerCase()));
        } else {
            player.sendMessage(MessageUtils.getMessage("pd.settings.usage"));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            boolean isPlayer = sender instanceof Player;
            List<String> result = new ArrayList<>();
            for (Map.Entry<String, String> entry : ACTIONS.entrySet()) {
                if (!isPlayer || sender.hasPermission(entry.getValue())) {
                    if (entry.getKey().startsWith(prefix)) {
                        result.add(entry.getKey());
                    }
                }
            }
            return result;
        }
        String action = args[0].toLowerCase();
        if (args.length == 2) {
            switch (action) {
                case "invite", "kick" -> {
                    return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                            .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                }
                case "trust", "untrust" -> {
                    return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                            .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                }
                case "acceptinv" -> {
                    if (sender instanceof Player player) {
                        return inviteManager.pendingFrom(player).stream()
                                .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                                .collect(Collectors.toList());
                    }
                    return null;
                }
                default -> {
                    return null;
                }
            }
        }
        if (args.length == 3) {
            if (action.equals("trust")) {
                return List.of("builder", "trusted").stream()
                        .filter(s -> s.startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (action.equals("settings")) {
                if (args[1].equalsIgnoreCase("preset")) {
                    return List.of("flat", "void", "normal").stream()
                            .filter(s -> s.startsWith(args[2].toLowerCase()))
                            .collect(Collectors.toList());
                }
                if (args[1].equalsIgnoreCase("environment")) {
                    return List.of("normal", "nether", "the_end").stream()
                            .filter(s -> s.startsWith(args[2].toLowerCase()))
                            .collect(Collectors.toList());
                }
                if (args[1].equalsIgnoreCase("notify")) {
                    return List.of("true", "false").stream()
                            .filter(s -> s.startsWith(args[2].toLowerCase()))
                            .collect(Collectors.toList());
                }
            }
        }
        return null;
    }
}
