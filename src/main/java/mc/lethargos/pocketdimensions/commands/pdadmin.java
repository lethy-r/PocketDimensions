package mc.lethargos.pocketdimensions.commands;

import mc.lethargos.pocketdimensions.PocketDimensions;
import mc.lethargos.pocketdimensions.managers.DimensionWorldManager;
import mc.lethargos.pocketdimensions.managers.SettingsManager;
import mc.lethargos.pocketdimensions.managers.TrustManager;
import mc.lethargos.pocketdimensions.storage.Storage;
import mc.lethargos.pocketdimensions.utils.MessageUtils;
import mc.lethargos.pocketdimensions.utils.WorldUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * /pdadmin - delete, reset and clean up pocket dimension worlds.
 *   /pdadmin delete <player> confirm   - removes the world AND all stored data
 *   /pdadmin reset <player> confirm    - removes the world, keeps player data
 *   /pdadmin cleanup <days> confirm    - deletes dimensions of players inactive for <days>
 */
public class pdadmin implements CommandExecutor {

    private final PocketDimensions plugin;
    private final DimensionWorldManager dimensionWorldManager;
    private final TrustManager trustManager;
    private final SettingsManager settingsManager;
    private final Storage storage;

    public pdadmin(PocketDimensions plugin, DimensionWorldManager dimensionWorldManager,
                   TrustManager trustManager, SettingsManager settingsManager, Storage storage) {
        this.plugin = plugin;
        this.dimensionWorldManager = dimensionWorldManager;
        this.trustManager = trustManager;
        this.settingsManager = settingsManager;
        this.storage = storage;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(MessageUtils.getMessage("admin.usage"));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "delete" -> handleDeleteOrReset(sender, args, true);
            case "reset" -> handleDeleteOrReset(sender, args, false);
            case "cleanup" -> handleCleanup(sender, args);
            default -> sender.sendMessage(MessageUtils.getMessage("admin.usage"));
        }
        return true;
    }

    private void handleDeleteOrReset(CommandSender sender, String[] args, boolean deleteData) {
        String action = deleteData ? "delete" : "reset";
        if (args.length < 2) {
            sender.sendMessage(MessageUtils.getMessage("admin." + action + ".usage"));
            return;
        }
        if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
            sender.sendMessage(MessageUtils.getMessage("admin.confirm-required")
                    .replace("%command%", "/pdadmin " + action + " " + args[1]));
            return;
        }
        OfflinePlayer target = resolvePlayer(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtils.getMessage("pdworldborder.unknown-player"));
            return;
        }
        String worldName = WorldUtils.pocketWorldName(target.getUniqueId());
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            if (!world.getPlayers().isEmpty()) {
                sender.sendMessage(MessageUtils.getMessage("admin.occupied")
                        .replace("%players%", String.join(", ", world.getPlayers().stream()
                                .map(p -> p.getName()).toList())));
                return;
            }
            if (!Bukkit.unloadWorld(world, true) || Bukkit.getWorld(worldName) != null) {
                sender.sendMessage(MessageUtils.getMessage("admin.unload-failed").replace("%world%", worldName));
                return;
            }
        }

        File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
        if (worldFolder.exists()) {
            if (!deleteRecursively(worldFolder)) {
                sender.sendMessage(MessageUtils.getMessage("admin.delete-failed").replace("%world%", worldName));
                return;
            }
        }
        if (deleteData) {
            storage.purgePlayer(target.getUniqueId());
            trustManager.invalidateAll();
            settingsManager.invalidateAll();
        }
        sender.sendMessage(MessageUtils.getMessage("admin." + action + ".success")
                .replace("%player%", target.getName() != null ? target.getName() : args[1]));
        plugin.getLogger().info(sender.getName() + " " + action + "d the pocket dimension of "
                + (target.getName() != null ? target.getName() : target.getUniqueId()) + ".");
    }

    private void handleCleanup(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MessageUtils.getMessage("admin.cleanup.usage"));
            return;
        }
        if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
            sender.sendMessage(MessageUtils.getMessage("admin.confirm-required")
                    .replace("%command%", "/pdadmin cleanup " + args[1]));
            return;
        }
        long days;
        try {
            days = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtils.getMessage("admin.cleanup.usage"));
            return;
        }
        long cutoff = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L;
        File worldsDir = new File(Bukkit.getWorldContainer(), WorldUtils.WORLDS_FOLDER);
        File[] folders = worldsDir.listFiles(File::isDirectory);
        int removed = 0;
        if (folders != null) {
            for (File folder : folders) {
                if (!folder.getName().startsWith(WorldUtils.PD_NAME_MARKER)) {
                    continue;
                }
                OfflinePlayer owner;
                try {
                    owner = Bukkit.getOfflinePlayer(UUID.fromString(
                            folder.getName().substring(WorldUtils.PD_NAME_MARKER.length())));
                } catch (IllegalArgumentException e) {
                    continue;
                }
                if (owner.isOnline() || owner.getLastPlayed() <= 0 || owner.getLastPlayed() >= cutoff) {
                    continue;
                }
                String worldName = WorldUtils.pocketWorldName(owner.getUniqueId());
                World world = Bukkit.getWorld(worldName);
                if (world != null && !world.getPlayers().isEmpty()) {
                    continue;
                }
                if (world != null && (!Bukkit.unloadWorld(world, true) || Bukkit.getWorld(worldName) != null)) {
                    plugin.getLogger().warning("Cleanup: could not unload " + worldName + ", skipping.");
                    continue;
                }
                File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
                if (!worldFolder.exists() || deleteRecursively(worldFolder)) {
                    storage.purgePlayer(owner.getUniqueId());
                    removed++;
                } else {
                    plugin.getLogger().warning("Cleanup: could not fully delete " + worldName + ", skipping.");
                }
            }
        }
        trustManager.invalidateAll();
        settingsManager.invalidateAll();
        sender.sendMessage(MessageUtils.getMessage("admin.cleanup.success")
                .replace("%count%", String.valueOf(removed))
                .replace("%days%", args[1]));
    }

    private OfflinePlayer resolvePlayer(String name) {
        OfflinePlayer online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return (offline.hasPlayedBefore() || offline.isOnline()) ? offline : null;
    }

    /** @return true only when every file was removed and the folder is gone. */
    private boolean deleteRecursively(File file) {
        Path path = file.toPath();
        boolean[] success = {true};
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    success[0] = false;
                    plugin.getLogger().warning("Could not delete " + p + ": " + e.getMessage());
                }
            });
        } catch (IOException e) {
            plugin.getLogger().severe("Could not walk " + path + ": " + e.getMessage());
            return false;
        }
        return success[0] && !Files.exists(path);
    }
}
