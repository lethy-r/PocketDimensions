package mc.lethargos.pocketdimensions.api.event;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired before a mob is teleported between the main world and a pocket
 * dimension. Cancelling aborts the teleport.
 */
public class MobTeleportEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    public enum Direction { STORE, RETRIEVE }

    private final Player player;
    private final Entity entity;
    private final Direction direction;
    private boolean cancelled;

    public MobTeleportEvent(Player player, Entity entity, Direction direction) {
        this.player = player;
        this.entity = entity;
        this.direction = direction;
    }

    public Player getPlayer() {
        return player;
    }

    public Entity getEntity() {
        return entity;
    }

    public Direction getDirection() {
        return direction;
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
