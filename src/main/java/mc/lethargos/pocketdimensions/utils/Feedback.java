package mc.lethargos.pocketdimensions.utils;

import mc.lethargos.pocketdimensions.PocketDimensions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;

/**
 * Entry/exit titles and sounds. Everything is config- and permission-gated:
 * features.feedback (global) and pocketdimensions.feedback (per player).
 */
public final class Feedback {

    private Feedback() {
    }

    public static void enter(PocketDimensions plugin, Player player, String ownerName) {
        if (!enabled(plugin, player)) {
            return;
        }
        if (plugin.getConfig().getBoolean("feedback.titles", true)) {
            Component title = MessageUtils.legacy(MessageUtils.getMessage("dimension.title-enter")
                    .replace("%player%", ownerName));
            Component subtitle = MessageUtils.legacy(MessageUtils.getMessage("dimension.subtitle-enter")
                    .replace("%player%", ownerName));
            player.showTitle(Title.title(title, subtitle,
                    Title.Times.times(Duration.ofMillis(400), Duration.ofSeconds(2), Duration.ofMillis(500))));
        }
        playSound(plugin, player, "feedback.sound-enter", "ENTITY_PLAYER_LEVELUP");
    }

    public static void leave(PocketDimensions plugin, Player player) {
        if (!enabled(plugin, player)) {
            return;
        }
        if (plugin.getConfig().getBoolean("feedback.titles", true)) {
            Component title = MessageUtils.legacy(MessageUtils.getMessage("dimension.title-leave"));
            Component subtitle = MessageUtils.legacy(MessageUtils.getMessage("dimension.subtitle-leave"));
            player.showTitle(Title.title(title, subtitle,
                    Title.Times.times(Duration.ofMillis(400), Duration.ofSeconds(2), Duration.ofMillis(500))));
        }
        playSound(plugin, player, "feedback.sound-leave", "ENTITY_ENDERMAN_TELEPORT");
    }

    private static void playSound(PocketDimensions plugin, Player player, String key, String fallback) {
        if (!plugin.getConfig().getBoolean("feedback.sounds", true)) {
            return;
        }
        String name = plugin.getConfig().getString(key, fallback);
        try {
            Sound sound = Sound.valueOf(name.toUpperCase());
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException e) {
            // Invalid config value; skip rather than break the interaction.
        }
    }

    private static boolean enabled(PocketDimensions plugin, Player player) {
        return plugin.getConfig().getBoolean("features.feedback", true)
                && player.hasPermission("pocketdimensions.feedback");
    }
}
