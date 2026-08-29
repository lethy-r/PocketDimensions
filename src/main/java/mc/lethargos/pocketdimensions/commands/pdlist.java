package mc.lethargos.pocketdimensions.commands;

import mc.lethargos.pocketdimensions.PocketDimensions;
import mc.lethargos.pocketdimensions.managers.BorderManager;
import mc.lethargos.pocketdimensions.utils.MessageUtils;
import mc.lethargos.pocketdimensions.utils.WorldUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * /pdlist - overview of every pocket dimension on disk (admin utility).
 */
public class pdlist implements CommandExecutor {

    private final PocketDimensions plugin;
    private final BorderManager borderManager;

    public pdlist(PocketDimensions plugin, BorderManager borderManager) {
        this.plugin = plugin;
        this.borderManager = borderManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.getConfig().getBoolean("features.admin-tools", true)) {
            sender.sendMessage(MessageUtils.getMessage("feature-disabled"));
            return true;
        }
        if (!sender.hasPermission("pocketdimensions.commands.admin.list")) {
            sender.sendMessage(MessageUtils.getMessage("no-permission"));
            return true;
        }

        File worldsDir = new File(Bukkit.getWorldContainer(), WorldUtils.WORLDS_FOLDER);
        File[] folders = worldsDir.listFiles(File::isDirectory);
        List<File> dimensions = new ArrayList<>();
        if (folders != null) {
            for (File folder : folders) {
                if (folder.getName().startsWith(WorldUtils.PD_NAME_MARKER)) {
                    dimensions.add(folder);
                }
            }
        }
        if (dimensions.isEmpty()) {
            sender.sendMessage(MessageUtils.getMessage("admin.list.none"));
            return true;
        }

        sender.sendMessage(MessageUtils.getMessage("admin.list.header")
                .replace("%count%", String.valueOf(dimensions.size())));
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        for (File folder : dimensions) {
            UUID owner;
            try {
                owner = UUID.fromString(folder.getName().substring(WorldUtils.PD_NAME_MARKER.length()));
            } catch (IllegalArgumentException e) {
                continue;
            }
            OfflinePlayer player = Bukkit.getOfflinePlayer(owner);
            String name = player.getName() != null ? player.getName() : owner.toString().substring(0, 8);
            boolean loaded = Bukkit.getWorld(WorldUtils.pocketWorldName(owner)) != null;
            Integer border = borderManager.getBorderSize(owner);
            String borderText = String.valueOf(border != null ? border
                    : plugin.getConfig().getInt("default-world-border-size", 10000));
            String lastSeen = player.isOnline() ? "online"
                    : (player.getLastPlayed() > 0 ? dateFormat.format(new Date(player.getLastPlayed())) : "never");
            sender.sendMessage(MessageUtils.getMessage("admin.list.entry")
                    .replace("%player%", name)
                    .replace("%state%", loaded ? "loaded" : "unloaded")
                    .replace("%size%", borderText)
                    .replace("%last%", lastSeen));
        }
        return true;
    }
}
