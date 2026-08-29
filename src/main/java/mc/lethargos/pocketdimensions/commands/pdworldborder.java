package mc.lethargos.pocketdimensions.commands;

import mc.lethargos.pocketdimensions.managers.BorderManager;
import mc.lethargos.pocketdimensions.utils.MessageUtils;
import mc.lethargos.pocketdimensions.utils.WorldUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class pdworldborder implements CommandExecutor, TabCompleter {

    private final BorderManager borderManager;

    public pdworldborder(BorderManager borderManager) {
        this.borderManager = borderManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("pocketdimensions.commands.pdworldborder")) {
            sender.sendMessage(MessageUtils.getMessage("no-permission"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(MessageUtils.getMessage("pdworldborder.usage"));
            return true;
        }

        String playerName = args[0];
        String sizeStr = args[1];
        int size;

        try {
            size = Integer.parseInt(sizeStr);
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtils.getMessage("pdworldborder.invalid-int"));
            return true;
        }

        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerName);
        if (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline()) {
            sender.sendMessage(MessageUtils.getMessage("pdworldborder.unknown-player"));
            return true;
        }

        borderManager.setBorderSize(targetPlayer.getUniqueId(), size);
        sender.sendMessage(MessageUtils.getMessage("pdworldborder.success")
                .replace("%player%", targetPlayer.getName() != null ? targetPlayer.getName() : playerName)
                .replace("%size%", String.valueOf(size)));

        // If world is loaded, update immediately
        World pdWorld = Bukkit.getWorld(WorldUtils.pocketWorldName(targetPlayer.getUniqueId()));
        if (pdWorld != null) {
            pdWorld.getWorldBorder().setSize(size);
            sender.sendMessage(MessageUtils.getMessage("pdworldborder.updated"));
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return null; // Return online players
        } else if (args.length == 2) {
            List<String> suggestions = Arrays.asList("1000", "5000", "10000", "20000");
            return suggestions.stream()
                    .filter(s -> s.startsWith(args[1]))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
