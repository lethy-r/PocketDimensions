package mc.lethargos.pocketdimensions.commands;

import mc.lethargos.pocketdimensions.PocketDimensions;
import mc.lethargos.pocketdimensions.utils.DimensionKeyItem;
import mc.lethargos.pocketdimensions.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class givepd implements CommandExecutor, TabCompleter {

    private final PocketDimensions plugin;

    public givepd(PocketDimensions plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(MessageUtils.getMessage("only-players"));
            return true;
        }

        // Permission check
        if (!sender.hasPermission("pocketdimensions.commands.givepd")) {
            sender.sendMessage(MessageUtils.getMessage("no-permission"));
            return true;
        }

        Player player = (Player) sender;
        if (args.length == 0) {
            player.sendMessage(MessageUtils.getMessage("givepd.usage"));
            return true;
        }

        String targetName = args[0];
        OfflinePlayer targetPlayer = Bukkit.getPlayerExact(targetName);
        if (targetPlayer == null) {
            targetPlayer = Bukkit.getOfflinePlayer(targetName);
        }
        UUID targetUUID = targetPlayer.getUniqueId();
        String targetPlayerName = targetPlayer.getName() != null ? targetPlayer.getName() : targetName;

        String type = "dimension";
        if (args.length > 1) {
            type = args[1].toLowerCase();
        }

        if (type.equals("dimension")) {
            ItemStack item = DimensionKeyItem.createDimensionKey(plugin, targetUUID, targetPlayerName);
            player.getInventory().addItem(item);
            player.sendMessage(MessageUtils.getMessage("givepd.item-given").replace("%player%", targetPlayerName));
        } else if (type.equals("mobtool") || type.equals("mob")) {
            if (!player.hasPermission("pocketdimensions.commands.givepd.tool")) {
                player.sendMessage(MessageUtils.getMessage("no-permission"));
                return true;
            }
            ItemStack item = DimensionKeyItem.createMobTool(plugin, targetUUID, targetPlayerName);
            player.getInventory().addItem(item);
            player.sendMessage(MessageUtils.getMessage("givepd.tool-given").replace("%player%", targetPlayerName));
        } else {
            player.sendMessage(MessageUtils.getMessage("givepd.invalid-type"));
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return null; // Null returns online players
        } else if (args.length == 2) {
            List<String> types = new ArrayList<>();
            types.add("dimension");
            if (sender.hasPermission("pocketdimensions.commands.givepd.tool")) {
                types.add("mobtool");
            }
            return types.stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
