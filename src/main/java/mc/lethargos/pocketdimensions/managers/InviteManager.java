package mc.lethargos.pocketdimensions.managers;

import mc.lethargos.pocketdimensions.PocketDimensions;
import mc.lethargos.pocketdimensions.api.event.InviteSendEvent;
import mc.lethargos.pocketdimensions.classes.InviteRequest;
import mc.lethargos.pocketdimensions.storage.DimensionSettings;
import mc.lethargos.pocketdimensions.storage.TrustTier;
import mc.lethargos.pocketdimensions.utils.MessageUtils;
import mc.lethargos.pocketdimensions.utils.WorldUtils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Invites are stored per (receiver, sender), so several people can invite the
 * same player at once; /pd acceptinv lists them when ambiguous. A player who
 * is inside someone else's dimension may invite others into it if the owner
 * granted them TRUSTED.
 */
public class InviteManager {

    private static final long EXPIRY_TICKS = 2400L; // 2 minutes

    private final PocketDimensions plugin;
    private final LocationManager locationManager;
    private final TrustManager trustManager;
    private final SettingsManager settingsManager;
    private final Map<UUID, Map<UUID, InviteRequest>> invites = new HashMap<>();

    public InviteManager(PocketDimensions plugin, LocationManager locationManager,
                         TrustManager trustManager, SettingsManager settingsManager) {
        this.plugin = plugin;
        this.locationManager = locationManager;
        this.trustManager = trustManager;
        this.settingsManager = settingsManager;
    }

    public void sendInvite(Player inviter, Player receiver) {
        World currentWorld = inviter.getWorld();
        if (!WorldUtils.isPocketWorld(currentWorld)) {
            inviter.sendMessage(MessageUtils.getMessage("pd.invite.no-dimension"));
            return;
        }
        UUID ownerUuid = WorldUtils.ownerUuidOf(currentWorld);
        if (ownerUuid == null) {
            inviter.sendMessage(MessageUtils.getMessage("pd.invite.no-dimension"));
            return;
        }
        if (!inviter.getUniqueId().equals(ownerUuid)
                && trustManager.getTier(ownerUuid, inviter.getUniqueId()) != TrustTier.TRUSTED) {
            inviter.sendMessage(MessageUtils.getMessage("pd.invite.not-owner"));
            return;
        }
        if (inviter.getUniqueId().equals(receiver.getUniqueId())) {
            inviter.sendMessage(MessageUtils.getMessage("pd.invite.self"));
            return;
        }

        InviteRequest request = new InviteRequest(ownerUuid, inviter, receiver);
        InviteSendEvent event = new InviteSendEvent(inviter, receiver);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }

        invites.computeIfAbsent(receiver.getUniqueId(), key -> new HashMap<>())
                .put(inviter.getUniqueId(), request);

        inviter.sendMessage(MessageUtils.getMessage("pd.invite.sent").replace("%player%", receiver.getName()));
        receiver.sendMessage(MessageUtils.getMessage("pd.invite.received").replace("%player%", inviter.getName()));

        // Expire only this invite; newer invites for the same receiver are untouched.
        new BukkitRunnable() {
            @Override
            public void run() {
                Map<UUID, InviteRequest> pending = invites.get(receiver.getUniqueId());
                if (pending != null && pending.remove(inviter.getUniqueId(), request)) {
                    if (pending.isEmpty()) {
                        invites.remove(receiver.getUniqueId());
                    }
                    inviter.sendMessage(MessageUtils.getMessage("pd.invite.expired-sender").replace("%player%", receiver.getName()));
                    receiver.sendMessage(MessageUtils.getMessage("pd.invite.expired-receiver").replace("%player%", inviter.getName()));
                }
            }
        }.runTaskLater(plugin, EXPIRY_TICKS);
    }

    /**
     * Accepts a pending invite. When several invites are pending and no sender
     * name is given, the names are listed instead of guessing.
     */
    public void acceptInvite(Player receiver, String senderName) {
        Map<UUID, InviteRequest> pending = invites.get(receiver.getUniqueId());
        if (pending == null || pending.isEmpty()) {
            receiver.sendMessage(MessageUtils.getMessage("pd.acceptinv.no-invites"));
            return;
        }

        InviteRequest invite = null;
        if (senderName != null) {
            for (InviteRequest request : pending.values()) {
                if (request.getFrom().getName() != null
                        && request.getFrom().getName().equalsIgnoreCase(senderName)) {
                    invite = request;
                    break;
                }
            }
            if (invite == null) {
                receiver.sendMessage(MessageUtils.getMessage("pd.acceptinv.no-invites"));
                return;
            }
        } else if (pending.size() == 1) {
            invite = pending.values().iterator().next();
        } else {
            List<String> names = pending.values().stream()
                    .map(request -> request.getFrom().getName())
                    .toList();
            receiver.sendMessage(MessageUtils.getMessage("pd.acceptinv.multiple").replace("%list%", String.join(", ", names)));
            return;
        }

        World pocketDimensionWorld = Bukkit.getWorld(WorldUtils.pocketWorldName(invite.getOwner()));
        if (pocketDimensionWorld == null) {
            receiver.sendMessage(MessageUtils.getMessage("pd.acceptinv.world-not-found"));
            plugin.getLogger().warning("World not found: " + WorldUtils.pocketWorldName(invite.getOwner()));
            remove(invite);
            return;
        }

        receiver.sendMessage(MessageUtils.getMessage("pd.acceptinv.teleporting")
                .replace("%player%", invite.getFrom().getName()));
        locationManager.saveLastLocation(receiver, false);
        receiver.teleport(pocketDimensionWorld.getSpawnLocation());
        remove(invite);

        // Notify the dimension owner (respecting their toggle and permission).
        Player owner = Bukkit.getPlayer(invite.getOwner());
        if (owner != null && !owner.getUniqueId().equals(receiver.getUniqueId())) {
            DimensionSettings settings = settingsManager.get(invite.getOwner());
            if (settings.notifyOnEntryOrDefault() && owner.hasPermission("pocketdimensions.feedback")) {
                owner.sendMessage(MessageUtils.getMessage("pd.invite.entered").replace("%player%", receiver.getName()));
            }
        }
    }

    /** Inviter names for tab completion of /pd acceptinv. */
    public List<String> pendingFrom(Player receiver) {
        Map<UUID, InviteRequest> pending = invites.get(receiver.getUniqueId());
        if (pending == null) {
            return List.of();
        }
        return pending.values().stream()
                .map(request -> request.getFrom().getName())
                .toList();
    }

    private void remove(InviteRequest invite) {
        Map<UUID, InviteRequest> pending = invites.get(invite.getTo().getUniqueId());
        if (pending != null) {
            pending.remove(invite.getFrom().getUniqueId());
            if (pending.isEmpty()) {
                invites.remove(invite.getTo().getUniqueId());
            }
        }
    }
}
