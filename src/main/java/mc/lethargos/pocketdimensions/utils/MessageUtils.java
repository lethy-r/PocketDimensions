package mc.lethargos.pocketdimensions.utils;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.logging.Level;

public class MessageUtils {
    private static FileConfiguration messagesConfig;
    private static FileConfiguration defaultMessagesConfig;
    private static final java.util.Map<String, String> HARDCODED_DEFAULTS = new java.util.HashMap<>();

    static {
        // Hardcoded defaults in case messages.yml is missing or broken
        HARDCODED_DEFAULTS.put("no-permission", "&cNo Permission");
        HARDCODED_DEFAULTS.put("only-players", "This command can only be executed by a player.");
        HARDCODED_DEFAULTS.put("player-not-online", "The specified player is not online.");

        HARDCODED_DEFAULTS.put("command.reloaded", "&aPocket Dimensions configuration reloaded. (Storage type changes require a restart.)");

        HARDCODED_DEFAULTS.put("givepd.usage", "Mention a player. Usage: /givepd <playername> [type]");
        HARDCODED_DEFAULTS.put("givepd.item-name", "&d%player%'s Pocket Dimension");
        HARDCODED_DEFAULTS.put("givepd.item-given", "&aGiven %player%'s Pocket Dimension item.");
        HARDCODED_DEFAULTS.put("givepd.tool-given", "&aGiven mob teleporter tool to %player%.");
        HARDCODED_DEFAULTS.put("givepd.invalid-type", "&cInvalid item type. Use 'dimension' or 'mobtool'.");
        
        HARDCODED_DEFAULTS.put("pdtp.usage", "Mention a player. Usage: /pdtp <playername>");
        HARDCODED_DEFAULTS.put("pdtp.teleporting", "Teleporting to %player%'s pocket dimension.");
        HARDCODED_DEFAULTS.put("pdtp.no-dimension", "That player doesn't have a pocket dimension.");
        
        HARDCODED_DEFAULTS.put("pd.usage", "Mention an action. Usage: /pd <action> [player]");
        HARDCODED_DEFAULTS.put("pd.invalid-action", "Invalid action. Valid actions are: %actions%");
        HARDCODED_DEFAULTS.put("pd.invite.usage", "You must specify a player for this action.\nUsage:/pd invite <player>");
        HARDCODED_DEFAULTS.put("pd.invite.no-permission", "&cNo permission");
        HARDCODED_DEFAULTS.put("pd.invite.no-dimension", "You don't have a pocket dimension!");
        HARDCODED_DEFAULTS.put("pd.invite.not-in-dimension", "You're not in your pocket dimension!");
        HARDCODED_DEFAULTS.put("pd.invite.self", "&cYou can't send invites to yourself!");
        HARDCODED_DEFAULTS.put("pd.invite.sent", "&aInvited %player% Successfully!");
        HARDCODED_DEFAULTS.put("pd.invite.received", "You have been invited to %player%'s Pocket Dimension!\nUse: /pd acceptinv \n To accept it. This expires in 120 seconds(2 Minutes).");
        HARDCODED_DEFAULTS.put("pd.invite.expired-sender", "&cInvite to %player% has expired.");
        HARDCODED_DEFAULTS.put("pd.invite.expired-receiver", "&cYour invite from %player% has expired.");
        
        HARDCODED_DEFAULTS.put("pd.acceptinv.no-invites", "&cNo invites.");
        HARDCODED_DEFAULTS.put("pd.acceptinv.teleporting", "Going to %player%'s Pocket Dimension!");
        HARDCODED_DEFAULTS.put("pd.acceptinv.world-not-found", "Hmm, we can't find that pocket dimension. Ask for another invite?");
        
        HARDCODED_DEFAULTS.put("pd.kick.usage", "You must specify a player for this action.\nUsage:/pd kick <player>");
        HARDCODED_DEFAULTS.put("pd.kick.no-permission", "&cNo permission");
        HARDCODED_DEFAULTS.put("pd.kick.success", "&aKicked Successfully!");
        HARDCODED_DEFAULTS.put("pd.kick.bypass", "&cUnable to kick this player!");
        HARDCODED_DEFAULTS.put("pd.kick.not-in-dimension", "&cPlayer is not in your pocket dimension.");
        HARDCODED_DEFAULTS.put("pd.kick.no-dimension", "&cYou don't have a pocket dimension!");

        HARDCODED_DEFAULTS.put("pdgamerule.usage", "Usage: /pdgamerule <playername> <gamerule> [value]");
        HARDCODED_DEFAULTS.put("pdgamerule.no-dimension", "That player doesn't have a pocket dimension.");
        HARDCODED_DEFAULTS.put("pdgamerule.unknown", "Unknown game rule: %rule%");
        HARDCODED_DEFAULTS.put("pdgamerule.current-value", "Current value of %rule%: %value%");
        HARDCODED_DEFAULTS.put("pdgamerule.unable-to-get", "Unable to get the value for game rule %rule%");
        HARDCODED_DEFAULTS.put("pdgamerule.invalid-boolean", "Invalid Value, Valid Values are: true, false");
        HARDCODED_DEFAULTS.put("pdgamerule.invalid-integer", "Invalid Value, Valid Values are integers only.");
        HARDCODED_DEFAULTS.put("pdgamerule.unsupported-type", "Unsupported game rule type.");
        HARDCODED_DEFAULTS.put("pdgamerule.set-success", "Game rule %rule% set to %value%");
        HARDCODED_DEFAULTS.put("pdgamerule.invalid-value", "Invalid value for game rule %rule%");

        HARDCODED_DEFAULTS.put("pdworldborder.usage", "&cUsage: /pdworldborder <player> <size>");
        HARDCODED_DEFAULTS.put("pdworldborder.invalid-int", "&cSize must be a valid integer.");
        HARDCODED_DEFAULTS.put("pdworldborder.unknown-player", "&cThat player has never played on this server.");
        HARDCODED_DEFAULTS.put("pdworldborder.success", "&aSet pocket dimension border size for %player% to %size%.");
        HARDCODED_DEFAULTS.put("pdworldborder.updated", "&aUpdated active world border.");

        HARDCODED_DEFAULTS.put("dimension.loading", "&aLoading your pocket dimension!");
        HARDCODED_DEFAULTS.put("dimension.creation-failed", "&cFailed to create the pocket dimension.");
        HARDCODED_DEFAULTS.put("dimension.teleporting", "&aSending you to your Pocket Dimension!");
        HARDCODED_DEFAULTS.put("dimension.load-failed", "&cFailed to load the pocket dimension.");
        HARDCODED_DEFAULTS.put("dimension.returning", "Sending you back to the main world.");
        
        HARDCODED_DEFAULTS.put("pd.leave.not-in-dimension", "&cYou are not in a pocket dimension.");
        HARDCODED_DEFAULTS.put("pd.leave.leaving", "&aLeaving pocket dimension...");

        HARDCODED_DEFAULTS.put("mob-teleport.success", "&aTeleported %entity% to your pocket dimension.");
        HARDCODED_DEFAULTS.put("mob-teleport.extract-success", "&aTeleported %entity% out of your pocket dimension.");
        HARDCODED_DEFAULTS.put("mob-teleport.denied", "&cYou cannot teleport this entity.");
        HARDCODED_DEFAULTS.put("mob-teleport.not-mob", "&cThat is not a valid mob.");
        HARDCODED_DEFAULTS.put("mob-teleport.dimension-error", "&cCould not find your pocket dimension.");
        HARDCODED_DEFAULTS.put("mob-teleport.no-return-location", "&cCould not find a return location for the mob.");

        HARDCODED_DEFAULTS.put("feature-disabled", "&cThis feature is disabled.");
        HARDCODED_DEFAULTS.put("protection.denied-build", "&cYou don't have permission to build in this dimension.");
        HARDCODED_DEFAULTS.put("protection.denied-container", "&cYou can't open containers in this dimension.");
        HARDCODED_DEFAULTS.put("protection.denied-mobs", "&cYou can't hurt mobs in this dimension.");

        HARDCODED_DEFAULTS.put("pd.invite.not-owner", "&cOnly the owner (or trusted members) can invite players here.");
        HARDCODED_DEFAULTS.put("pd.invite.entered", "&e%player% &7entered your pocket dimension.");
        HARDCODED_DEFAULTS.put("pd.acceptinv.multiple", "&eYou have several invites: &f%list%&e. Use /pd acceptinv <player>.");

        HARDCODED_DEFAULTS.put("pd.trust.usage", "Usage: /pd trust <player> [builder|trusted]");
        HARDCODED_DEFAULTS.put("pd.untrust.usage", "Usage: /pd untrust <player>");
        HARDCODED_DEFAULTS.put("pd.trust.self", "&cYou can't change your own trust.");
        HARDCODED_DEFAULTS.put("pd.trust.success", "&a%player% is now %tier% in your dimension.");
        HARDCODED_DEFAULTS.put("pd.trust.removed", "&a%player% is no longer trusted in your dimension.");
        HARDCODED_DEFAULTS.put("pd.trust.granted", "&aYou are now %tier% in %player%'s dimension.");
        HARDCODED_DEFAULTS.put("pd.trust.invalid-tier", "&cUnknown tier. Valid: %tiers%");

        HARDCODED_DEFAULTS.put("pd.setspawn.success", "&aDimension spawn point set to your location.");
        HARDCODED_DEFAULTS.put("pd.setspawn.not-in-dimension", "&cYou must be inside your own pocket dimension.");

        HARDCODED_DEFAULTS.put("pd.upgrade.success", "&aDimension border upgraded to %size% blocks.");
        HARDCODED_DEFAULTS.put("pd.upgrade.max", "&eYour dimension is already at the largest configured size.");
        HARDCODED_DEFAULTS.put("pd.upgrade.charged", "&7Charged &f%cost%&7.");

        HARDCODED_DEFAULTS.put("pd.settings.current", "&7Preset: &f%preset%&7, environment: &f%environment%&7, entry notifications: &f%notify%");
        HARDCODED_DEFAULTS.put("pd.settings.usage", "Usage: /pd settings <preset|environment|notify> <value>");
        HARDCODED_DEFAULTS.put("pd.settings.invalid-preset", "&cInvalid preset. Valid: flat, void, normal");
        HARDCODED_DEFAULTS.put("pd.settings.invalid-environment", "&cInvalid environment. Valid: normal, nether, the_end");
        HARDCODED_DEFAULTS.put("pd.settings.invalid-notify", "&cValid values: true, false");
        HARDCODED_DEFAULTS.put("pd.settings.preset-set", "&aPreset set to %value% (applies when your dimension is next created).");
        HARDCODED_DEFAULTS.put("pd.settings.environment-set", "&aEnvironment set to %value% (applies when your dimension is next created).");
        HARDCODED_DEFAULTS.put("pd.settings.notify-set", "&aEntry notifications: %value%");

        HARDCODED_DEFAULTS.put("economy.insufficient-funds-creation", "&cYou need &f%cost%&c to create a pocket dimension.");
        HARDCODED_DEFAULTS.put("economy.insufficient-funds-upgrade", "&cYou need &f%cost%&c for that upgrade.");
        HARDCODED_DEFAULTS.put("economy.transaction-failed", "&cThe payment failed. Please try again.");

        HARDCODED_DEFAULTS.put("admin.usage", "&cUsage: /pdadmin <delete|reset> <player> confirm | /pdadmin cleanup <days> confirm");
        HARDCODED_DEFAULTS.put("admin.confirm-required", "&cThis cannot be undone. Run &f%command% confirm&c to proceed.");
        HARDCODED_DEFAULTS.put("admin.occupied", "&cThat dimension still has players inside: %players%");
        HARDCODED_DEFAULTS.put("admin.unload-failed", "&cCould not unload '&f%world%&c'. Nothing was deleted - try again, or restart the server first.");
        HARDCODED_DEFAULTS.put("admin.delete-failed", "&cCould not fully delete '&f%world%&c' (files locked?). Nothing was changed - restart the server and delete the folder manually.");
        HARDCODED_DEFAULTS.put("admin.delete.usage", "Usage: /pdadmin delete <player> confirm");
        HARDCODED_DEFAULTS.put("admin.delete.success", "&aDeleted the pocket dimension and all its data.");
        HARDCODED_DEFAULTS.put("admin.reset.usage", "Usage: /pdadmin reset <player> confirm");
        HARDCODED_DEFAULTS.put("admin.reset.success", "&aReset the pocket dimension world (player data kept).");
        HARDCODED_DEFAULTS.put("admin.cleanup.usage", "Usage: /pdadmin cleanup <days> confirm");
        HARDCODED_DEFAULTS.put("admin.cleanup.success", "&aRemoved %count% dimension(s) inactive for over %days% day(s).");
        HARDCODED_DEFAULTS.put("admin.list.none", "&7No pocket dimensions exist yet.");
        HARDCODED_DEFAULTS.put("admin.list.header", "&8--- &dPocket Dimensions &7(%count%)&8 ---");
        HARDCODED_DEFAULTS.put("admin.list.entry", "&f%player% &7[%state%] &7border: &f%size% &7last seen: &f%last%");

        HARDCODED_DEFAULTS.put("world-management.entity-cap", "&cYour dimension has %count% entities, over the cap of %cap%. Please clean up.");

        HARDCODED_DEFAULTS.put("menu.title", "&dPocket Dimension Menu");
        HARDCODED_DEFAULTS.put("menu.locked", "&8(No permission)");
        HARDCODED_DEFAULTS.put("menu.regeneration-required", "&cWorld exists - reset required to apply");
        HARDCODED_DEFAULTS.put("menu.back.name", "&7Back");
        HARDCODED_DEFAULTS.put("menu.teleport.name", "&5Travel to your dimension");
        HARDCODED_DEFAULTS.put("menu.teleport.lore-enter", "&7Enter your pocket dimension.");
        HARDCODED_DEFAULTS.put("menu.teleport.lore-leave", "&7Return to the main world.");
        HARDCODED_DEFAULTS.put("menu.trust.name", "&bTrusted players");
        HARDCODED_DEFAULTS.put("menu.trust.lore", "&7Manage who can build here.");
        HARDCODED_DEFAULTS.put("menu.trust.skull-name", "&f%player%");
        HARDCODED_DEFAULTS.put("menu.trust.skull-lore", "&7Tier: &f%tier%\n&eLeft-click: cycle tier\n&cRight-click: remove");
        HARDCODED_DEFAULTS.put("menu.trust.empty", "&7No trusted players yet. Use /pd trust <player>.");
        HARDCODED_DEFAULTS.put("menu.preset.name", "&aDimension preset");
        HARDCODED_DEFAULTS.put("menu.preset.lore", "&7Current: &f%value%\n&eClick to cycle\n&8Applies on next creation");
        HARDCODED_DEFAULTS.put("menu.environment.name", "&3Environment");
        HARDCODED_DEFAULTS.put("menu.environment.lore", "&7Current: &f%value%\n&eClick to cycle\n&8Applies on next creation");
        HARDCODED_DEFAULTS.put("menu.border.name", "&fWorld border");
        HARDCODED_DEFAULTS.put("menu.border.lore", "&7Current: &f%size%\n&7Next tier: &f%next%\n&eClick to upgrade");
        HARDCODED_DEFAULTS.put("menu.notify.name", "&6Entry notifications");
        HARDCODED_DEFAULTS.put("menu.notify.lore", "&7Currently: &f%state%\n&eClick to toggle");
    }

