package mc.lethargos.pocketdimensions.commands;

import mc.lethargos.pocketdimensions.managers.SettingsManager;
import mc.lethargos.pocketdimensions.storage.DimensionSettings;
import mc.lethargos.pocketdimensions.utils.MessageUtils;
import mc.lethargos.pocketdimensions.utils.WorldUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class pdgamerule implements CommandExecutor, TabCompleter {

    private final SettingsManager settingsManager;

    public pdgamerule(SettingsManager settingsManager) {
        this.settingsManager = settingsManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(MessageUtils.getMessage("only-players"));
            return true;
        }

        if (!sender.hasPermission("pocketdimensions.commands.pdgamerule")) {
            sender.sendMessage(MessageUtils.getMessage("no-permission"));
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 2) {
            player.sendMessage(MessageUtils.getMessage("pdgamerule.usage"));
            return true;
        }

        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(args[0]);
        UUID owner = targetPlayer.getUniqueId();
        String worldName = WorldUtils.pocketWorldName(owner);
        File worldFolder = new File(Bukkit.getWorldContainer(), worldName);

        if (!worldFolder.exists() || !worldFolder.isDirectory()) {
            player.sendMessage(MessageUtils.getMessage("pdgamerule.no-dimension"));
            return true;
        }

        World targetWorld = Bukkit.getWorld(worldName);
        if (targetWorld == null) {
            targetWorld = new WorldCreator(worldName).createWorld();
        }
        if (targetWorld == null) {
            player.sendMessage(MessageUtils.getMessage("dimension.load-failed"));
            return true;
        }

        GameRule<?> gameRule = GameRule.getByName(args[1]);
        if (gameRule == null) {
            player.sendMessage(MessageUtils.getMessage("pdgamerule.unknown").replace("%rule%", args[1]));
            return true;
        }

        // If no value is provided, just display the current value of the game rule.
        if (args.length == 2) {
            Object currentValue = targetWorld.getGameRuleValue(gameRule);
            if (currentValue != null) {
                player.sendMessage(MessageUtils.getMessage("pdgamerule.current-value")
                        .replace("%rule%", args[1])
                        .replace("%value%", currentValue.toString()));
            } else {
                player.sendMessage(MessageUtils.getMessage("pdgamerule.unable-to-get").replace("%rule%", args[1]));
            }
            return true;
        }

        // Handle setting a new value for the game rule
        String inputValue = args[2];
        try {
            if (gameRule.getType() == Boolean.class) {
                if (!inputValue.equalsIgnoreCase("true") && !inputValue.equalsIgnoreCase("false")) {
                    player.sendMessage(MessageUtils.getMessage("pdgamerule.invalid-boolean"));
                    return true;
                }
                Boolean value = Boolean.parseBoolean(inputValue);
                targetWorld.setGameRule((GameRule<Boolean>) gameRule, value);
            } else if (gameRule.getType() == Integer.class) {
                if (!inputValue.matches("-?[0-9]+")) {
                    player.sendMessage(MessageUtils.getMessage("pdgamerule.invalid-integer"));
                    return true;
                }
                Integer value = Integer.parseInt(inputValue);
                targetWorld.setGameRule((GameRule<Integer>) gameRule, value);
            } else {
                player.sendMessage(MessageUtils.getMessage("pdgamerule.unsupported-type"));
                return true;
            }

            // Persist as a per-dimension override so it survives unload/reload
            // and is re-applied on entry (over the config defaults).
            DimensionSettings settings = settingsManager.get(owner);
            settings.setGameruleOverride(gameRule.getName(), inputValue.toLowerCase());
            settingsManager.save(owner, settings);

            player.sendMessage(MessageUtils.getMessage("pdgamerule.set-success")
                    .replace("%rule%", args[1])
                    .replace("%value%", inputValue));
        } catch (NumberFormatException e) {
            player.sendMessage(MessageUtils.getMessage("pdgamerule.invalid-value").replace("%rule%", args[1]));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            return null; // Suggest online players
        }

        if (args.length == 2) {
            // Suggest game rules when the player has typed the second argument
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(args[0]);
            World targetWorld = Bukkit.getWorld(WorldUtils.pocketWorldName(targetPlayer.getUniqueId()));

            if (targetWorld != null) {
                for (String gameRule : targetWorld.getGameRules()) {
                    if (gameRule.toLowerCase().startsWith(args[1].toLowerCase())) {
                        suggestions.add(gameRule);
                    }
                }
            }
        }

        return suggestions;
    }
}
