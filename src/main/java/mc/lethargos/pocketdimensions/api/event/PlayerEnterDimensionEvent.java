package mc.lethargos.pocketdimensions.api.event;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Fired before a player is teleported into their own pocket dimension.
 * Cancelling aborts the entry (no world is created if it does not exist yet).
 */
public class PlayerEnterDimensionEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final UUID owner;
    private final World world;
    private boolean cancelled;

    public PlayerEnterDimensionEvent(Player player, UUID owner, World world) {
        this.player = player;
        this.owner = owner;
        this.world = world;
    }

    public Player getPlayer() {
        return player;
    }

    public UUID getOwner() {
        return owner;
    }

    public String getOwnerName() {
        Player online = Bukkit.getPlayer(owner);
        return online != null ? online.getName() : owner.toString();
    }

    public World getWorld() {
        return world;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
