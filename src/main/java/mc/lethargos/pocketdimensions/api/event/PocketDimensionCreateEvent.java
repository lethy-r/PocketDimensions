package mc.lethargos.pocketdimensions.api.event;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Fired before a pocket dimension world is created. Cancelling prevents
 * creation (and any economy charge). Note: the preset/environment describe
 * what will be generated and cannot be changed from this event.
 */
public class PocketDimensionCreateEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID owner;
    private final String ownerName;
    private final String worldName;
    private final String preset;
    private final String environment;
    private boolean cancelled;

    public PocketDimensionCreateEvent(UUID owner, String ownerName, String worldName, String preset, String environment) {
        this.owner = owner;
        this.ownerName = ownerName;
        this.worldName = worldName;
        this.preset = preset;
        this.environment = environment;
    }

    public UUID getOwner() {
        return owner;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getWorldName() {
        return worldName;
    }

    public String getPreset() {
        return preset;
    }

    public String getEnvironment() {
        return environment;
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
