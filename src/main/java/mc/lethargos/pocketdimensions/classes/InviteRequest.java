package mc.lethargos.pocketdimensions.classes;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * A pending invite. The dimension owner can differ from the sender when a
 * TRUSTED co-op player invites people into the owner's dimension.
 */
public class InviteRequest {

    private final UUID owner;
    private final Player from;
    private final Player to;

    public InviteRequest(UUID owner, Player from, Player to) {
        this.owner = owner;
        this.from = from;
        this.to = to;
    }

    public UUID getOwner() {
        return owner;
    }

    public Player getFrom() {
        return from;
    }

    public Player getTo() {
        return to;
    }
}
