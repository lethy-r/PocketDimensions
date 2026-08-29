package mc.lethargos.pocketdimensions;

import mc.lethargos.pocketdimensions.commands.PdReload;
import mc.lethargos.pocketdimensions.commands.givepd;
import mc.lethargos.pocketdimensions.commands.pdadmin;
import mc.lethargos.pocketdimensions.commands.pdgamerule;
import mc.lethargos.pocketdimensions.commands.pdlist;
import mc.lethargos.pocketdimensions.commands.pdworldborder;
import mc.lethargos.pocketdimensions.commands.pdtp;
import mc.lethargos.pocketdimensions.commands.player.pd;
import mc.lethargos.pocketdimensions.commands.player.pdleave;
import mc.lethargos.pocketdimensions.events.ItemListener;
import mc.lethargos.pocketdimensions.events.PlayerInteract;
import mc.lethargos.pocketdimensions.events.ProtectionListener;
import mc.lethargos.pocketdimensions.hooks.PocketDimensionsExpansion;
import mc.lethargos.pocketdimensions.managers.BorderManager;
import mc.lethargos.pocketdimensions.managers.DimensionService;
import mc.lethargos.pocketdimensions.managers.DimensionWorldManager;
import mc.lethargos.pocketdimensions.managers.EconomyManager;
import mc.lethargos.pocketdimensions.managers.InviteManager;
import mc.lethargos.pocketdimensions.managers.LocationManager;
import mc.lethargos.pocketdimensions.managers.MobTeleportManager;
import mc.lethargos.pocketdimensions.managers.PocketDimensionManager;
import mc.lethargos.pocketdimensions.managers.SettingsManager;
import mc.lethargos.pocketdimensions.managers.TrustManager;
import mc.lethargos.pocketdimensions.menu.PocketMenu;
import mc.lethargos.pocketdimensions.storage.Storage;
import mc.lethargos.pocketdimensions.storage.StorageManager;
import mc.lethargos.pocketdimensions.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class PocketDimensions extends JavaPlugin {

    private StorageManager storageManager;

    @Override
    public void onEnable() {
        // Load Config
        saveDefaultConfig();

        // Update Config with missing defaults
        org.bukkit.configuration.file.FileConfiguration config = getConfig();
        boolean configUpdated = false;
        java.io.InputStream defConfigStream = getResource("config.yml");
        if (defConfigStream != null) {
            org.bukkit.configuration.file.YamlConfiguration defConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(defConfigStream));
            for (String key : defConfig.getKeys(true)) {
                if (!config.contains(key)) {
                    config.set(key, defConfig.get(key));
                    configUpdated = true;
                }
            }
        }
        if (configUpdated) {
            saveConfig();
            getLogger().info("Config updated with new values.");
        }

        // Load Messages
        MessageUtils.init(this);

        // Storage (JSON default, SQLITE/MYSQL via JDBC; auto-imports legacy JSON files)
        storageManager = new StorageManager(this);
        storageManager.init();
        Storage storage = storageManager.getStorage();

        // Managers (one shared instance each)
        LocationManager locationManager = new LocationManager(storage);
        BorderManager borderManager = new BorderManager(storage);
        SettingsManager settingsManager = new SettingsManager(storage);
        TrustManager trustManager = new TrustManager(storage);
        EconomyManager economyManager = new EconomyManager(this, borderManager);
        economyManager.setup();
        InviteManager inviteManager = new InviteManager(this, locationManager, trustManager, settingsManager);
        PocketDimensionManager pocketDimensionManager = new PocketDimensionManager(locationManager);
        MobTeleportManager mobTeleportManager = new MobTeleportManager(this, locationManager);
        DimensionService dimensionService = new DimensionService(this, locationManager, borderManager,
                settingsManager, economyManager);
        DimensionWorldManager dimensionWorldManager = new DimensionWorldManager(this, storage, locationManager);

        // Register event listeners
        getServer().getPluginManager().registerEvents(new PlayerInteract(this, mobTeleportManager, dimensionService), this);
        ProtectionListener protectionListener = new ProtectionListener(this, trustManager);
        protectionListener.reloadConfig();
        getServer().getPluginManager().registerEvents(protectionListener, this);
        getServer().getPluginManager().registerEvents(new ItemListener(this, storage), this);
        getServer().getPluginManager().registerEvents(dimensionWorldManager, this);
        dimensionWorldManager.start();
        ItemListener.registerRecipe(this);

        // GUI menu
        PocketMenu menu = new PocketMenu(this, borderManager, dimensionService, trustManager, settingsManager, economyManager);
        getServer().getPluginManager().registerEvents(menu, this);

        // Register commands
        givepd givepdCmd = new givepd(this);
        Objects.requireNonNull(this.getCommand("givepd")).setExecutor(givepdCmd);
        Objects.requireNonNull(this.getCommand("givepd")).setTabCompleter(givepdCmd);

        pdtp pdtpCmd = new pdtp();
        Objects.requireNonNull(this.getCommand("pdtp")).setExecutor(pdtpCmd);
        Objects.requireNonNull(this.getCommand("pdtp")).setTabCompleter(pdtpCmd);

        pd pdCmd = new pd(this, inviteManager, pocketDimensionManager, trustManager, settingsManager,
                economyManager, menu);
        Objects.requireNonNull(this.getCommand("pd")).setExecutor(pdCmd);
        Objects.requireNonNull(this.getCommand("pd")).setTabCompleter(pdCmd);

        pdgamerule pdgameruleCmd = new pdgamerule(settingsManager);
        Objects.requireNonNull(this.getCommand("pdgamerule")).setExecutor(pdgameruleCmd);
        Objects.requireNonNull(this.getCommand("pdgamerule")).setTabCompleter(pdgameruleCmd);

        pdworldborder pdworldborderCmd = new pdworldborder(borderManager);
        Objects.requireNonNull(this.getCommand("pdworldborder")).setExecutor(pdworldborderCmd);
        Objects.requireNonNull(this.getCommand("pdworldborder")).setTabCompleter(pdworldborderCmd);

        Objects.requireNonNull(this.getCommand("pdleave")).setExecutor(new pdleave(locationManager));

        PdReload pdReloadCmd = new PdReload(this, mobTeleportManager, protectionListener, economyManager);
        Objects.requireNonNull(this.getCommand("pdreload")).setExecutor(pdReloadCmd);

        pdlist pdlistCmd = new pdlist(this, borderManager);
        Objects.requireNonNull(this.getCommand("pdlist")).setExecutor(pdlistCmd);

        pdadmin pdadminCmd = new pdadmin(this, dimensionWorldManager, trustManager, settingsManager, storage);
        Objects.requireNonNull(this.getCommand("pdadmin")).setExecutor(pdadminCmd);

        // PlaceholderAPI (optional)
        if (getConfig().getBoolean("features.placeholderapi", true)
                && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PocketDimensionsExpansion(this, borderManager).register();
            getLogger().info("PlaceholderAPI expansion registered.");
        }

        getLogger().info("Plugin Activated!");
    }

    @Override
    public void onDisable() {
        if (storageManager != null) {
            storageManager.close();
        }
        getLogger().info("Shutting Down!");
    }
}
