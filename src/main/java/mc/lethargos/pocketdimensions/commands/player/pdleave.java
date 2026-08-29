package mc.lethargos.pocketdimensions.commands.player;

import mc.lethargos.pocketdimensions.managers.LocationManager;
import mc.lethargos.pocketdimensions.utils.MessageUtils;
import mc.lethargos.pocketdimensions.utils.WorldUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class pdleave implements CommandExecutor {
    private final LocationManager locationManager;

    public pdleave(LocationManager locationManager) {
        this.locationManager = locationManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(MessageUtils.getMessage("only-players"));
            return true;
        }

        if (!sender.hasPermission("pocketdimensions.commands.player.pdleave")) {
            sender.sendMessage(MessageUtils.getMessage("no-permission"));
            return true;
        }

        Player player = (Player) sender;

        // Check if player is in a pocket dimension
        if (!WorldUtils.isPocketWorld(player.getWorld())) {
            player.sendMessage(MessageUtils.getMessage("pd.leave.not-in-dimension"));
            return true;
        }

        player.sendMessage(MessageUtils.getMessage("pd.leave.leaving"));

        // Teleport to last location
        Location lastLocation = locationManager.getLastLocation(player, false);
        if (lastLocation != null) {
            player.teleport(lastLocation);
        } else {
            // Fallback to main world
            World mainWorld = WorldUtils.getMainWorld();
            if (mainWorld != null) {
                player.teleport(mainWorld.getSpawnLocation());
            } else {
                player.sendMessage(MessageUtils.getMessage("dimension.load-failed")); // Fallback error
            }
        }

        return true;
    }
}