    public static void init(JavaPlugin plugin) {
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        // Load defaults from the JAR
        InputStream defConfigStream = plugin.getResource("messages.yml");
        if (defConfigStream != null) {
            defaultMessagesConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defConfigStream));
            messagesConfig.setDefaults(defaultMessagesConfig);
        }
    }

    public static String getMessage(String key) {
        if (messagesConfig == null) return "Messages not loaded.";

        String msg = messagesConfig.getString(key);

        // Fallback for files generated with the 'messages' root key (legacy support)
        if (msg == null) {
            msg = messagesConfig.getString("messages." + key);
        }

        // If still null, try to get from defaults (which were set in init)
        // Note: YamlConfiguration.getString() usually falls back to defaults automatically if setDefaults is called.
        // However, if the key exists but is null, or if we have the "messages." prefix issue, we double check.

        if (msg == null && defaultMessagesConfig != null) {
             msg = defaultMessagesConfig.getString(key);
             if (msg == null) {
                 msg = defaultMessagesConfig.getString("messages." + key);
             }
        }

        if (msg == null) {
            msg = HARDCODED_DEFAULTS.get(key);
        }

        if (msg == null) return "Missing message: " + key;

        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    /** List variant for GUI lore lines; falls back to the jar defaults and hardcoded defaults. */
    public static java.util.List<String> getMessages(String key) {
        java.util.List<String> lines = messagesConfig != null ? messagesConfig.getStringList(key) : java.util.List.of();
        if (lines.isEmpty() && defaultMessagesConfig != null) {
            lines = defaultMessagesConfig.getStringList(key);
        }
        if (lines.isEmpty()) {
            String single = HARDCODED_DEFAULTS.get(key);
            if (single != null) {
                lines = java.util.List.of(single.split("\n"));
            }
        }
        java.util.List<String> colored = new java.util.ArrayList<>();
        for (String line : lines) {
            colored.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        return colored;
    }

    /** Parses a legacy '&'-coded string into an Adventure Component (titles, GUI names). */
    public static net.kyori.adventure.text.Component legacy(String legacyText) {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand()
                .deserialize(legacyText == null ? "" : legacyText);
    }

    public static void reload(JavaPlugin plugin) {
        init(plugin);
    }
}
