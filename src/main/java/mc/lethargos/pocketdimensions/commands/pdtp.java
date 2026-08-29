package mc.lethargos.pocketdimensions.commands;

import mc.lethargos.pocketdimensions.managers.DimensionService;
import mc.lethargos.pocketdimensions.utils.MessageUtils;
import mc.lethargos.pocketdimensions.utils.WorldUtils;
import org.bukkit.Bukkit;
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
import java.util.List;
import java.util.UUID;

public class pdtp implements CommandExecutor, TabCompleter {

    private final DimensionService dimensionService;

    public pdtp(DimensionService dimensionService) {
        this.dimensionService = dimensionService;
    }

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
        UUID owner = targetPlayer.getUniqueId();

        if (!WorldUtils.existingWorldFolder(owner)) {
            player.sendMessage(MessageUtils.getMessage("pdtp.no-dimension"));
            return true;
        }

        // Loads the world with its proper preset generator when it was unloaded.
        World targetWorld = dimensionService.loadWorld(owner);
        if (targetWorld == null) {
            player.sendMessage(MessageUtils.getMessage("dimension.load-failed"));
            return true;
        }
        player.sendMessage(MessageUtils.getMessage("pdtp.teleporting")
                .replace("%player%", targetPlayer.getName() != null ? targetPlayer.getName() : args[0]));
        player.teleport(targetWorld.getSpawnLocation());

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
