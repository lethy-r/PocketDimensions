package mc.lethargos.pocketdimensions.commands;

import mc.lethargos.pocketdimensions.utils.MessageUtils;
import mc.lethargos.pocketdimensions.utils.WorldUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class pdtp implements CommandExecutor, TabCompleter {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(MessageUtils.getMessage("only-players"));
            return true;
        }

        if (!sender.hasPermission("pocketdimensions.commands.pdtp")) {
            sender.sendMessage(MessageUtils.getMessage("no-permission"));
            return true;
        }

        Player player = (Player) sender;
        if (args.length == 0) {
            player.sendMessage(MessageUtils.getMessage("pdtp.usage"));
            return true;
        }

        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(args[0]);
        String worldName = WorldUtils.pocketWorldName(targetPlayer.getUniqueId());
        File worldFolder = new File(Bukkit.getWorldContainer(), worldName);

        if (worldFolder.exists() && worldFolder.isDirectory()) {
            World targetWorld = Bukkit.getWorld(worldName);
            if (targetWorld == null) {
                targetWorld = new WorldCreator(worldName).createWorld();
            }
            if (targetWorld == null) {
                player.sendMessage(MessageUtils.getMessage("dimension.load-failed"));
                return true;
            }
            player.sendMessage(MessageUtils.getMessage("pdtp.teleporting")
                    .replace("%player%", targetPlayer.getName() != null ? targetPlayer.getName() : args[0]));
            player.teleport(targetWorld.getSpawnLocation());
        } else {
            player.sendMessage(MessageUtils.getMessage("pdtp.no-dimension"));
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return null; // Return online players
        }
        return new ArrayList<>();
    }
}
