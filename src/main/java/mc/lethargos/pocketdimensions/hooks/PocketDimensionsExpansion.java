package mc.lethargos.pocketdimensions.hooks;

import mc.lethargos.pocketdimensions.PocketDimensions;
import mc.lethargos.pocketdimensions.managers.BorderManager;
import mc.lethargos.pocketdimensions.utils.WorldUtils;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * PlaceholderAPI expansion:
 *   %pd_in_pd%            - whether the player stands in a pocket dimension
 *   %pd_border%           - the border size of the dimension they are in
 *   %pd_visitors%         - players currently inside the player's dimension
 *   %pd_dimension_loaded% - whether the player's dimension world is loaded
 */
public class PocketDimensionsExpansion extends PlaceholderExpansion {

    private final PocketDimensions plugin;
    private final BorderManager borderManager;

    public PocketDimensionsExpansion(PocketDimensions plugin, BorderManager borderManager) {
        this.plugin = plugin;
        this.borderManager = borderManager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "pd";
    }

    @Override
    public @NotNull String getAuthor() {
        return "lethy";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(@Nullable org.bukkit.OfflinePlayer offlinePlayer, @NotNull String params) {
        if (offlinePlayer == null || !offlinePlayer.isOnline()) {
            return "";
        }
        Player player = (Player) offlinePlayer;
        UUID owner = player.getUniqueId();
        switch (params.toLowerCase()) {
            case "in_pd":
                return String.valueOf(WorldUtils.isPocketWorld(player.getWorld()));
            case "border":
                return WorldUtils.isPocketWorld(player.getWorld())
                        ? String.valueOf((int) player.getWorld().getWorldBorder().getSize())
                        : "";
            case "visitors":
                World dimension = Bukkit.getWorld(WorldUtils.pocketWorldName(owner));
                if (dimension == null) {
                    return "0";
                }
                long visitors = dimension.getPlayers().stream()
                        .filter(online -> !online.getUniqueId().equals(owner))
                        .count();
                return String.valueOf(visitors);
            case "dimension_loaded":
                return String.valueOf(Bukkit.getWorld(WorldUtils.pocketWorldName(owner)) != null);
            default:
                return null;
        }
    }
}
