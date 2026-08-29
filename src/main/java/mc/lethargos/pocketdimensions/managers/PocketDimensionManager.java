package mc.lethargos.pocketdimensions.managers;

import mc.lethargos.pocketdimensions.utils.MessageUtils;
import mc.lethargos.pocketdimensions.utils.WorldUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class PocketDimensionManager {

    private final LocationManager locationManager;

    public PocketDimensionManager(LocationManager locationManager) {
        this.locationManager = locationManager;
    }

    public void kick(Player playerFrom, Player playerTo) {
        World pocketDimensionWorld = Bukkit.getWorld(WorldUtils.pocketWorldName(playerFrom.getUniqueId()));
        if (pocketDimensionWorld == null) {
            playerFrom.sendMessage(MessageUtils.getMessage("pd.kick.no-dimension"));
            return;
        }
        if (playerTo == null) {
            playerFrom.sendMessage(MessageUtils.getMessage("player-not-online"));
            return;
        }
        if (playerTo.getWorld() != pocketDimensionWorld) {
            playerFrom.sendMessage(MessageUtils.getMessage("pd.kick.not-in-dimension"));
            return;
        }
        if (playerTo.hasPermission("pocketdimensions.commands.player.pd.kick.bypass") || playerTo.isOp()) {
            playerFrom.sendMessage(MessageUtils.getMessage("pd.kick.bypass"));
            return;
        }
        Location lastLocation = locationManager.getLastLocation(playerTo, false);
        if (lastLocation != null) {
            playerTo.teleport(lastLocation);
        } else {
            World mainWorld = WorldUtils.getMainWorld();
            if (mainWorld != null) {
                playerTo.teleport(mainWorld.getSpawnLocation());
            }
        }
        playerFrom.sendMessage(MessageUtils.getMessage("pd.kick.success"));
    }
}
