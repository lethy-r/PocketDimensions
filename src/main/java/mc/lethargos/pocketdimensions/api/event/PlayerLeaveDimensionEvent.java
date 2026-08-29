package mc.lethargos.pocketdimensions.api.event;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Fired before a player leaves their pocket dimension. Cancelling keeps the
 * player inside.
 */
public class PlayerLeaveDimensionEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final UUID owner;
    private final World world;
    private boolean cancelled;

    public PlayerLeaveDimensionEvent(Player player, UUID owner, World world) {
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
