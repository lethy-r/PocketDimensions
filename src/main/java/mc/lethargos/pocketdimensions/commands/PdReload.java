package mc.lethargos.pocketdimensions.commands;

import mc.lethargos.pocketdimensions.PocketDimensions;
import mc.lethargos.pocketdimensions.events.ProtectionListener;
import mc.lethargos.pocketdimensions.managers.EconomyManager;
import mc.lethargos.pocketdimensions.managers.MobTeleportManager;
import mc.lethargos.pocketdimensions.utils.MessageUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class PdReload implements CommandExecutor {

    private final PocketDimensions plugin;
    private final MobTeleportManager mobTeleportManager;
    private final ProtectionListener protectionListener;
    private final EconomyManager economyManager;

    public PdReload(PocketDimensions plugin, MobTeleportManager mobTeleportManager,
                    ProtectionListener protectionListener, EconomyManager economyManager) {
        this.plugin = plugin;
        this.mobTeleportManager = mobTeleportManager;
        this.protectionListener = protectionListener;
        this.economyManager = economyManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("pocketdimensions.commands.reload")) {
            sender.sendMessage(MessageUtils.getMessage("no-permission"));
            return true;
        }

        plugin.reloadConfig();
        MessageUtils.reload(plugin);
        mobTeleportManager.reloadConfig();
        protectionListener.reloadConfig();
        economyManager.setup();

        sender.sendMessage(MessageUtils.getMessage("command.reloaded"));
        return true;
    }
}
